package me.asu.aidev.execution.pricing;

import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure matching and estimation rules for versioned, text-token prices. */
public final class PriceSchedule {
    private PriceSchedule() {}

    /** Rates are currency micro-units per million tokens. Intervals are [from, until). */
    public record Rate(String version, String provider, String model, String currency, String tier,
                       Instant from, Instant until, ZoneId zone, Set<DayOfWeek> days,
                       LocalTime start, LocalTime end, long input, long cached, long output,
                       Long cacheWrite, Long minContext, Long maxContext) {
        public Rate {
            requireText(version); requireText(provider); requireText(model); requireText(currency); requireText(tier);
            Objects.requireNonNull(from); Objects.requireNonNull(zone); days = Set.copyOf(days);
            if (until != null && !until.isAfter(from)) throw new IllegalArgumentException("Invalid price interval");
            if ((start == null) != (end == null) || start != null && start.equals(end))
                throw new IllegalArgumentException("Invalid recurring time window");
            if (input < 0 || cached < 0 || output < 0 || cacheWrite != null && cacheWrite < 0
                    || minContext != null && minContext < 0 || maxContext != null && maxContext < 0
                    || minContext != null && maxContext != null && minContext >= maxContext)
                throw new IllegalArgumentException("Invalid price or context range");
        }
    }

    public record Usage(Long input, Long cached, Long output, Long cacheWrite) {
        public Usage {
            if (input != null && input < 0 || cached != null && cached < 0 || output != null && output < 0
                    || cacheWrite != null && cacheWrite < 0 || input != null && cached != null && cached > input
                    || input != null && cacheWrite != null && cacheWrite > input
                    || input != null && cached != null && cacheWrite != null && cacheWrite > input - cached)
                throw new IllegalArgumentException("Invalid token usage");
        }
    }

    public record Request(String provider, String model, String currency, String tier,
                          Instant time, Long contextTokens, Usage usage, boolean subscription) {
        public Request {
            requireText(provider); requireText(model); requireText(currency); requireText(tier);
            Objects.requireNonNull(time); Objects.requireNonNull(usage);
            if (contextTokens != null && contextTokens < 0) throw new IllegalArgumentException("Invalid context");
        }
    }

    public enum Status { ESTIMATED, SUBSCRIPTION, PRICE_UNKNOWN, PRICE_AMBIGUOUS, USAGE_UNKNOWN }
    public record Estimate(Status status, String priceVersion, String currency, Long microUnits) {}

    public static Estimate estimate(Request request, List<Rate> rates) {
        Objects.requireNonNull(request); Objects.requireNonNull(rates);
        if (request.subscription()) return new Estimate(Status.SUBSCRIPTION, null, request.currency(), null);
        List<Rate> matches = rates.stream().filter(rate -> matches(rate, request)).toList();
        if (matches.isEmpty()) return new Estimate(Status.PRICE_UNKNOWN, null, request.currency(), null);
        if (matches.size() != 1) return new Estimate(Status.PRICE_AMBIGUOUS, null, request.currency(), null);
        Rate price = matches.getFirst(); Usage usage = request.usage();
        if (usage.input() == null || usage.cached() == null || usage.output() == null
                || price.cacheWrite() != null && usage.cacheWrite() == null)
            return new Estimate(Status.USAGE_UNKNOWN, price.version(), price.currency(), null);
        long writes = price.cacheWrite() == null ? 0 : usage.cacheWrite();
        if (writes > usage.input() - usage.cached()) throw new IllegalArgumentException("Invalid cache usage");
        BigInteger sum = multiply(usage.input() - usage.cached() - writes, price.input())
                .add(multiply(usage.cached(), price.cached())).add(multiply(usage.output(), price.output()));
        if (price.cacheWrite() != null) sum = sum.add(multiply(writes, price.cacheWrite()));
        long micros = sum.add(BigInteger.valueOf(999_999)).divide(BigInteger.valueOf(1_000_000)).longValueExact();
        return new Estimate(Status.ESTIMATED, price.version(), price.currency(), micros);
    }

    private static boolean matches(Rate rate, Request request) {
        if (!rate.provider().equals(request.provider()) || !rate.model().equals(request.model())
                || !rate.currency().equals(request.currency()) || !rate.tier().equals(request.tier())
                || request.time().isBefore(rate.from()) || rate.until() != null && !request.time().isBefore(rate.until()))
            return false;
        if ((rate.minContext() != null || rate.maxContext() != null) && request.contextTokens() == null) return false;
        if (rate.minContext() != null && request.contextTokens() < rate.minContext()
                || rate.maxContext() != null && request.contextTokens() >= rate.maxContext()) return false;
        ZonedDateTime local = request.time().atZone(rate.zone());
        DayOfWeek day = local.getDayOfWeek();
        if (rate.start() == null) return rate.days().isEmpty() || rate.days().contains(day);
        LocalTime time = local.toLocalTime();
        boolean overnight = rate.start().isAfter(rate.end());
        boolean inWindow = overnight ? !time.isBefore(rate.start()) || time.isBefore(rate.end())
                                    : !time.isBefore(rate.start()) && time.isBefore(rate.end());
        if (!inWindow) return false;
        if (overnight && time.isBefore(rate.end())) day = day.minus(1);
        return rate.days().isEmpty() || rate.days().contains(day);
    }

    private static BigInteger multiply(long quantity, long rate) {
        return BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(rate));
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Price identity required");
    }
}
