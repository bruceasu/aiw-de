package me.asu.aidev.execution.domain;

public record LeaseToken(String value) {
    public LeaseToken {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("lease token is blank");
        if (value.length() > 128) throw new IllegalArgumentException("lease token is too long");
    }
}
