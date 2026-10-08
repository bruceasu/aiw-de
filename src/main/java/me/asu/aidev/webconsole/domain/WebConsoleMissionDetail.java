package me.asu.aidev.webconsole.domain;

import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Private, read-only MVC projection. Window counts never stand in for Run totals. */
public record WebConsoleMissionDetail(
        AutonomousRunDashboard run,
        Requirement requirement,
        Window taskWindow,
        Window dependencyWindow,
        long displayedTaskDependencyCount,
        List<TaskRow> tasks,
        Graph graph,
        Window attemptWindow,
        List<Attempt> attempts,
        Window signalWindow,
        List<RunSignal> signals,
        RunSignal latestFailure,
        Window timelineWindow,
        Instant timelineFrom,
        Instant timelineTo,
        List<TimelineDay> timelineDays,
        List<String> timelineCategories,
        Instant snapshotAt) {

    public record Window(long total, int returned, int limit) {
        public boolean truncated() { return total > returned; }
    }

    public record Requirement(String taskId, String instruction, boolean available) {}
    public record TaskRow(WebConsoleTaskSummary task, String anchor, List<Prerequisite> prerequisites,
                          boolean dependenciesIncomplete) {}
    public record Prerequisite(String taskId, String scope, String anchor) {}
    public record Graph(int width, int height, List<Node> nodes, List<Edge> edges,
                        boolean incomplete, boolean cyclic) {}
    public record Node(String taskId, String taskType, String state, String anchor,
                       String shortId, int x, int y) {}
    public record Edge(String prerequisiteId, String taskId, String path) {}

    public record Attempt(String attemptId, String taskId, int attemptNo, String workerId,
                          String state, Instant createdAt, Instant startedAt, Instant finishedAt,
                          String terminationReason, String failureCategory, String completionVerdict,
                          String completionSummary, String anchor, String taskAnchor) {}
    public record RunSignal(long sequence, String eventType, String taskId, String actor,
                            String detail, Instant occurredAt, String taskAnchor) {}
    public record TimelineDay(LocalDate date, List<TimelineEvent> entries) {}
    public record TimelineEvent(AutonomousRunTimelineEntry entry, String key,
                                String taskAnchor, String attemptAnchor) {}
}
