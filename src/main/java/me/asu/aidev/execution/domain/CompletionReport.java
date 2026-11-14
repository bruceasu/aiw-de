package me.asu.aidev.execution.domain;

import java.util.Objects;

/** Structured semantic outcome reported by a successfully executed task. */
public record CompletionReport(CompletionVerdict verdict, String summary, String experience, SkillUpdate skillUpdate) {
    public record SkillUpdate(String ref,String content,String reason) {}

    public CompletionReport(CompletionVerdict verdict, String summary) {
        this(verdict, summary, null, null);
    }

    public CompletionReport(CompletionVerdict verdict,String summary,String experience) {
        this(verdict,summary,experience,null);
    }

    public CompletionReport {
        Objects.requireNonNull(verdict, "verdict");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("completion summary is blank");
        }
        summary = summary.strip();
        if (experience != null) {
            experience = experience.strip();
            if (experience.isEmpty() || experience.length() > 2000 || experience.indexOf(0) >= 0) {
                throw new IllegalArgumentException("invalid reusable experience summary");
            }
        }
    }
}
