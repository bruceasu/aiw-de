package me.asu.aidev.goal.domain;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** Mutable editing data is distinct from immutable formal snapshots and executable plans. */
public final class GoalData {
    private GoalData() {}
    public record Message(String id, String role, String text, long sourceRevision, boolean stale) {}
    public record Content(String body, String missing, String backend, List<String> knowledgeIds,
                          List<Message> messages) {}
    public record Draft(String id, String actor, String title, long revision, long epoch,
                        boolean deleted, Content content) {}
    public record Edit(long revision, String title, String body, String missing, String backend,
                       List<String> knowledgeIds) {}
    public record Attachment(String id, String filename, String sha256, String status, String extracted, String detail) {}
    public record Version(String id, String goalId, long sourceRevision, JsonNode snapshot,
                          JsonNode editor, long editorRevision, String recommendationId, String planId) {}
}
