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
public class SolutionArchitectureCompletedHandler implements TaskCompletionHandler {
    private final WorkTaskService tasks;
    public SolutionArchitectureCompletedHandler(WorkTaskService tasks) { this.tasks = tasks; }
    @Override public Set<TaskType> supportedTypes() { return Set.of(TaskType.SOLUTION_ARCHITECTURE); }
    @Override public List<TaskId> onCompleted(TaskCompletionContext context) {
        var source = context.completedTask();
        String runId = source.subjectId();
        TaskId planningId = AutonomousTaskIds.planning(runId);
        tasks.createIfAbsent(new CreateWorkTaskCommand(
                planningId, TaskType.IMPLEMENTATION_PLANNING,
                source.subjectType(), runId,
                AutonomousCapabilities.forRole(source.requiredCapabilities(), "implementation-planning"),
                "implementation-planning",
                "Create an executable implementation plan using all authoritative upstream inputs. Break the work into concrete code changes, " +
                        "tests, migrations if required, and verification steps. In R8-S2, split the coding work only when items can be implemented " +
                        "independently from the same immutable base revision without requiring another item's intermediate commit. If that is not safe, " +
                        "emit one work item. Do not modify repository files. End the response with the exact machine-readable manifest below. " +
                        "Use 1-16 unique lowercase keys. AIW_CAPABILITIES is optional and comma-separated. Instruction lines may be multi-line.\n\n" +
                        "AIW_IMPLEMENTATION_WORK_ITEMS_V1\n" +
                        "AIW_WORK_ITEM_BEGIN <key>\n" +
                        "AIW_CAPABILITIES java,maven\n" +
                        "<precise implementation instruction>\n" +
                        "AIW_WORK_ITEM_END\n" +
                        "AIW_IMPLEMENTATION_WORK_ITEMS_END",
                source.repositoryRef(), source.baseRevision(), source.priority(),
                Set.of(AutonomousTaskIds.requirement(runId), source.id())));
        return List.of(planningId);
    }
}
