package me.asu.aidev.webconsole.application;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.fleet.application.FleetDashboardService;
import me.asu.aidev.fleet.domain.FleetDashboard;
import me.asu.aidev.webconsole.domain.WorkerIdentity;
import org.springframework.stereotype.Service;

/** Bounded, owner-filtered work attached to the existing public fleet identities. */
@Service
public class WorkerWorkView {
    private final AuthorityRepository db;
    private final FleetDashboardService fleet;
    public WorkerWorkView(AuthorityRepository db,FleetDashboardService fleet) {
        this.db=db;this.fleet=fleet;
    }
    public record Member(FleetDashboard.WorkerSummary worker,String source,String managementStatus,String mark,Long registeredAt,int pendingCount,
                         int visibleWorkCount,List<Map<String,Object>> work) {}
    public record Snapshot(long observedAt,int workerLimit,int workLimit,int totalWorkers,int totalVisibleWork,int readyTasks,
                           List<Member> members) {}
    private static final String PENDING="""
        CASE WHEN EXISTS(SELECT 1 FROM formal_question_slot qs JOIN formal_question q ON q.id=qs.question_id
          WHERE qs.source_task_id=t.id AND q.state='OPEN') OR EXISTS(
          SELECT 1 FROM execution_tool_approval q WHERE q.attempt_id=a.id AND q.state='PENDING')
          THEN 1 ELSE 0 END
        """;
    private static final String WORK = """
        FROM execution_attempt a JOIN work_task t ON t.id=a.task_id AND t.attempt_no=a.attempt_no
        LEFT JOIN execution_context x ON x.task_id=t.id
        LEFT JOIN execution_policy p ON p.plan_id=x.plan_id
        LEFT JOIN authority_owner o ON o.id=p.owner_id
        LEFT JOIN flexible_workflow_plan f ON t.subject_type='flexible-plan' AND f.id=t.subject_id
        LEFT JOIN workflow_execution w ON w.id=f.id
        LEFT JOIN execution_run_session s ON s.attempt_id=a.id
        WHERE a.worker_id IN (%s) AND (o.actor=? OR f.created_by=?)
          AND ((t.current_worker_id=a.worker_id AND t.lease_token=a.lease_token)
            OR t.state='WAITING_INPUT')
        """;
    public Snapshot read(String actor) {
        // The authenticated controller passes no actor for read-only console roles.
        // They retain fleet diagnostics without receiving private work details.
        var snapshot=fleet.dashboard(100);
        var workers=snapshot.workers().workers();
        if(workers.isEmpty())return new Snapshot(System.currentTimeMillis(),100,200,snapshot.workers().total(),0,snapshot.backlog().ready(),List.of());
        String ids=String.join(",",Collections.nCopies(workers.size(),"?"));
        var args=new ArrayList<Object>();workers.forEach(w->args.add(w.workerId()));args.add(actor);args.add(actor);
        String scope=WORK.formatted(ids);
        var counts=new HashMap<String,Integer>();
        var pendingCounts=new HashMap<String,Integer>();
        if(actor!=null)db.jdbc().queryForList("SELECT a.worker_id,COUNT(*) AS count,SUM("+PENDING+") AS pending_count "+scope+" GROUP BY a.worker_id",args.toArray())
                .forEach(r->{counts.put((String)r.get("worker_id"),((Number)r.get("count")).intValue());pendingCounts.put((String)r.get("worker_id"),((Number)r.get("pending_count")).intValue());});
        var work=actor==null?List.<Map<String,Object>>of():db.jdbc().queryForList("""
            SELECT a.id AS attempt_id,a.worker_id,a.state AS attempt_state,a.attempt_no,
              a.ai_runtime,a.ai_provider,a.ai_model,a.ai_effort,t.id AS task_id,t.instruction,t.skill_ref,
              t.state AS task_state,COALESCE(x.plan_id,f.id) AS plan_id,w.run_json,f.spec_json AS plan_spec_json,
              s.phase,s.instruction_revision,s.revision AS run_revision,s.last_contact_ms,
              CASE WHEN x.task_id IS NULL THEN 'native' ELSE 'guarded' END AS workbench_source,
            """+PENDING+" AS pending "+scope+" ORDER BY pending DESC,a.created_at_epoch_ms DESC,a.id LIMIT 200",args.toArray());
        var grouped=new HashMap<String,List<Map<String,Object>>>();
        var byAttempt=new LinkedHashMap<String,Map<String,Object>>();
        for(var item:work) {
            Object json=item.remove("run_json");
            Object planSpec=item.remove("plan_spec_json");item.put("unified",json!=null);
            if(json instanceof String value) {
                var run=db.decode(value,Map.class);
                if(run.get("spec") instanceof Map<?,?> spec)item.put("plan_title",spec.get("title"));
            } else if(planSpec instanceof String value) {
                var spec=db.decode(value,Map.class);
                if(spec.get("goal") instanceof String goal)item.put("plan_title",goal.substring(0,Math.min(160,goal.length())));
            }
            String instruction=(String)item.get("instruction");
            item.put("title",instruction==null?item.get("task_id"):instruction.substring(0,Math.min(120,instruction.length())));
            if(instruction!=null&&instruction.length()>12000){item.put("instruction",instruction.substring(0,12000));item.put("instruction_truncated",true);}
            byAttempt.put((String)item.get("attempt_id"),item);
            grouped.computeIfAbsent((String)item.get("worker_id"),ignored->new ArrayList<>()).add(item);
        }
        enrich(byAttempt);
        var origins=new HashMap<String,Map<String,Object>>();
        db.jdbc().queryForList("SELECT w.id,w.status,w.registered_at_epoch_ms,f.worker_id AS factory_id FROM worker w LEFT JOIN factory_worker_registration f ON f.worker_id=w.id WHERE w.id IN ("+ids+")",workers.stream().map(FleetDashboard.WorkerSummary::workerId).toArray())
                .forEach(r->origins.put((String)r.get("id"),r));
        var members=new ArrayList<Member>();
        for(var worker:workers) {
            var origin=origins.getOrDefault(worker.workerId(),Map.of());
            members.add(new Member(worker,origin.get("factory_id")==null?"UNKNOWN":"FACTORY",
                    Objects.toString(origin.get("status"),"UNKNOWN"),WorkerIdentity.forId(worker.workerId()).mark(),
                    origin.get("registered_at_epoch_ms") instanceof Number registered?registered.longValue():null,
                    pendingCounts.getOrDefault(worker.workerId(),0),
                    counts.getOrDefault(worker.workerId(),0),grouped.getOrDefault(worker.workerId(),List.of())));
        }
        return new Snapshot(System.currentTimeMillis(),100,200,snapshot.workers().total(),counts.values().stream().mapToInt(Integer::intValue).sum(),snapshot.backlog().ready(),members);
    }
    private void enrich(Map<String,Map<String,Object>> work) {
        if(work.isEmpty())return;
        String ids=String.join(",",Collections.nCopies(work.size(),"?"));Object[] args=work.keySet().toArray();
        db.jdbc().queryForList("""
            SELECT e.attempt_id,e.event_type,e.occurred_ms,e.payload_json FROM execution_run_event e
             WHERE e.attempt_id IN (%s) AND e.sequence_no>(SELECT COALESCE(MAX(e2.sequence_no),0)-8
               FROM execution_run_event e2 WHERE e2.attempt_id=e.attempt_id)
             ORDER BY e.attempt_id,e.sequence_no
            """.formatted(ids),args).forEach(r->{
                var item=work.get((String)r.get("attempt_id"));
                var payload=db.decode((String)r.get("payload_json"),Map.class);
                var event=new LinkedHashMap<String,Object>();event.put("type",r.get("event_type"));event.put("time",r.get("occurred_ms"));
                for(String key:List.of("toolName","status","runtimeEvent"))if(payload.get(key) instanceof String value)event.put(key,value);
                @SuppressWarnings("unchecked") var events=(List<Map<String,Object>>)item.computeIfAbsent("events",ignored->new ArrayList<Map<String,Object>>());
                events.add(event);
                if(payload.get("model") instanceof String model)item.put("ai_model",model);
                if(payload.get("effort") instanceof String effort)item.put("ai_effort",effort);
            });
        db.jdbc().queryForList("""
            SELECT attempt_id,COUNT(*) AS calls,SUM(input_tokens) AS input_tokens,SUM(cached_tokens) AS cached_tokens,
              SUM(output_tokens) AS output_tokens,COUNT(*)-COUNT(input_tokens) AS missing_input,
              COUNT(*)-COUNT(cached_tokens) AS missing_cached,
              COUNT(*)-COUNT(output_tokens) AS missing_output FROM model_usage_request
             WHERE attempt_id IN (%s) GROUP BY attempt_id
            """.formatted(ids),args).forEach(r->work.get((String)r.get("attempt_id")).put("usage",r));
        db.jdbc().queryForList("""
            SELECT e.attempt_id,e.currency,SUM(e.cost_micros) AS cost_micros,COUNT(*) AS priced_calls
              FROM model_usage_estimate e WHERE e.attempt_id IN (%s)
               AND e.cost_micros IS NOT NULL AND e.estimate_revision=(SELECT MAX(e2.estimate_revision)
                FROM model_usage_estimate e2 WHERE e2.attempt_id=e.attempt_id AND e2.request_id=e.request_id)
             GROUP BY e.attempt_id,e.currency ORDER BY e.currency
            """.formatted(ids),args).forEach(r->{
                @SuppressWarnings("unchecked") var prices=(List<Map<String,Object>>)work.get((String)r.get("attempt_id"))
                        .computeIfAbsent("prices",ignored->new ArrayList<Map<String,Object>>());
                prices.add(r);
            });
    }
}
