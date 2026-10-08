package me.asu.aidev.webconsole.application;

import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Attempt;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Edge;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Graph;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Node;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Prerequisite;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.RunSignal;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.TaskRow;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.TimelineDay;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.TimelineEvent;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Window;
import me.asu.aidev.webconsole.domain.WebConsoleTaskSummary;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleMissionReadRepository;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleMissionReadRepository.Dependency;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes existing authority and bounded private reads without changing lifecycle behavior. */
@Service
public class WebConsoleMissionQueryService {
    public static final int TIMELINE_LIMIT = 300;
    private final WebConsoleQueryService console;
    private final JdbcWebConsoleMissionReadRepository reads;

    public WebConsoleMissionQueryService(WebConsoleQueryService console, JdbcWebConsoleMissionReadRepository reads) {
        this.console = console;
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public WebConsoleMissionDetail get(String runId) {
        var run = console.runDashboard(runId);
        var tasks = reads.tasks(runId).stream().sorted(Comparator.comparing(WebConsoleTaskSummary::createdAt)
                .thenComparing(WebConsoleTaskSummary::taskId)).toList();
        var ids = tasks.stream().map(WebConsoleTaskSummary::taskId).toList();
        Set<String> displayed = Set.copyOf(ids);
        var dependencies = reads.dependencies(ids);
        long eligibleEdges = reads.displayedTaskDependencyCount(ids);
        boolean edgesTruncated = eligibleEdges > dependencies.size();
        var prerequisites = new HashMap<String, List<Prerequisite>>();
        for (var edge : dependencies) {
            String scope = edge.subjectType() == null ? "Missing prerequisite"
                    : !"AUTONOMOUS_REQUIREMENT".equals(edge.subjectType()) || !runId.equals(edge.subjectId())
                    ? "Different Run or subject" : displayed.contains(edge.prerequisiteId())
                    ? "Displayed" : "Outside task window";
            prerequisites.computeIfAbsent(edge.taskId(), ignored -> new ArrayList<>()).add(new Prerequisite(
                    edge.prerequisiteId(), scope, displayed.contains(edge.prerequisiteId())
                    ? key("task", edge.prerequisiteId()) : null));
        }
        var taskRows = tasks.stream().map(t -> new TaskRow(t, key("task", t.taskId()),
                List.copyOf(prerequisites.getOrDefault(t.taskId(), List.of())), edgesTruncated)).toList();
        var attempts = reads.attempts(runId).stream().map(a -> new Attempt(a.attemptId(), a.taskId(), a.attemptNo(),
                a.workerId(), a.state(), a.createdAt(), a.startedAt(), a.finishedAt(), a.terminationReason(),
                a.failureCategory(), a.completionVerdict(), a.completionSummary(), key("attempt", a.attemptId()),
                displayed.contains(a.taskId()) ? key("task", a.taskId()) : null)).toList();
        Map<String, String> attemptAnchors = attempts.stream().collect(Collectors.toMap(
                a -> key("pair", a.taskId(), a.attemptNo()), Attempt::anchor));
        Function<RunSignal, RunSignal> signal = s -> s == null ? null : new RunSignal(s.sequence(), s.eventType(),
                s.taskId(), s.actor(), s.detail(), s.occurredAt(), displayed.contains(s.taskId() == null ? "" : s.taskId())
                ? key("task", s.taskId()) : null);
        var signals = reads.signals(runId).stream().map(signal).toList();
        var timeline = console.timeline(runId, TIMELINE_LIMIT);
        var days = new LinkedHashMap<LocalDate, List<TimelineEvent>>();
        for (var entry : timeline.entries()) {
            String taskAnchor = entry.taskId() != null && displayed.contains(entry.taskId())
                    ? key("task", entry.taskId()) : null;
            String attemptAnchor = entry.taskId() == null || entry.attemptNo() == null ? null
                    : attemptAnchors.get(key("pair", entry.taskId(), entry.attemptNo()));
            days.computeIfAbsent(entry.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), ignored -> new ArrayList<>())
                    .add(new TimelineEvent(entry, key("event", entry.occurredAt(), entry.category(), entry.eventType(),
                            entry.runEventSequence(), entry.taskId(), entry.attemptNo(), entry.workerId(),
                            entry.approvalRequestId()), taskAnchor, attemptAnchor));
        }
        long totalDependencies = reads.dependencyCount(runId);
        boolean partialGraph = run.tasks().total() > tasks.size() || totalDependencies > dependencies.size()
                || prerequisites.values().stream().flatMap(List::stream).anyMatch(p -> p.anchor() == null);
        return new WebConsoleMissionDetail(run, reads.requirement(runId),
                new Window(run.tasks().total(), tasks.size(), JdbcWebConsoleMissionReadRepository.TASK_LIMIT),
                new Window(totalDependencies, dependencies.size(), JdbcWebConsoleMissionReadRepository.DEPENDENCY_LIMIT), eligibleEdges,
                taskRows, graph(tasks, dependencies, partialGraph),
                new Window(run.execution().attempts(), attempts.size(), JdbcWebConsoleMissionReadRepository.ATTEMPT_LIMIT), attempts,
                new Window(reads.signalCount(runId), signals.size(), JdbcWebConsoleMissionReadRepository.SIGNAL_LIMIT), signals,
                signal.apply(reads.latestFailure(runId)),
                new Window(timeline.totalEntries(), timeline.returnedEntries(), TIMELINE_LIMIT),
                timeline.entries().isEmpty() ? null : timeline.entries().getFirst().occurredAt(),
                timeline.entries().isEmpty() ? null : timeline.entries().getLast().occurredAt(),
                days.entrySet().stream().map(e -> new TimelineDay(e.getKey(), List.copyOf(e.getValue()))).toList(),
                timeline.entries().stream().map(e -> e.category().name()).distinct().sorted().toList(), Instant.now());
    }

    private static Graph graph(List<WebConsoleTaskSummary> tasks, List<Dependency> dependencies, boolean incomplete) {
        var byId = tasks.stream().collect(Collectors.toMap(WebConsoleTaskSummary::taskId, Function.identity()));
        var internal = dependencies.stream().filter(e -> byId.containsKey(e.taskId())
                && byId.containsKey(e.prerequisiteId())).toList();
        var remaining = new HashMap<String, Integer>();
        var level = new HashMap<String, Integer>();
        var dependents = new HashMap<String, List<String>>();
        tasks.forEach(t -> { remaining.put(t.taskId(), 0); level.put(t.taskId(), 0); });
        internal.forEach(e -> {
            remaining.merge(e.taskId(), 1, Integer::sum);
            dependents.computeIfAbsent(e.prerequisiteId(), ignored -> new ArrayList<>()).add(e.taskId());
        });
        var queue = new ArrayDeque<String>();
        tasks.stream().filter(t -> remaining.get(t.taskId()) == 0).forEach(t -> queue.add(t.taskId()));
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            visited++;
            for (var next : dependents.getOrDefault(id, List.of())) {
                level.merge(next, level.get(id) + 1, Math::max);
                if (remaining.merge(next, -1, Integer::sum) == 0) queue.addLast(next);
            }
        }
        boolean cyclic = visited != tasks.size();
        int unresolvedLevel = level.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        var rows = new HashMap<Integer, Integer>();
        var nodes = new ArrayList<Node>();
        for (var task : tasks) {
            int column = remaining.get(task.taskId()) > 0 ? unresolvedLevel : level.get(task.taskId());
            int row = rows.merge(column, 1, Integer::sum) - 1;
            String shortId = task.taskId().length() <= 24 ? task.taskId() : task.taskId().substring(0, 21) + "…";
            nodes.add(new Node(task.taskId(), task.taskType(), task.state(), key("task", task.taskId()),
                    shortId, 24 + column * 286, 24 + row * 106));
        }
        var positions = nodes.stream().collect(Collectors.toMap(Node::taskId, Function.identity()));
        var edges = internal.stream().map(e -> {
            var from = positions.get(e.prerequisiteId());
            var to = positions.get(e.taskId());
            int x1 = from.x() + 242, y1 = from.y() + 40, x2 = to.x(), y2 = to.y() + 40;
            int middle = (x1 + x2) / 2;
            return new Edge(e.prerequisiteId(), e.taskId(), "M " + x1 + " " + y1 + " C " + middle + " " + y1
                    + " " + middle + " " + y2 + " " + x2 + " " + y2);
        }).toList();
        return new Graph(nodes.stream().mapToInt(n -> n.x() + 266).max().orElse(290),
                nodes.stream().mapToInt(n -> n.y() + 104).max().orElse(128), List.copyOf(nodes), edges, incomplete, cyclic);
    }

    /** Length-prefixed immutable identity; URL-safe encoding also avoids selector/markup injection. */
    private static String key(String prefix, Object... values) {
        var identity = new StringBuilder();
        for (Object value : values) {
            String part = value == null ? "" : value.toString();
            identity.append(part.length()).append(':').append(part);
        }
        return prefix + "-" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.toString().getBytes(StandardCharsets.UTF_8));
    }
}
