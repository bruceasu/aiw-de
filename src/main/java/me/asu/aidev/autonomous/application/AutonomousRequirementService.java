package me.asu.aidev.autonomous.application;

import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskType;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutonomousRequirementService {
    public static final String SUBJECT_TYPE = "AUTONOMOUS_REQUIREMENT";
    private final WorkTaskService tasks;
    private final AutonomousRunService runs;
    private final GitRevisionResolver revisions;
    private final int defaultMaxParallelism;

    public AutonomousRequirementService(WorkTaskService tasks, AutonomousRunService runs, GitRevisionResolver revisions,
                                        @Value("${aidev.autonomous.default-max-parallelism:0}") int defaultMaxParallelism) {
        this.tasks = tasks;
        this.runs = runs;
        this.revisions = revisions;
        if (defaultMaxParallelism < 0 || defaultMaxParallelism > 1024) {
            throw new IllegalArgumentException("aidev.autonomous.default-max-parallelism must be between 0 and 1024");
        }
        this.defaultMaxParallelism = defaultMaxParallelism;
    }

    @Transactional
    public AutonomousRequirementRun start(StartAutonomousRequirementCommand command) {
        revisions.repositoryPath(command.repositoryRef());
        String baseRevision = command.baseRevision() == null ? null
                : revisions.resolve(command.repositoryRef(), command.baseRevision());
        if (baseRevision == null && command.maxParallelism() != null && command.maxParallelism() != 1) {
            throw new IllegalArgumentException("direct directory Run maxParallelism must be 1");
        }
        String runId = command.requestId() == null
                ? "AR-" + UUID.randomUUID().toString().replace("-", "")
                : "AR-" + command.requestId();
        var taskId = AutonomousTaskIds.requirement(runId);
        tasks.createIfAbsent(new CreateWorkTaskCommand(
                taskId,
                TaskType.REQUIREMENT_ANALYSIS,
                SUBJECT_TYPE,
                runId,
                AutonomousCapabilities.forRole(command.technicalCapabilities(), "requirement-analysis"),
                "requirement-analysis",
                "Analyze the following user requirement and produce a precise engineering requirement specification. " +
                        "Resolve ambiguity conservatively from repository evidence, list assumptions explicitly, define acceptance criteria, " +
                        "and preserve the user's intent. Do not modify repository files.\n\nUSER REQUIREMENT:\n" + command.requirement(),
                command.repositoryRef(),
                baseRevision,
                command.priority(),
                Set.of()));
        int maxParallelism = baseRevision == null ? 1
                : command.maxParallelism() == null ? defaultMaxParallelism : command.maxParallelism();
        runs.createIfAbsent(runId, taskId, maxParallelism, command.maxParallelism() != null);
        return new AutonomousRequirementRun(runId, taskId);
    }
}
