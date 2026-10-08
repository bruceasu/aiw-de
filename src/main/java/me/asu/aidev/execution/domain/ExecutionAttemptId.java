package me.asu.aidev.execution.domain;

public record ExecutionAttemptId(String value) {
    public ExecutionAttemptId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("attempt id is blank");
        if (value.length() > 128) throw new IllegalArgumentException("attempt id is too long");
    }
}
