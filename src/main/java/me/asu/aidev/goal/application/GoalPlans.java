package me.asu.aidev.goal.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import me.asu.aidev.goal.domain.GoalData.*;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.workflow.application.*;
import me.asu.aidev.workflow.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalPlans {
    private final GoalRepository repo;
    private final GoalDiscussionService discussion;
    private final WorkflowEditorService editor;
    private final WorkflowRecommendationService recommendations;
    private final GoalWorkspace workspace;
    public GoalPlans(GoalRepository repo, GoalDiscussionService discussion, WorkflowEditorService editor,
        WorkflowRecommendationService recommendations, GoalWorkspace workspace) {
        this.repo = repo; this.discussion = discussion; this.editor = editor; this.recommendations = recommendations; this.workspace = workspace;
    }
    @Transactional public Version formalize(String goal, String actor, long revision, boolean acceptMissing) {
        Draft draft = discussion.expected(goal, actor, revision);
        repo.lock(draft);
        if (draft.content().body().isBlank()) throw new IllegalArgumentException("Write a goal before formalizing it");
        if (!draft.content().missing().isBlank() && !acceptMissing) throw new IllegalArgumentException("Explicitly accept missing information to continue with a not-ready goal");
        var existing = repo.jdbc.query("SELECT id FROM goal_version WHERE goal_id=? AND source_revision=?", (rs,n) -> rs.getString(1), goal, revision);
        if (!existing.isEmpty()) return repo.version(existing.getFirst(), actor);
        String id = UUID.randomUUID().toString();
        String text = draft.content().body() + (draft.content().missing().isBlank() ? "" : "\n\nNOT READY — missing information accepted by the user:\n" + draft.content().missing());
        if (text.length() > 16384) throw new IllegalArgumentException("Formal goal with missing information exceeds 16384 characters");
        var snapshot = Map.of("title", draft.title(), "goal", text, "missing", draft.content().missing(), "ready", draft.content().missing().isBlank(),
            "backend", draft.content().backend(), "knowledgeIds", draft.content().knowledgeIds(),
            "attachments", repo.attachments(goal, actor).stream().map(a -> Map.of("id", a.id(), "name", a.filename(), "sha256", a.sha256())).toList());
        repo.jdbc.update("INSERT INTO goal_version VALUES (?,?,?,?,?,0,NULL,NULL)", id, goal, revision, repo.encode(snapshot),
            repo.encode(Map.of("goal", text, "source", "MANAGER", "steps", List.of())));
        return repo.version(id, actor);
    }
    private Version expected(String id, String actor, long revision) {
        Version v = repo.version(id, actor);
        if (v.editorRevision() != revision || v.planId() != null) throw new IllegalStateException("Editor changed or already saved as a plan; reload");
        if (repo.jdbc.update("UPDATE goal_version SET editor_revision=editor_revision WHERE id=? AND editor_revision=? AND plan_id IS NULL", id, revision) != 1)
            throw new IllegalStateException("Editor changed; reload");
        return v;
    }
    @Transactional public Version saveEditor(String id, String actor, long revision, JsonNode spec) {
        Version v = expected(id, actor, revision);
        if (spec == null || !spec.isObject() || !spec.path("steps").isArray() || spec.path("steps").size() > 32
            || !spec.path("goal").asText().equals(v.snapshot().path("goal").asText()) || repo.encode(spec).length() > 128000)
            throw new IllegalArgumentException("Editor must preserve this formal goal, with at most 32 steps and 128000 characters");
        repo.jdbc.update("UPDATE goal_version SET editor_json=?,editor_revision=editor_revision+1,recommendation_id=NULL WHERE id=?", repo.encode(spec), id);
        return repo.version(id, actor);
    }
    @Transactional public Version analyze(String id, String actor, long revision) {
        Version v = expected(id, actor, revision);
        if (v.recommendationId() != null) return v;
        var reply = recommendations.request(new WorkflowRecommendationService.Request(UUID.randomUUID().toString(), revision, v.snapshot().path("goal").asText()), actor);
        repo.jdbc.update("UPDATE goal_version SET recommendation_id=? WHERE id=?", reply.id(), id);
        return repo.version(id, actor);
    }
    public WorkflowRecommendationService.Result analysis(String id, String actor) {
        Version v = repo.version(id, actor);
        if (v.recommendationId() == null) throw new IllegalStateException("No analysis requested");
        return recommendations.get(v.recommendationId(), actor);
    }
    @Transactional public Version applyAnalysis(String id, String actor, long revision) {
        Version v = expected(id, actor, revision);
        var result = analysis(id, actor);
        if (!"READY_TO_APPLY".equals(result.state()) || result.inputVersion() != revision)
            throw new IllegalStateException("AI Steps is unavailable, invalid or based on an older editor; continue editing manually");
        return saveEditor(id, actor, revision, repo.json.valueToTree(result.plan()));
    }
    @Transactional public Version prepare(String id, String actor, long revision, boolean shareConfirmed) {
        Version v = repo.version(id, actor);
        if (v.planId() != null) return v;
        if (!shareConfirmed) throw new IllegalArgumentException("Confirm the handoff to the existing operator-visible plans console");
        v = expected(id, actor, revision);
        WorkflowPlanSpec spec;
        try { spec = repo.json.treeToValue(v.editor(), WorkflowPlanSpec.class); }
        catch (Exception e) { throw new IllegalArgumentException("Complete the steps before preparing an execution plan", e); }
        editor.validateCandidate(spec);
        final String versionId = v.id();
        String[] temporary = {null};
        var steps = spec.steps().stream().map(s -> {
            boolean needs = editor.roles().stream().anyMatch(r -> r.skillRef().equals(s.skillRef()) && r.needsWorkspace());
            String target = s.repositoryRef();
            if (needs && target == null) {
                if (temporary[0] == null) temporary[0] = workspace.ensure(versionId);
                target = temporary[0];
            }
            return new WorkflowPlanSpec.Step(s.key(), s.requiredSkills(), s.preferredSkills(), s.skillRef(), s.instruction(), target,
                s.baseRevision(), s.dependsOn(), s.model(), s.effort());
        }).toList();
        WorkflowPlan saved = editor.draft(new WorkflowPlanSpec(spec.goal(), spec.source(), steps), actor);
        repo.jdbc.update("UPDATE goal_version SET plan_id=?,editor_revision=editor_revision+1 WHERE id=? AND plan_id IS NULL", saved.id(), id);
        return repo.version(id, actor);
    }
}
