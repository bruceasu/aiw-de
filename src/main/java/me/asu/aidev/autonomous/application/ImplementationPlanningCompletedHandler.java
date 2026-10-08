package me.asu.aidev.autonomous.application;

import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Materializes one or more independently executable implementation work items from the planning result. */
@Component
public class ImplementationPlanningCompletedHandler implements TaskCompletionHandler {
    private final WorkTaskService tasks;
    private final ImplementationPlanParser parser = new ImplementationPlanParser();

    public ImplementationPlanningCompletedHandler(WorkTaskService tasks) {
        this.tasks = tasks;
    }

    @Override public Set<TaskType> supportedTypes() { return Set.of(TaskType.IMPLEMENTATION_PLANNING); }

    @Override
    public List<TaskId> onCompleted(TaskCompletionContext context) {
        var source = context.completedTask();
        String runId = source.subjectId();
        String summary = context.completionReport() == null ? null : context.completionReport().summary();
        var manifest = parser.parse(summary);

        if (manifest.isEmpty() || source.baseRevision() == null) {
            // R8-S1 compatibility and a safe degradation path for manually authored/plain planning summaries.
            TaskId implementationId = AutonomousTaskIds.implementation(runId);
            tasks.createIfAbsent(commandFor(source, implementationId,
                    AutonomousCapabilities.forRole(source.requiredCapabilities(), "coding"),
                    "Implement the requested change using all authoritative upstream requirement, architecture, and implementation-plan inputs. " +
                            "Modify only what is needed, add or update tests, run relevant verification, and leave the repository in a reviewable state."));
            return List.of(implementationId);
        }

        var generated = new ArrayList<TaskId>();
        for (ImplementationWorkItem item : manifest.orElseThrow().items()) {
            TaskId implementationId = AutonomousTaskIds.implementationItem(runId, item.key());
            tasks.createIfAbsent(commandFor(source, implementationId,
                    implementationCapabilities(source.requiredCapabilities(), item.capabilities()),
                    "Implement autonomous work item '" + item.key() + "'. This item was declared safe to implement independently " +
                            "from the same immutable base revision. Use all authoritative upstream requirement, architecture, and planning inputs.\n\n" +
                            "WORK ITEM INSTRUCTION:\n" + item.instruction()));
            generated.add(implementationId);
        }
        return List.copyOf(generated);
    }

    private static CreateWorkTaskCommand commandFor(me.asu.aidev.work.domain.WorkTask source,
                                                     TaskId implementationId,
                                                     Set<String> capabilities,
                                                     String instruction) {
        String runId = source.subjectId();
        return new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION,
                source.subjectType(), runId,
                capabilities,
                "coding",
                instruction,
                source.repositoryRef(), source.baseRevision(), source.priority(),
                Set.of(AutonomousTaskIds.requirement(runId), AutonomousTaskIds.architecture(runId), source.id()));
    }

    private static Set<String> implementationCapabilities(Set<String> source, Set<String> itemCapabilities) {
        var result = new LinkedHashSet<String>(AutonomousCapabilities.forRole(source, "coding"));
        if (itemCapabilities != null) {
            for (String capability : itemCapabilities) {
                if (capability == null || capability.isBlank()) continue;
                if (capability.startsWith("repo:") || capability.equals("coding") || capability.equals("integration")
                        || capability.equals("code-review") || capability.equals("verification")
                        || capability.equals("repair") || capability.equals("integration-verification")) {
                    continue;
                }
                result.add(capability.strip());
            }
        }
        return Set.copyOf(result);
    }
}
