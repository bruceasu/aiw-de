package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.domain.AutonomousRun;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineCategory;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEntry;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEventType;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.autonomous.port.AutonomousRunTimelineRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutonomousRunTimelineService {
    public static final int DEFAULT_LIMIT = 500;
    public static final int MAX_LIMIT = 2000;

    private final AutonomousRunRepository runs;
    private final AutonomousRunTimelineRepository timeline;

    public AutonomousRunTimelineService(AutonomousRunRepository runs, AutonomousRunTimelineRepository timeline) {
        this.runs = Objects.requireNonNull(runs);
        this.timeline = Objects.requireNonNull(timeline);
    }

    @Transactional(readOnly = true)
    public TimelineResult get(String runId, Integer requestedLimit) {
        String id = normalizeRunId(runId);
        int limit = normalizeLimit(requestedLimit);
        AutonomousRun run = runs.find(id)
                .orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + id));

        List<AutonomousRunTimelineEntry> entries = new ArrayList<>();
        entries.add(new AutonomousRunTimelineEntry(
                run.startedAt(), AutonomousRunTimelineCategory.RUN, AutonomousRunTimelineEventType.RUN_STARTED,
                run.requirementTaskId().value(), "REQUIREMENT_ANALYSIS", null, null, null,
                "RUNNING", null, null, null, null));
        entries.addAll(timeline.findProjectedEntries(id));
        if (run.completedAt() != null) {
            var eventType = switch (run.status()) {
                case SUCCEEDED -> AutonomousRunTimelineEventType.RUN_SUCCEEDED;
                case REJECTED -> AutonomousRunTimelineEventType.RUN_REJECTED;
                default -> null;
            };
            if (eventType != null) {
                entries.add(new AutonomousRunTimelineEntry(
                        run.completedAt(), AutonomousRunTimelineCategory.RUN, eventType,
                        run.terminalSourceTaskId() == null ? null : run.terminalSourceTaskId().value(),
                        null, null, null,
                        run.approvalRequestId() == null ? null : run.approvalRequestId().value(),
                        run.status().name(), null, null, run.resultRevision(), null));
            }
        }

        entries.sort(Comparator
                .comparing(AutonomousRunTimelineEntry::occurredAt)
                .thenComparingInt(e -> order(e.eventType()))
                .thenComparingLong(e -> e.runEventSequence() == null ? Long.MAX_VALUE : e.runEventSequence())
                .thenComparing(e -> nullSafe(e.taskId()))
                .thenComparing(e -> nullSafe(e.approvalRequestId())));

        int total = entries.size();
        List<AutonomousRunTimelineEntry> returned = total <= limit
                ? List.copyOf(entries)
                : List.copyOf(entries.subList(Math.max(0, total - limit), total));
        return new TimelineResult(run, total, returned.size(), total > limit, returned);
    }

    private static int order(AutonomousRunTimelineEventType type) {
        return switch (type) {
            case RUN_STARTED -> 0;
            case TASK_CREATED -> 10;
            case ATTEMPT_CLAIMED -> 20;
            case ATTEMPT_STARTED -> 30;
            case ATTEMPT_COMPLETED, ATTEMPT_FAILED, ATTEMPT_EXPIRED -> 40;
            case RUN_FAILED -> 45;
            case RUN_RECOVERED, RUN_PAUSED, RUN_RESUMED, RUN_CANCELLED, RUN_MAX_PARALLELISM_CHANGED -> 50;
            case ENGINEERING_GATE_PASSED, ENGINEERING_GATE_REPAIR_REQUIRED -> 60;
            case APPROVAL_REQUESTED -> 70;
            case APPROVAL_APPROVED, APPROVAL_REJECTED -> 80;
            case RUN_SUCCEEDED, RUN_REJECTED -> 90;
        };
    }

    private static String normalizeRunId(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is blank");
        return runId.strip();
    }

    private static int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) return DEFAULT_LIMIT;
        if (requestedLimit < 1 || requestedLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return requestedLimit;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record TimelineResult(
            AutonomousRun run,
            int totalEntries,
            int returnedEntries,
            boolean truncated,
            List<AutonomousRunTimelineEntry> entries) {}
}
