package me.asu.aidev.work.domain;

import java.time.Instant;
import java.util.Objects;

public record TaskInstructionRevision(
        TaskId taskId,
        int revision,
        String instruction,
        String actor,
        String reason,
        Integer predecessorRevision,
        Instant createdAt) {

    public TaskInstructionRevision {
        Objects.requireNonNull(taskId, "taskId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        instruction = instruction == null ? "" : instruction.strip();
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is blank");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is blank");
        if (predecessorRevision != null && predecessorRevision != revision - 1) {
            throw new IllegalArgumentException("predecessorRevision must immediately precede revision");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
