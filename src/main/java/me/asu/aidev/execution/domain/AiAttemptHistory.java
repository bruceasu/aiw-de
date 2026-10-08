package me.asu.aidev.execution.domain;

public record AiAttemptHistory(String profileId, String profileVersion, String model,
                               String effort, boolean fast, String failureCategory, String state) {}
