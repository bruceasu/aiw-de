package me.asu.aidev.development.application;

import me.asu.aidev.autonomous.application.ParallelImplementationIntegrationCoordinator;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Fans an authoritative code-change result into its next deterministic engineering work. */
@Component
public class CodeChangeCompletedHandler implements TaskCompletionHandler {
    private static final Set<String> ROLE_CAPABILITIES = Set.of(
            "coding", "integration", "code-review", "verification", "integration-verification", "repair");

    private final WorkTaskService tasks;
    private final ParallelImplementationIntegrationCoordinator parallelIntegration;

    public CodeChangeCompletedHandler(WorkTaskService tasks,
                                      ParallelImplementationIntegrationCoordinator parallelIntegration) {
        this.tasks = tasks;
        this.parallelIntegration = parallelIntegration;
    }

    @Override
    public Set<TaskType> supportedTypes() {
        return Set.of(TaskType.IMPLEMENTATION, TaskType.INTEGRATION, TaskType.REPAIR);
    }

    @Override
    public List<TaskId> onCompleted(TaskCompletionContext context) {
        var change = context.completedTask();
        if (change.type() == TaskType.IMPLEMENTATION) {
            var parallelResult = parallelIntegration.onImplementationCompleted(context);
            if (parallelResult.isPresent()) return parallelResult.orElseThrow();
        }

        String downstreamBaseRevision = change.baseRevision() != null
                ? context.workspaceResult().resultRevision()
                : null;
        boolean integrationLineage = isIntegrationLineage(change.type(), change.skillRef());

        TaskId reviewId = DevelopmentTaskIds.review(change.id());
        TaskId verificationId = DevelopmentTaskIds.verification(change.id());

        tasks.createIfAbsent(new CreateWorkTaskCommand(
                reviewId,
                TaskType.CODE_REVIEW,
                change.subjectType(),
                change.subjectId(),
                downstreamCapabilities(change.requiredCapabilities(), "code-review"),
                "code-review",
                integrationLineage
                        ? "Review the integrated code result from all authoritative upstream work items. Inspect correctness, conflict resolution, omitted branch work, maintainability, regressions, security, and adherence to the requested change. Do not modify repository files. End the final response with exactly one line: AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED."
                        : "Review the completed code change from the authoritative upstream task input. Inspect correctness, maintainability, regressions, security, and adherence to the requested change. Do not modify repository files. End the final response with exactly one line: AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                change.repositoryRef(),
                downstreamBaseRevision,
                change.priority(),
                Set.of(change.id())));

        TaskType verificationType = integrationLineage ? TaskType.INTEGRATION_VERIFICATION : TaskType.VERIFICATION;
        String verificationRole = integrationLineage ? "integration-verification" : "verification";
        tasks.createIfAbsent(new CreateWorkTaskCommand(
                verificationId,
                verificationType,
                change.subjectType(),
                change.subjectId(),
                downstreamCapabilities(change.requiredCapabilities(), verificationRole),
                verificationRole,
                integrationLineage
                        ? "Verify the integrated result as a whole. Run the relevant build, tests, static checks, and cross-work-item integration checks; detect merge omissions or incompatible assumptions. Do not modify repository files. End the final response with exactly one line: AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED."
                        : "Verify the completed code change from the authoritative upstream task input. Run the relevant build, tests, and static checks and report concrete failures. Do not modify repository files. End the final response with exactly one line: AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                change.repositoryRef(),
                downstreamBaseRevision,
                change.priority(),
                Set.of(change.id())));

        return List.of(reviewId, verificationId);
    }

    public static boolean isIntegrationLineage(TaskType type, String skillRef) {
        return type == TaskType.INTEGRATION || "integration-repair".equals(skillRef);
    }

    static Set<String> downstreamCapabilities(Set<String> source, String role) {
        var capabilities = new LinkedHashSet<String>();
        for (String capability : source) {
            if (capability == null || capability.isBlank()) continue;
            if (ROLE_CAPABILITIES.contains(capability)) continue;
            capabilities.add(capability);
        }
        capabilities.add(role);
        return Set.copyOf(capabilities);
    }
}
