package me.asu.aidev.execution.pricing;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;

/** Read-only owner-scoped projection; incomplete usage and estimates remain null. */
@Service
public class ModelUsageView {
    public record Snapshot(String protocol, String ownerId, long observedAt, int limit,
                           Map<String,Object> totals, List<Map<String,Object>> currencies, List<Map<String,Object>> calls) {}
    public record Query(String planId, String taskId) {}
    private final AuthorityRepository repo;
    public ModelUsageView(AuthorityRepository repo) { this.repo = repo; }

    public Snapshot read(String owner, Query query) {
        String actor = repo.actor(owner);
        if (query == null) throw new IllegalArgumentException("MODEL_USAGE_QUERY_REQUIRED");
        String plan = filter(query.planId()), task = filter(query.taskId());
        var args = new ArrayList<Object>();
        args.add(owner); args.add(actor);
        String scope = """
              FROM model_usage_request u JOIN work_task t ON t.id=u.task_id
              LEFT JOIN execution_context x ON x.task_id=t.id
              LEFT JOIN execution_policy p ON p.plan_id=x.plan_id
              LEFT JOIN flexible_workflow_plan f ON t.subject_type='flexible-plan' AND f.id=t.subject_id
              LEFT JOIN model_usage_estimate e ON e.attempt_id=u.attempt_id AND e.request_id=u.request_id
                    AND e.estimate_revision=(SELECT MAX(e2.estimate_revision) FROM model_usage_estimate e2
                           WHERE e2.attempt_id=u.attempt_id AND e2.request_id=u.request_id)
             WHERE (p.owner_id=? OR f.created_by=?)
            """;
        if (plan != null) { scope += " AND COALESCE(p.plan_id,f.id)=?"; args.add(plan); }
        if (task != null) { scope += " AND u.task_id=?"; args.add(task); }
        var totals = repo.jdbc().queryForMap("""
            SELECT COUNT(*) AS calls, SUM(u.input_tokens) AS known_input, SUM(u.cached_tokens) AS known_cached,
                   SUM(u.output_tokens) AS known_output, COUNT(*)-COUNT(u.input_tokens) AS missing_input,
                   COUNT(*)-COUNT(u.cached_tokens) AS missing_cached, COUNT(*)-COUNT(u.output_tokens) AS missing_output,
                   COUNT(*)-COUNT(e.cost_micros) AS without_estimate
            """ + scope, args.toArray());
        var currencies = repo.jdbc().queryForList("""
            SELECT e.currency, SUM(e.cost_micros) AS known_cost_micros, COUNT(*) AS estimated_calls
            """ + scope + " AND e.cost_micros IS NOT NULL GROUP BY e.currency ORDER BY e.currency", args.toArray());
        var rows = repo.jdbc().queryForList("""
            SELECT u.attempt_id,u.request_id,u.task_id,u.worker_id,u.provider,u.model,u.protocol,u.purpose,
                   u.connection_kind,u.price_scope,u.service_tier,u.started_ms,u.finished_ms,u.state,
                   u.input_tokens,u.cached_tokens,u.output_tokens,u.cache_write_tokens,
                   e.currency,e.cost_micros,e.snapshot_id,e.rule_id,e.calculated_ms,
                   COALESCE(p.plan_id,f.id) AS plan_id
            """ + scope + " ORDER BY u.started_ms DESC,u.attempt_id,u.request_id LIMIT 100", args.toArray());
        return new Snapshot("aiw.model-usage.v1", owner, System.currentTimeMillis(), 100, totals, List.copyOf(currencies), List.copyOf(rows));
    }

    private static String filter(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 128) throw new IllegalArgumentException("MODEL_USAGE_FILTER_INVALID");
        return value;
    }
}
