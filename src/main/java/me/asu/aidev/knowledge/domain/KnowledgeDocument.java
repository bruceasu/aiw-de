package me.asu.aidev.knowledge.domain;

import me.asu.aidev.work.domain.TaskType;
import java.time.Instant;
import java.util.List;

/** Immutable document produced by one completed planning-stage task. */
public record KnowledgeDocument(
        String id, String runId, TaskType documentType, String repositoryRef,
        String baseRevision, String resultRevision, int attemptNo,
        String contentHash, String markdown, Instant createdAt,
        List<String> upstreamDocumentIds) {}
