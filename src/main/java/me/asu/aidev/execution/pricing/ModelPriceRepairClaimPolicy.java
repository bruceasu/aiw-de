package me.asu.aidev.execution.pricing;

import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.port.TaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.stereotype.Component;

/** Do not restart stopped, stale or repeatedly expired repair work. */
@Component
public class ModelPriceRepairClaimPolicy implements TaskClaimPolicy {
    private final AuthorityRepository repo;
    public ModelPriceRepairClaimPolicy(AuthorityRepository repo) { this.repo=repo; }
    @Override public boolean canClaim(WorkTask task) {
        if (!ModelPriceRepairDispatch.SUBJECT.equals(task.subjectType())) return true;
        Long valid = repo.jdbc().queryForObject("""
            SELECT COUNT(*) FROM model_price_repair_material m
              JOIN model_price_collection_state s ON s.provider=m.provider
              JOIN model_price_collection_failure f ON f.provider=m.provider
             WHERE m.id=? AND m.status='PREPARED' AND s.status='FAILED' AND f.active=TRUE AND f.failure_count>=2
               AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
               AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
               AND (SELECT COUNT(*) FROM execution_attempt a WHERE a.task_id=?)<3
               AND NOT EXISTS (SELECT 1 FROM execution_attempt a WHERE a.task_id=? AND a.stop_requested_at_epoch_ms IS NOT NULL)
            """, Long.class, task.subjectId(), task.id().value(), task.id().value());
        return valid != null && valid == 1;
    }
}
