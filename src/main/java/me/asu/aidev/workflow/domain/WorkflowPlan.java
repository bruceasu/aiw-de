package me.asu.aidev.workflow.domain;

import java.time.Instant;

public record WorkflowPlan(String id, State state, long version, WorkflowPlanSpec spec,
                           String createdBy, String updatedBy, String reason,
                           Instant createdAt, Instant updatedAt) {
    public enum State { DRAFT, CONFIRMED, WITHDRAWN, STOPPED }
}
