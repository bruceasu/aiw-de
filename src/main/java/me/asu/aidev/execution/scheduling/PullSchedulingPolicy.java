package me.asu.aidev.execution.scheduling;

import me.asu.aidev.work.domain.ReadyTaskCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless ordering for worker-pull scheduling.
 *
 * Priority remains the primary business signal, while wait-time aging prevents indefinite starvation.
 * Within the same effective priority, subjects with fewer active leases are preferred so concurrent
 * workers naturally spread across independent runs instead of draining one run first.
 *
 * This policy is advisory only. Correctness still comes from the atomic READY -> CLAIMED update.
 */
public final class PullSchedulingPolicy {
    static final long AGING_STEP_SECONDS = Duration.ofMinutes(10).toSeconds();
    static final int MAX_AGING_BOOST = 100;

    private PullSchedulingPolicy() {}

    public static List<ReadyTaskCandidate> rank(List<ReadyTaskCandidate> candidates, Instant now) {
        return candidates.stream().sorted(comparator(now)).toList();
    }

    public static int effectivePriority(ReadyTaskCandidate candidate, Instant now) {
        long waitedSeconds = Math.max(0L, Duration.between(candidate.task().updatedAt(), now).toSeconds());
        int ageBoost = (int) Math.min(MAX_AGING_BOOST, waitedSeconds / AGING_STEP_SECONDS);
        return candidate.task().priority().value() + ageBoost;
    }

    static Comparator<ReadyTaskCandidate> comparator(Instant now) {
        return Comparator
                .comparingInt((ReadyTaskCandidate c) -> effectivePriority(c, now)).reversed()
                .thenComparingInt(ReadyTaskCandidate::activeSubjectLeases)
                .thenComparing(c -> c.task().updatedAt())
                .thenComparing(c -> c.task().createdAt())
                .thenComparing(c -> c.task().id().value());
    }
}
