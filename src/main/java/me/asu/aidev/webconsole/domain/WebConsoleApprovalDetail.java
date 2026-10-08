package me.asu.aidev.webconsole.domain;

import java.time.Instant;

/** One saved approval and evidence tied to its exact code-result attempt. */
public record WebConsoleApprovalDetail(
        String approvalRequestId,
        String runId,
        String status,
        String resultRevision,
        String sourceTaskId,
        String sourceAttemptId,
        Instant sourceFinishedAt,
        String sourceSummary,
        String gateStatus,
        Instant gateClosedAt,
        String publicationRef,
        Evidence review,
        Evidence verification,
        String evidenceProblem,
        String requestedBy,
        String channel,
        Instant requestedAt,
        String decidedBy,
        String decisionNote,
        Instant decidedAt) {

    public boolean evidenceAvailable() { return evidenceProblem == null; }

    public record Evidence(String taskId, String attemptId, String verdict, String summary,
                           Instant finishedAt, String problem) {
        public boolean available() { return problem == null; }
    }
}
