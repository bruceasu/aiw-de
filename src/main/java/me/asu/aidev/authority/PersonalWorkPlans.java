package me.asu.aidev.authority;

import java.nio.charset.StandardCharsets;
import java.util.*;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal machines may create owned drafts, never confirm them or grant execution. */
@Service
public class PersonalWorkPlans {
    public record Input(String requestId, String title, String body) {
        public Input {
            if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)
                    || title == null || title.isBlank() || title.length() > 200
                    || body == null || body.length() > 16000 || (title + body).indexOf('\0') >= 0)
                throw new IllegalArgumentException("Bounded selected personal work required");
        }
    }
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ActionAdmissionService admission;
    private final WorkflowPlanService plans;

    public PersonalWorkPlans(AuthorityRepository repo, AuthorityBoundary boundary,
            ActionAdmissionService admission, WorkflowPlanService plans) {
        this.repo = repo; this.boundary = boundary; this.admission = admission; this.plans = plans;
    }
    private String requestKey(AuthorityBoundary.Principal principal, String requestId) {
        if (!UUID.fromString(requestId).toString().equals(requestId)) throw new IllegalArgumentException("Canonical request ID required");
        return "personal-work:" + principal.worker() + ":" + requestId;
    }
    private void authenticate(AuthorityBoundary.Principal principal, String boot) {
        admission.enabled(principal);
        if (!boundary.processBoot(principal.worker(), boot)) throw new SecurityException("Current personal process required");
    }
    @Transactional
    public Map<String, Object> create(AuthorityBoundary.Principal principal, String boot, Input input) {
        boundary.lock(); authenticate(principal, boot);
        String goal = input.title() + (input.body().isBlank() ? "" : "\n\n" + input.body());
        var step = new WorkflowPlanSpec.Step("analyze", Set.of("requirement-analysis"), Set.of(),
                "software-requirements-analyst",
                "Analyze the stated personal work. Identify the tools, access, output location and acceptance criteria needed to complete it. Report missing requirements. Do not claim that the requested work has been executed.",
                null, null, Set.of());
        var spec = new WorkflowPlanSpec(goal, WorkflowPlanSpec.Source.MANAGER, List.of(step));
        var plan = plans.draftFromRequest(spec, repo.actor(principal.owner()), requestKey(principal, input.requestId()));
        return receipt(principal, input.requestId(), plan);
    }
    @Transactional
    public Map<String, Object> read(AuthorityBoundary.Principal principal, String boot, String requestId) {
        boundary.lock(); authenticate(principal, boot);
        String actor = repo.actor(principal.owner());
        String id = "FP-" + UUID.nameUUIDFromBytes((actor + "\n" + requestKey(principal, requestId)).getBytes(StandardCharsets.UTF_8));
        // Read only this owner's deterministically created draft, not arbitrary Plan IDs.
        if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM flexible_workflow_plan WHERE id=? AND created_by=?", Long.class, id, actor) != 1)
            throw new NoSuchElementException("Personal Plan not found");
        return receipt(principal, requestId, plans.get(id));
    }
    private Map<String, Object> receipt(AuthorityBoundary.Principal principal, String requestId, WorkflowPlan plan) {
        return Map.of("requestId", requestId, "ownerId", principal.owner(), "workerId", principal.worker(),
                "platformInstanceId", boundary.instance(), "planId", plan.id(), "state", plan.state().name(),
                "version", plan.version(), "creationGrantsExecution", false);
    }
}
