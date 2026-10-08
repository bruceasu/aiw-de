package me.asu.aidev.execution.domain;

import java.util.Objects;

/** Structured semantic outcome reported by a successfully executed task. */
public record CompletionReport(CompletionVerdict verdict, String summary) {
    public CompletionReport {
        Objects.requireNonNull(verdict, "verdict");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("completion summary is blank");
        }
        summary = summary.strip();
    }
}
