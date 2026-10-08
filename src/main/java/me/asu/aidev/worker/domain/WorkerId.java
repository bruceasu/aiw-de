package me.asu.aidev.worker.domain;

public record WorkerId(String value) {
    public WorkerId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("worker id is blank");
        if (value.length() > 128) throw new IllegalArgumentException("worker id is too long");
    }
}
