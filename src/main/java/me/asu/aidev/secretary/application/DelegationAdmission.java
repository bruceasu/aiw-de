package me.asu.aidev.secretary.application;

import java.util.List;
import me.asu.aidev.secretary.domain.DelegationRule;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The old repair path must finish before the same target enters the new policy. */
@Component
public class DelegationAdmission {
    private final JdbcTemplate jdbc;
    private final WorkflowPlanService plans;
    private final SecretaryPlanGate secretary;
    public DelegationAdmission(JdbcTemplate jdbc, WorkflowPlanService plans, SecretaryPlanGate secretary) {
        this.jdbc = jdbc; this.plans = plans; this.secretary = secretary;
    }
    public void plan(String id) {
        secretary.serializePlan(id);
        List<me.asu.aidev.work.domain.WorkTask> tasks = plans.steps(id);
        for (var task : tasks.stream().sorted(java.util.Comparator.comparing(t -> t.id().value())).toList())
            jdbc.update("UPDATE work_task SET version=version WHERE id=?", task.id().value());
        jdbc.update("UPDATE flexible_workflow_plan SET version=version WHERE id=?", id);
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM workflow_repair_authorization WHERE plan_id=? AND status='PENDING'", Integer.class, id);
        if (pending != null && pending > 0) throw new IllegalStateException("Resolve or revoke the pending legacy V4 grant before admission");
        var plan = plans.get(id);
        tasks = plans.steps(id);
        for (String repair : jdbc.queryForList("SELECT repair_task_id FROM workflow_repair_authorization WHERE plan_id=? AND status='CONSUMED' AND repair_task_id IS NOT NULL", String.class, id)) {
            var position = WorkflowTaskPosition.parse(plan, new me.asu.aidev.work.domain.TaskId(repair));
            if (position.isEmpty()) throw new IllegalStateException("Legacy repair identity cannot be verified; human handling required");
            var at = position.get();
            boolean reviewed = tasks.stream().filter(t -> t.skillRef().equals("workflow-stage-reviewer") && t.state() == TaskState.COMPLETED)
                    .anyMatch(t -> WorkflowTaskPosition.parse(plan, t.id()).filter(p -> p.step() == at.step() && p.round() == at.round()).isPresent());
            if (!reviewed) throw new IllegalStateException("Finish the legacy repair and its independent review before admission");
        }
        Integer coordinating = jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task t JOIN secretary_request r ON r.id=t.id
                JOIN secretary_work w ON w.id=r.work_id
                WHERE w.plan_id=? AND t.state IN ('READY','BLOCKED','CLAIMED','RUNNING')
                """, Integer.class, id);
        if (coordinating != null && coordinating > 0) throw new IllegalStateException("Resolve the in-flight secretary coordination before admission");
    }
    public long baseline(DelegationRule rule) {
        if (rule.step() == null) return 0;
        var plan = plans.get(rule.targetId());
        var tasks = plans.steps(plan.id()).stream().filter(t -> WorkflowTaskPosition.parse(plan,t.id())
                .filter(p -> p.step() == rule.step()).isPresent()).toList();
        return switch (rule.action()) {
            case A01 -> tasks.stream().filter(t -> !t.skillRef().equals("workflow-stage-reviewer"))
                    .filter(t -> t.state() != TaskState.CANCELLED || jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?",Long.class,t.id().value()) > 0).count();
            case A03 -> tasks.stream().mapToLong(t -> {
                Number attempts = jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?", Number.class, t.id().value());
                return attempts == null ? 0 : Math.max(0, attempts.longValue()-1);
            }).sum();
            case A04 -> Math.max(0, tasks.stream().filter(t -> t.skillRef().equals("workflow-stage-reviewer")).count()
                    - tasks.stream().filter(t -> !t.skillRef().equals("workflow-stage-reviewer")).count());
            default -> 0;
        };
    }
}
