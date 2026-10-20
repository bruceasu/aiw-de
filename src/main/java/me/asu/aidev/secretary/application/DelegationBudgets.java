package me.asu.aidev.secretary.application;

import java.util.List;
import java.util.Map;
import me.asu.aidev.secretary.domain.DelegationRule;
import me.asu.aidev.secretary.jdbc.DelegationRepository;
import me.asu.aidev.secretary.jdbc.DelegationRepository.Grant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cumulative identity belongs to the target, never to a grant or Worker. */
@Service
public class DelegationBudgets {
    private final JdbcTemplate jdbc;
    private final DelegationBoundary boundary;
    private final DelegationRepository repo;
    private final DelegationTargets targets;
    public DelegationBudgets(JdbcTemplate jdbc, DelegationBoundary boundary, DelegationRepository repo, DelegationTargets targets) {
        this.jdbc = jdbc; this.boundary = boundary; this.repo = repo; this.targets = targets;
    }
    public record Budget(String target, String action, long limit, long used, long reserved) {}
    public List<Budget> list() {
        return jdbc.query("SELECT * FROM delegation_budget ORDER BY target_key,action", (rs,n) -> new Budget(
                rs.getString("target_key"),rs.getString("action"),rs.getLong("limit_value"),rs.getLong("used_value"),rs.getLong("reserved_value")));
    }
    public Budget current(DelegationRule rule) {
        String key = targets.key(rule);
        // Existing automatic rounds and explicit human repair/retry/review are a floor.
        long baseline = targets.baseline(rule);
        jdbc.update("UPDATE delegation_budget SET used_value=CASE WHEN used_value<? THEN ? ELSE used_value END WHERE target_key=? AND action=?",
                baseline, baseline, key, rule.action().name());
        return list().stream().filter(b -> b.target().equals(key) && b.action().equals(rule.action().name())).findFirst().orElseThrow();
    }
    public void consume(Grant grant, String request, boolean reserved) {
        Budget budget = current(grant.rule());
        long limit = Math.min(budget.limit(), grant.rule().maximumUses());
        if (budget.used() >= limit) throw new IllegalStateException("Cumulative target/action budget is exhausted");
        if (jdbc.update("UPDATE delegation_budget SET used_value=used_value+1,reserved_value=reserved_value+? WHERE target_key=? AND action=? AND used_value<?",
                reserved ? 1 : 0, budget.target(), budget.action(), limit) != 1)
            throw new IllegalStateException("Cumulative budget changed");
        repo.audit(budget.target(),grant.id(),request,"delegate:"+grant.workerId(),reserved ? "ROUND_RESERVED" : "BUDGET_CONSUMED",
                "One unit; action " + budget.action() + "; grant revision " + grant.revision());
    }
    @Transactional public void supplement(String target, String action, long amount, String actor) {
        boundary.lock();
        if (amount < 1 || amount > Integer.MAX_VALUE) throw new IllegalArgumentException("A positive explicit increment is required");
        // The controller establishes the action-specific human role; preserve all prior consumption.
        if (jdbc.update("UPDATE delegation_budget SET limit_value=limit_value+? WHERE target_key=? AND action=? AND limit_value<=?",
                amount,target,action,Long.MAX_VALUE-amount) != 1) throw new IllegalStateException("Budget missing or increment too large");
        repo.audit(target,null,null,actor,"BUDGET_SUPPLEMENTED","Added " + amount + " units to " + action + "; consumption preserved");
    }
    public void track(String task, String target, boolean reserved) {
        jdbc.update("INSERT INTO delegation_task VALUES(?,?,1,?,FALSE,FALSE) ON CONFLICT(task_id) DO NOTHING",task,target,reserved);
    }
    public void settle(String task, boolean release) {
        var rows = jdbc.queryForList("SELECT target_key FROM delegation_task WHERE task_id=? AND reserved=TRUE AND settled=FALSE",String.class,task);
        if (rows.isEmpty()) return;
        if (jdbc.update("UPDATE delegation_task SET settled=TRUE WHERE task_id=? AND settled=FALSE",task) != 1) return;
        jdbc.update("UPDATE delegation_budget SET reserved_value=reserved_value-1,used_value=used_value-? WHERE target_key=? AND action='A01' AND reserved_value>0",
                release ? 1 : 0,rows.getFirst());
        repo.audit(rows.getFirst(),null,null,"system",release ? "ROUND_RELEASED" : "ROUND_SETTLED",
                task + (release ? "; cancelled before execution started" : "; execution attempted; independent review or failure recorded"));
    }
}
