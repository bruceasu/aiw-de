package me.asu.aidev.autonomous.application;

import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RequirementAnalysisCompletedHandler implements TaskCompletionHandler {
    private final WorkTaskService tasks;
    public RequirementAnalysisCompletedHandler(WorkTaskService tasks) { this.tasks = tasks; }
    @Override public Set<TaskType> supportedTypes() { return Set.of(TaskType.REQUIREMENT_ANALYSIS); }
    @Override public List<TaskId> onCompleted(TaskCompletionContext context) {
        var source = context.completedTask();
        TaskId architectureId = AutonomousTaskIds.architecture(source.subjectId());
        tasks.createIfAbsent(new CreateWorkTaskCommand(
                architectureId, TaskType.SOLUTION_ARCHITECTURE,
                source.subjectType(), source.subjectId(),
                AutonomousCapabilities.forRole(source.requiredCapabilities(), "solution-architecture"),
                "solution-architecture",
                "Design the solution architecture for the authoritative requirement-analysis input. Inspect the repository as needed. " +
                        "Produce concrete component boundaries, data/control flow, persistence and API changes, tradeoffs, risks, and constraints. " +
                        "Do not modify repository files.",
                source.repositoryRef(), source.baseRevision(), source.priority(), Set.of(source.id())));
        return List.of(architectureId);
    }
}
