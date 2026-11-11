package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PriceScheduleTest {
    private static PriceSchedule.Rate rate(String id, Set<DayOfWeek> days, LocalTime start, LocalTime end,
                                           Set<LocalDate> only, Set<LocalDate> excluded,
                                           LocalDate calendarFrom, LocalDate calendarUntil, boolean writesIncluded) {
        return new PriceSchedule.Rate(id, "provider", "model", "USD", "default",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"),
                ZoneId.of("UTC"), days, start, end, only, excluded, calendarFrom, calendarUntil,
                2_000_000, 500_000, 8_000_000, writesIncluded, null, null, null);
    }

    private static PriceSchedule.Request request(String at, PriceSchedule.Usage usage, boolean subscription) {
        return new PriceSchedule.Request("provider", "model", "USD", "default", Instant.parse(at), 100_000L, usage, subscription);
    }

    @Test void calculatesInputCacheAndOutputWithoutDoubleCountingCache() {
        var rate = rate("r1", Set.of(), null, null, Set.of(), Set.of(), null, null, true);
        var estimate = PriceSchedule.estimate(request("2026-04-01T12:00:00Z",
                new PriceSchedule.Usage(100_000L, 20_000L, 50_000L, null), false), List.of(rate));
        assertEquals(PriceSchedule.Status.ESTIMATED, estimate.status());
        assertEquals(Long.valueOf(570_000L), estimate.microUnits());
        assertEquals("r1", estimate.priceVersion());
    }

    @Test void usesStartDayForOvernightWindowAndRespectsExcludedHoliday() {
        var rate = rate("overnight", Set.of(DayOfWeek.MONDAY), LocalTime.of(23, 0), LocalTime.of(2, 0),
                Set.of(), Set.of(LocalDate.parse("2026-04-06")), LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"), true);
        var usage = new PriceSchedule.Usage(1L, 0L, 1L, null);
        assertEquals(PriceSchedule.Status.ESTIMATED, PriceSchedule.estimate(request("2026-04-14T01:00:00Z", usage, false), List.of(rate)).status());
        assertEquals(PriceSchedule.Status.PRICE_UNKNOWN, PriceSchedule.estimate(request("2026-04-07T01:00:00Z", usage, false), List.of(rate)).status());
        assertEquals(PriceSchedule.Status.PRICE_UNKNOWN, PriceSchedule.estimate(request("2026-04-14T02:00:00Z", usage, false), List.of(rate)).status());
    }

    @Test void unknown_calendar_or_unbounded_overlap_never_invents_a_price() {
        var first = rate("r1", Set.of(), null, null, Set.of(), Set.of(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-01"), true);
        var second = rate("r2", Set.of(), null, null, Set.of(), Set.of(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-01"), true);
        var usage = new PriceSchedule.Usage(1L, 0L, 1L, null);
        assertEquals(PriceSchedule.Status.PRICE_AMBIGUOUS, PriceSchedule.estimate(request("2026-04-01T00:00:00Z", usage, false), List.of(first, second)).status());
        assertEquals(PriceSchedule.Status.PRICE_UNKNOWN, PriceSchedule.estimate(request("2026-06-01T00:00:00Z", usage, false), List.of(first)).status());
    }

    @Test void missing_usage_separate_cache_writes_and_subscriptions_remain_unpriced() {
        var ordinary = rate("normal", Set.of(), null, null, Set.of(), Set.of(), null, null, true);
        var separateWrites = rate("writes", Set.of(), null, null, Set.of(), Set.of(), null, null, false);
        assertEquals(PriceSchedule.Status.USAGE_UNKNOWN, PriceSchedule.estimate(request("2026-04-01T00:00:00Z",
                new PriceSchedule.Usage(10L, null, 2L, null), false), List.of(ordinary)).status());
        assertEquals(PriceSchedule.Status.USAGE_UNKNOWN, PriceSchedule.estimate(request("2026-04-01T00:00:00Z",
                new PriceSchedule.Usage(10L, 0L, 2L, 4L), false), List.of(separateWrites)).status());
        assertEquals(PriceSchedule.Status.SUBSCRIPTION, PriceSchedule.estimate(request("2026-04-01T00:00:00Z",
                new PriceSchedule.Usage(10L, 0L, 2L, null), true), List.of(ordinary)).status());
    }
}
