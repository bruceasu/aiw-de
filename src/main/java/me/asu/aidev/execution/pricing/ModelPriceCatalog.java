package me.asu.aidev.execution.pricing;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically publishes normalized rates and fills previously missing estimates. */
@Service
public class ModelPriceCatalog {
    public record Rule(String id, String model, String currency, String tier,
                       long fromMs, Long untilMs, String zone, Set<String> days,
                       String start, String end, Set<String> onlyDates, Set<String> excludedDates,
                       String calendarFrom, String calendarUntil, long input, long cached, long output, Boolean cacheWriteIncluded,
                       Long cacheWrite, Long minContext, Long maxContext) {}
    public record Snapshot(String id, String provider, String sourceUrl, String contentSha256,
                           String parserVersion, long observedMs, List<Rule> rules) {}
    public record Cursor(long startedMs, String attemptId, String requestId) {}
    public record BackfillResult(int calculated, Cursor next) {}
    private record PublishedRate(PriceSchedule.Rate rate, String snapshot) {}

    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;

    public ModelPriceCatalog(AuthorityRepository repo, AuthorityBoundary boundary) {
        this.repo = repo;
        this.boundary = boundary;
    }

    /** A snapshot ID identifies immutable data. An existing ID is never overwritten. */
    @Transactional
    public void publish(Snapshot input) {
        validate(input);
        boundary.lock();
        String digest = AuthorityRepository.hash(repo.encode(canonical(input)));
        var existing = repo.jdbc().queryForList("SELECT snapshot_digest FROM model_price_snapshot WHERE snapshot_id=?", String.class, input.id());
        if (!existing.isEmpty()) {
            if (!digest.equals(existing.getFirst())) throw new IllegalStateException("PRICE_SNAPSHOT_ID_CONFLICT");
            return; // A retry must not roll the head back to an older snapshot.
        }
        repo.jdbc().update("INSERT INTO model_price_snapshot VALUES(?,?,?,?,?,?,?,?)", input.id(), input.provider(),
                input.sourceUrl(), input.contentSha256(), digest, input.parserVersion(), input.observedMs(), System.currentTimeMillis());
        for (Rule rule : input.rules()) {
            repo.jdbc().update("""
                INSERT INTO model_price_rule(snapshot_id,rule_id,provider,model,currency,service_tier,
                    valid_from_ms,valid_until_ms,zone_id,days_json,start_time,end_time,only_dates_json,excluded_dates_json,
                    calendar_from,calendar_until,input_per_million,
                    cached_per_million,output_per_million,cache_write_included,cache_write_per_million,min_context,max_context)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, input.id(), rule.id(), input.provider(), rule.model(), rule.currency(), rule.tier(),
                    rule.fromMs(), rule.untilMs(), rule.zone(), repo.encode(rule.days().stream().sorted().toList()),
                    rule.start(), rule.end(), repo.encode(rule.onlyDates().stream().sorted().toList()),
                    repo.encode(rule.excludedDates().stream().sorted().toList()), rule.calendarFrom(), rule.calendarUntil(),
                    rule.input(), rule.cached(), rule.output(), rule.cacheWriteIncluded(), rule.cacheWrite(),
                    rule.minContext(), rule.maxContext());
        }
        repo.jdbc().update("""
            INSERT INTO model_price_head(provider,snapshot_id) VALUES(?,?)
            ON CONFLICT(provider) DO UPDATE SET snapshot_id=excluded.snapshot_id
            """, input.provider(), input.id());
        repo.audit("PRICE:" + input.provider(), null, input.id(),
                boundary.human() == null ? "model-price-refresh" : boundary.human(),
                "MODEL_PRICE_SNAPSHOT_PUBLISHED", digest);
    }

    /** Fills only unestimated completed API calls; never rewrites old estimates or budgets. */
    @Transactional
    public BackfillResult backfill(String provider, int limit, Cursor cursor) {
        text(provider, 64);
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("PRICE_BACKFILL_LIMIT_INVALID");
        if (cursor != null) { text(cursor.attemptId(), 128); text(cursor.requestId(), 128); }
        boundary.lock();
        List<PublishedRate> published = loadRates(provider);
        if (published.isEmpty()) return new BackfillResult(0, null);
        List<PriceSchedule.Rate> rates = published.stream().map(PublishedRate::rate).toList();
        var calls = repo.jdbc().queryForList("""
            SELECT u.* FROM model_usage_request u WHERE u.provider=? AND u.state='COMPLETED'
              AND u.connection_kind='API_KEY' AND u.price_scope='TEXT_ONLY' AND u.service_tier<>'unknown' AND NOT EXISTS
                (SELECT 1 FROM model_usage_estimate e WHERE e.attempt_id=u.attempt_id AND e.request_id=u.request_id)
              AND (u.started_ms>? OR (u.started_ms=? AND u.attempt_id>?)
                   OR (u.started_ms=? AND u.attempt_id=? AND u.request_id>?))
              ORDER BY u.started_ms,u.attempt_id,u.request_id LIMIT ?
            """, provider, cursor == null ? -1L : cursor.startedMs(), cursor == null ? -1L : cursor.startedMs(),
                    cursor == null ? "" : cursor.attemptId(), cursor == null ? -1L : cursor.startedMs(),
                    cursor == null ? "" : cursor.attemptId(), cursor == null ? "" : cursor.requestId(), limit);
        int count = 0;
        for (var call : calls) {
            String model = string(call, "model"), tier = string(call, "service_tier");
            var currencies = rates.stream().filter(r -> r.model().equals(model) && r.tier().equals(tier))
                    .map(PriceSchedule.Rate::currency).collect(Collectors.toSet());
            if (currencies.size() != 1) continue;
            String currency = currencies.iterator().next();
            Long inputTokens = number(call, "input_tokens");
            var usage = new PriceSchedule.Usage(inputTokens, number(call, "cached_tokens"),
                    number(call, "output_tokens"), number(call, "cache_write_tokens"));
            var request = new PriceSchedule.Request(provider, model, currency, tier,
                    Instant.ofEpochMilli(number(call, "started_ms")), inputTokens, usage, false);
            PriceSchedule.Estimate estimate;
            try { estimate = PriceSchedule.estimate(request, rates); }
            catch (ArithmeticException | IllegalArgumentException unpriceable) { continue; }
            if (estimate.status() != PriceSchedule.Status.ESTIMATED) continue;
            String snapshot = published.stream().filter(value -> value.rate().version().equals(estimate.priceVersion()))
                    .map(PublishedRate::snapshot).findFirst().orElseThrow();
            repo.jdbc().update("INSERT INTO model_usage_estimate VALUES(?,?,?,?,?,?,?,?)",
                    call.get("attempt_id"), call.get("request_id"), 1L, snapshot, estimate.priceVersion(),
                    currency, estimate.microUnits(), System.currentTimeMillis());
            count++;
        }
        Cursor next = null;
        if (calls.size() == limit) {
            var last = calls.getLast();
            next = new Cursor(number(last, "started_ms"), string(last, "attempt_id"), string(last, "request_id"));
        }
        return new BackfillResult(count, next);
    }

    private List<PublishedRate> loadRates(String provider) {
        var result = new ArrayList<PublishedRate>();
        var snapshots = repo.jdbc().queryForList("SELECT snapshot_id,observed_ms FROM model_price_snapshot WHERE provider=? ORDER BY observed_ms,snapshot_id", provider);
        var nextBySnapshot = new java.util.HashMap<String,Long>();
        for (int i = 0; i + 1 < snapshots.size(); i++)
            nextBySnapshot.put(string(snapshots.get(i), "snapshot_id"), number(snapshots.get(i + 1), "observed_ms"));
        for (var row : repo.jdbc().queryForList("""
                SELECT r.*,s.observed_ms,s.snapshot_id FROM model_price_rule r
                  JOIN model_price_snapshot s ON s.snapshot_id=r.snapshot_id
                 WHERE s.provider=? ORDER BY s.observed_ms,s.snapshot_id,r.rule_id
                """, provider)) {
            @SuppressWarnings("unchecked") List<String> days = repo.decode(string(row, "days_json"), List.class);
            @SuppressWarnings("unchecked") List<String> only = repo.decode(string(row, "only_dates_json"), List.class);
            @SuppressWarnings("unchecked") List<String> excluded = repo.decode(string(row, "excluded_dates_json"), List.class);
            Long until = number(row, "valid_until_ms"), next = nextBySnapshot.get(string(row, "snapshot_id"));
            if (next != null && (until == null || next < until)) until = next;
            long from = Math.max(number(row, "valid_from_ms"), number(row, "observed_ms"));
            if (until != null && until <= from) continue;
            var rate = new PriceSchedule.Rate(string(row, "rule_id"), string(row, "provider"), string(row, "model"),
                    string(row, "currency"), string(row, "service_tier"), Instant.ofEpochMilli(from),
                    until == null ? null : Instant.ofEpochMilli(until),
                    ZoneId.of(string(row, "zone_id")), days.stream().map(DayOfWeek::valueOf).collect(Collectors.toSet()),
                    time(string(row, "start_time")), time(string(row, "end_time")), dates(only), dates(excluded),
                    date(string(row, "calendar_from")), date(string(row, "calendar_until")), number(row, "input_per_million"),
                    number(row, "cached_per_million"), number(row, "output_per_million"), AuthorityRepository.flag(row.get("cache_write_included")), number(row, "cache_write_per_million"),
                    number(row, "min_context"), number(row, "max_context"));
            result.add(new PublishedRate(rate, string(row, "snapshot_id")));
        }
        return result;
    }

    private void validate(Snapshot snapshot) {
        Objects.requireNonNull(snapshot); text(snapshot.id(), 128); text(snapshot.provider(), 64);
        text(snapshot.sourceUrl(), 2048); text(snapshot.parserVersion(), 128);
        if (snapshot.contentSha256() == null || !snapshot.contentSha256().matches("[0-9a-f]{64}")
                || snapshot.observedMs() < 1 || snapshot.rules() == null || snapshot.rules().isEmpty()
                || snapshot.rules().size() > 2000 || snapshot.rules().stream().anyMatch(Objects::isNull)
                || snapshot.rules().stream().map(Rule::id).distinct().count() != snapshot.rules().size())
            throw new IllegalArgumentException("PRICE_SNAPSHOT_INVALID");
        for (Rule rule : snapshot.rules()) {
            text(rule.id(), 128); text(rule.model(), 200); text(rule.currency(), 16); text(rule.tier(), 64);
            if (rule.fromMs() < 1 || rule.days() == null || rule.onlyDates() == null || rule.excludedDates() == null || rule.cacheWriteIncluded() == null)
                throw new IllegalArgumentException("PRICE_RULE_INVALID");
            new PriceSchedule.Rate(rule.id(), snapshot.provider(), rule.model(), rule.currency(), rule.tier(),
                    Instant.ofEpochMilli(rule.fromMs()), rule.untilMs() == null ? null : Instant.ofEpochMilli(rule.untilMs()),
                    ZoneId.of(rule.zone()), rule.days().stream().map(DayOfWeek::valueOf).collect(Collectors.toSet()),
                    time(rule.start()), time(rule.end()), dates(rule.onlyDates()), dates(rule.excludedDates()),
                    date(rule.calendarFrom()), date(rule.calendarUntil()), rule.input(), rule.cached(), rule.output(), rule.cacheWriteIncluded(),
                    rule.cacheWrite(), rule.minContext(), rule.maxContext());
        }
    }

    private Snapshot canonical(Snapshot snapshot) {
        var rules = snapshot.rules().stream().sorted(java.util.Comparator.comparing(Rule::id))
                .map(rule -> new Rule(rule.id(), rule.model(), rule.currency(), rule.tier(), rule.fromMs(), rule.untilMs(),
                        rule.zone(), new TreeSet<>(rule.days()), rule.start(), rule.end(), new TreeSet<>(rule.onlyDates()),
                        new TreeSet<>(rule.excludedDates()), rule.calendarFrom(), rule.calendarUntil(), rule.input(), rule.cached(),
                        rule.output(), rule.cacheWriteIncluded(), rule.cacheWrite(), rule.minContext(), rule.maxContext())).toList();
        return new Snapshot(snapshot.id(), snapshot.provider(), snapshot.sourceUrl(), snapshot.contentSha256(),
                snapshot.parserVersion(), snapshot.observedMs(), rules);
    }

    private static LocalTime time(String value) { return value == null ? null : LocalTime.parse(value); }
    private static LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
    private static Set<LocalDate> dates(java.util.Collection<String> values) {
        return values.stream().map(LocalDate::parse).collect(Collectors.toSet());
    }
    private static String string(Map<String,Object> row, String key) { return (String)row.get(key); }
    private static Long number(Map<String,Object> row, String key) {
        return row.get(key) == null ? null : ((Number)row.get(key)).longValue();
    }
    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("PRICE_IDENTITY_INVALID");
    }
}
