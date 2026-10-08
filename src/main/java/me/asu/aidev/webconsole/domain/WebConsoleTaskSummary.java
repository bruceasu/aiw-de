package me.asu.aidev.webconsole.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import me.asu.aidev.work.domain.TaskInstructionRevision;

/** Read-only task row used by Run Detail. */
public record WebConsoleTaskSummary(
        String taskId,
        String taskType,
        String state,
        Set<String> requiredCapabilities,
        String currentWorkerId,
        int attemptNo,
        String repositoryRef,
        String baseRevision,
        String instruction,
        Instant createdAt,
        Instant updatedAt,
        long version,
        int instructionRevision,
        Integer executingInstructionRevision,
        Instant stopRequestedAt,
        Instant stopAcknowledgedAt,
        List<TaskInstructionRevision> instructionHistory,
        List<String> correctionTaskIds) {

    public WebConsoleTaskSummary {
        instructionHistory = instructionHistory == null ? List.of() : List.copyOf(instructionHistory);
        correctionTaskIds = correctionTaskIds == null ? List.of() : List.copyOf(correctionTaskIds);
    }

    public WebConsoleTaskSummary(String taskId, String taskType, String state, Set<String> requiredCapabilities,
                                 String currentWorkerId, int attemptNo, String repositoryRef, String baseRevision,
                                 String instruction, Instant createdAt, Instant updatedAt) {
        this(taskId, taskType, state, requiredCapabilities, currentWorkerId, attemptNo, repositoryRef,
                baseRevision, instruction, createdAt, updatedAt, 0, 1, null, null, null, List.of(), List.of());
    }
}
