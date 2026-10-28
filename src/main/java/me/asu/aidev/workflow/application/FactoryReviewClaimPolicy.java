package me.asu.aidev.workflow.application;

import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A factory execution's Worker cannot claim its own independent review. */
@Component
public class FactoryReviewClaimPolicy implements WorkerTaskClaimPolicy {
    private final JdbcTemplate jdbc;
    public FactoryReviewClaimPolicy(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public boolean canClaim(WorkTask task) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM factory_plan_review WHERE review_task_id=?",Long.class,task.id().value())==0;
    }
    @Override public boolean canClaim(WorkTask task, Worker worker) {
        var rows=jdbc.queryForList("SELECT source_task_id,state FROM factory_plan_review WHERE review_task_id=?",task.id().value());
        if (rows.isEmpty()) return true;
        var row=rows.getFirst();
        if (!"PENDING".equals(row.get("state")) || row.get("source_task_id")==null) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND worker_id=?",Long.class,
                row.get("source_task_id"),worker.id().value())==0;
    }
}
