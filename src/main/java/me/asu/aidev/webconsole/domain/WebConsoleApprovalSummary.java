package me.asu.aidev.webconsole.domain;

import java.time.Instant;

/** Read-only approval inbox row. */
public record WebConsoleApprovalSummary(
        String approvalRequestId,
        String runId,
        String status,
        String resultRevision,
        String requestedBy,
        String channel,
        String decidedBy,
        Instant requestedAt,
        Instant decidedAt) {}
