package me.asu.aidev.execution.domain;

/** Immutable snapshot of the approved parameters selected for one execution attempt. */
public record AiSelection(String profileId, String profileVersion, String provider, String runtime,
                          String model, String effort, boolean fast, String reason) {}
