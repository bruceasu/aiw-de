package me.asu.aidev.secretary.domain;

public final class SecretaryData {
    private SecretaryData() {}
    public enum State { DRAFT, SCHEDULED, NEEDS_CONFIRMATION, QUEUED, ACTIVE, DONE, CANCELLED, STOP_REQUESTED, NEEDS_HUMAN }
    public record Content(String title, String body, String kind, String zone, String snapshot,
                          long planVersion, String planHash, String authority, String detail) {}
    public record Work(String id, String actor, long revision, long generation, State state,
                       String planId, Long dueMs, Content content) {}
    public record Request(String id, String workId, long generation, String phase,
                          long planVersion, String sourceAttempt, boolean consumed) {}
    public record Message(String id, String userText, String reply, long sourceRevision) {}
}
