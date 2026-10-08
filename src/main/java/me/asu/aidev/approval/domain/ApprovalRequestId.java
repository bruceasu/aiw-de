package me.asu.aidev.approval.domain;

import java.util.Objects;

public record ApprovalRequestId(String value) {
    public ApprovalRequestId {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) throw new IllegalArgumentException("approval request id is blank");
        if (value.length() > 128) throw new IllegalArgumentException("approval request id is too long");
    }
}
