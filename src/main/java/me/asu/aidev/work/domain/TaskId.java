package me.asu.aidev.work.domain;

public record TaskId(String value) {
    public TaskId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("task id is blank");
        if (value.length() > 128) throw new IllegalArgumentException("task id is too long");
    }
}
