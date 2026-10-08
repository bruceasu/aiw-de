package me.asu.aidev.autonomous.application;

import me.asu.aidev.work.domain.TaskId;

public record AutonomousRequirementRun(String runId, TaskId requirementTaskId) {}
