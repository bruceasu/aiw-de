package me.asu.aidev.execution.guard;

import java.math.BigInteger;
import java.util.*;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** One platform ledger. Callers serialize target -> worker -> task with the authority lock. */
@Component
public class ExecutionLedger {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.application.TaskExecutionService> executionService;
    private final AuthorityRepository repo; private final AuthorityBoundary boundary;
    public ExecutionLedger(AuthorityRepository repo,AuthorityBoundary boundary) { this.repo=repo;this.boundary=boundary; }
    void configure(Policy policy) {
        var budget=policy.budget();
        limit(policy.planId(),Dimension.TIME_MS,"",budget.timeMs());
        limit(policy.planId(),Dimension.TASKS,"",budget.tasks());
        limit(policy.planId(),Dimension.COST_MICROS,"",budget.costMicros());
    }
    private void limit(String plan,Dimension dimension,String loop,Long value) {
        repo.jdbc().update("""
                INSERT INTO execution_budget(plan_id,dimension,loop_id,limit_value) VALUES(?,?,?,?)
                ON CONFLICT(plan_id,dimension,loop_id) DO UPDATE SET limit_value=excluded.limit_value
                """,plan,dimension.name(),loop,value);
    }
    public List<Map<String,Object>> snapshot(String plan) {
        return repo.jdbc().queryForList("SELECT * FROM execution_budget WHERE plan_id=? ORDER BY dimension,loop_id",plan);
    }
    boolean available(String plan,Dimension d,String loop,long needed) {
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_budget WHERE plan_id=? AND dimension=? AND loop_id=?",plan,d.name(),loop);
        if(rows.size()!=1) return false;
        var b=rows.getFirst(); if(b.get("limit_value")==null || number(b,"unknown_count")>0) return false;
        return BigInteger.valueOf(number(b,"limit_value")).subtract(BigInteger.valueOf(number(b,"used_value")))
                .subtract(BigInteger.valueOf(number(b,"reserved_value"))).compareTo(BigInteger.valueOf(needed))>=0;
    }
    boolean admits(Policy p) {
        return p.budget().configured() && available(p.planId(),Dimension.TASKS,"",0)
                && available(p.planId(),Dimension.TIME_MS,"",p.budget().attemptTimeMs())
                && available(p.planId(),Dimension.COST_MICROS,"",p.budget().attemptCostMicros());
    }
    void task(String plan) {
        if(!available(plan,Dimension.TASKS,"",1)) throw new IllegalStateException("TASK_BUDGET_CONFIGURATION_OR_INCREMENT_REQUIRED");
        repo.jdbc().update("UPDATE execution_budget SET used_value=used_value+1 WHERE plan_id=? AND dimension='TASKS' AND loop_id=''",plan);
    }
    void reserve(String task,String attempt,Policy p) {
        if(!repo.jdbc().queryForList("SELECT attempt_id FROM execution_meter WHERE attempt_id=?",String.class,attempt).isEmpty()) return;
        if(!admits(p)) throw new IllegalStateException("EXECUTION_BUDGET_CONFIGURATION_OR_INCREMENT_REQUIRED");
        var b=p.budget();
        repo.jdbc().update("UPDATE execution_budget SET reserved_value=reserved_value+? WHERE plan_id=? AND dimension='TIME_MS' AND loop_id=''",b.attemptTimeMs(),p.planId());
        repo.jdbc().update("UPDATE execution_budget SET reserved_value=reserved_value+? WHERE plan_id=? AND dimension='COST_MICROS' AND loop_id=''",b.attemptCostMicros(),p.planId());
        repo.jdbc().update("INSERT INTO execution_meter(attempt_id,task_id,plan_id,reserved_time,reserved_cost,price_json) VALUES(?,?,?,?,?,?)",
                attempt,task,p.planId(),b.attemptTimeMs(),b.attemptCostMicros(),repo.encode(b.price()));
    }
    void start(String task,String attempt) {
        if(repo.jdbc().update("UPDATE execution_meter SET started_ms=? WHERE attempt_id=? AND task_id=? AND started_ms IS NULL AND settled=FALSE",
                System.currentTimeMillis(),attempt,task)!=1) throw new IllegalStateException("Exact unstarted claim reservation required");
    }
    /** Usage is the attempt's cumulative vector, never a delta inferred from retries. */
    void usage(String attempt,Usage usage,boolean unknown) {
        var meter=repo.jdbc().queryForMap("SELECT * FROM execution_meter WHERE attempt_id=?",attempt);
        if(AuthorityRepository.flag(meter.get("settled"))) throw new IllegalStateException("Meter already settled");
        boolean invalid=usage!=null && (usage.inputTokens()<number(meter,"input_tokens") || usage.cachedTokens()<number(meter,"cached_tokens") || usage.outputTokens()<number(meter,"output_tokens"));
        if(unknown || invalid) repo.jdbc().update("UPDATE execution_meter SET usage_unknown=TRUE WHERE attempt_id=?",attempt);
        else if(usage!=null) repo.jdbc().update("UPDATE execution_meter SET input_tokens=?,cached_tokens=?,output_tokens=? WHERE attempt_id=?",
                usage.inputTokens(),usage.cachedTokens(),usage.outputTokens(),attempt);
    }
    void settle(String attempt,long finishedMs) {
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_meter WHERE attempt_id=?",attempt);
        if(rows.isEmpty()) return;var meter=rows.getFirst();if(AuthorityRepository.flag(meter.get("settled"))) return;
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE attempt_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,attempt)>0)
            throw new IllegalStateException("UNKNOWN_OPERATION_REQUIRES_RECONCILIATION_AND_QUIESCENCE");
        Long elapsed=AuthorityRepository.flag(meter.get("time_unknown"))?null:meter.get("started_ms")==null?0L:Math.max(0,finishedMs-number(meter,"started_ms"));
        var price=repo.decode(String.valueOf(meter.get("price_json")),Price.class);
        boolean unknown=AuthorityRepository.flag(meter.get("usage_unknown"));
        Long cost=unknown?null:cost(price,new Usage(number(meter,"input_tokens"),number(meter,"cached_tokens"),number(meter,"output_tokens")));
        String plan=String.valueOf(meter.get("plan_id"));
        settleDimension(plan,Dimension.TIME_MS,elapsed,number(meter,"reserved_time"));
        // Unknown cost retains its approved exposure. It is never silently refunded or represented as zero.
        settleDimension(plan,Dimension.COST_MICROS,cost,number(meter,"reserved_cost"));
        repo.jdbc().update("UPDATE execution_meter SET settled=TRUE,elapsed_ms=?,cost_micros=? WHERE attempt_id=?",elapsed,cost,attempt);
    }
    private void settleDimension(String plan,Dimension dimension,Long actual,long reserved) {
        if(actual==null) { repo.jdbc().update("UPDATE execution_budget SET unknown_count=unknown_count+1 WHERE plan_id=? AND dimension=? AND loop_id=''",plan,dimension.name());return; }
        var row=repo.jdbc().queryForMap("SELECT * FROM execution_budget WHERE plan_id=? AND dimension=? AND loop_id=''",plan,dimension.name());
        long used=Math.addExact(number(row,"used_value"),actual),remaining=Math.subtractExact(number(row,"reserved_value"),reserved);
        if(remaining<0) throw new IllegalStateException("Budget reservation underflow");
        repo.jdbc().update("UPDATE execution_budget SET used_value=?,reserved_value=? WHERE plan_id=? AND dimension=? AND loop_id=''",used,remaining,plan,dimension.name());
    }
    @Transactional public Object reconcileMeter(MeterReconcile input) {
        boundary.lock();var meter=repo.jdbc().queryForMap("SELECT * FROM execution_meter WHERE attempt_id=?",input.attemptId());
        String plan=String.valueOf(meter.get("plan_id"));
        String owner=repo.jdbc().queryForObject("SELECT owner_id FROM execution_policy WHERE plan_id=?",String.class,plan);
        if(boundary.delegated() || boundary.human()==null || !Objects.equals(repo.actor(owner),boundary.human())) throw new SecurityException("Human owner must verify actual consumption");
        String json=repo.encode(input);var previous=repo.jdbc().queryForList("SELECT decision_json FROM execution_meter_reconciliation WHERE attempt_id=?",String.class,input.attemptId());
        if(!previous.isEmpty()) { if(!previous.getFirst().equals(json)) throw new IllegalStateException("METER_RECONCILIATION_CONFLICT");return snapshot(plan); }
        if(!AuthorityRepository.flag(meter.get("settled"))) throw new IllegalStateException("Confirm execution quiescence and settle the attempt first");
        if((meter.get("elapsed_ms")==null)!=(input.actualElapsedMs()!=null) || (meter.get("cost_micros")==null)!=(input.actualCostMicros()!=null)) throw new IllegalArgumentException("Resolve precisely the unknown dimensions; known consumption is immutable");
        if(input.actualElapsedMs()!=null) resolve(plan,Dimension.TIME_MS,input.actualElapsedMs(),number(meter,"reserved_time"));
        if(input.actualCostMicros()!=null) resolve(plan,Dimension.COST_MICROS,input.actualCostMicros(),number(meter,"reserved_cost"));
        repo.jdbc().update("UPDATE execution_meter SET elapsed_ms=COALESCE(elapsed_ms,?),cost_micros=COALESCE(cost_micros,?) WHERE attempt_id=?",input.actualElapsedMs(),input.actualCostMicros(),input.attemptId());
        repo.jdbc().update("INSERT INTO execution_meter_reconciliation VALUES(?,?,?,?)",input.attemptId(),json,boundary.human(),System.currentTimeMillis());
        repo.audit("PLAN:"+plan,null,input.attemptId(),boundary.human(),"EXECUTION_CONSUMPTION_RECONCILED",AuthorityRepository.hash(json));
        executionService.getObject().requestGuardedAdvance(plan);
        return snapshot(plan);
    }
    private void resolve(String plan,Dimension dimension,long actual,long reserved) {
        settleDimension(plan,dimension,actual,reserved);
        if(repo.jdbc().update("UPDATE execution_budget SET unknown_count=unknown_count-1 WHERE plan_id=? AND dimension=? AND loop_id='' AND unknown_count>0",plan,dimension.name())!=1) throw new IllegalStateException("Unknown exposure accounting mismatch");
    }
    private long cost(Price p,Usage u) {
        return BigInteger.valueOf(u.inputTokens()-u.cachedTokens()).multiply(BigInteger.valueOf(p.inputPerMillion()))
                .add(BigInteger.valueOf(u.cachedTokens()).multiply(BigInteger.valueOf(p.cachedPerMillion())))
                .add(BigInteger.valueOf(u.outputTokens()).multiply(BigInteger.valueOf(p.outputPerMillion())))
                .add(BigInteger.valueOf(999_999)).divide(BigInteger.valueOf(1_000_000)).longValueExact();
    }
    @Transactional public Object increment(Increment input,String actor) {
        boundary.lock();
        if(!boundary.delegated()) actor=boundary.human();
        var policy=repo.jdbc().queryForMap("SELECT owner_id FROM execution_policy WHERE plan_id=?",input.planId());
        if(boundary.delegated()) {
            if(!Objects.equals(policy.get("owner_id"),boundary.principal().owner())) throw new SecurityException("Budget owner mismatch");
        } else if(boundary.human()==null || !Objects.equals(repo.actor(String.valueOf(policy.get("owner_id"))),boundary.human()))
            throw new SecurityException("Current budget owner required");
        String digest=AuthorityRepository.hash(repo.encode(input));
        var previous=repo.jdbc().queryForList("SELECT request_digest FROM execution_increment WHERE decision_id=?",String.class,input.decisionId());
        if(!previous.isEmpty()) {
            if(!previous.getFirst().equals(digest)) throw new IllegalStateException("INCREMENT_ID_CONFLICT");
            return snapshot(input.planId());
        }
        String loop=Objects.toString(input.loopId(),"");
        if(input.dimension()==Dimension.LOOP_BACKJUMPS) loop(input.planId(),loop);
        var b=repo.jdbc().queryForMap("SELECT limit_value FROM execution_budget WHERE plan_id=? AND dimension=? AND loop_id=?",input.planId(),input.dimension().name(),loop);
        if(b.get("limit_value")==null) throw new IllegalStateException("Configure required limits before incrementing");
        long updated=Math.addExact(number(b,"limit_value"),input.amount());
        if(updated>input.cumulativeCeiling()) throw new SecurityException("Increment exceeds explicit cumulative ceiling");
        repo.jdbc().update("UPDATE execution_budget SET limit_value=? WHERE plan_id=? AND dimension=? AND loop_id=?",updated,input.planId(),input.dimension().name(),loop);
        repo.jdbc().update("INSERT INTO execution_increment VALUES(?,?,?,?,?,?)",input.decisionId(),input.planId(),digest,repo.encode(input),actor,System.currentTimeMillis());
        repo.audit("PLAN:"+input.planId(),null,input.decisionId(),actor,"EXECUTION_BUDGET_INCREMENT",input.dimension().name()+" +"+input.amount());
        executionService.getObject().requestGuardedAdvance(input.planId());
        return snapshot(input.planId());
    }
    private void loop(String plan,String id) {
        text(id,128);
        repo.jdbc().update("INSERT INTO execution_budget(plan_id,dimension,loop_id,limit_value) VALUES(?,'LOOP_BACKJUMPS',?,3) ON CONFLICT(plan_id,dimension,loop_id) DO NOTHING",plan,id);
    }
    /** The calling graph transition and unique transition ID must share this transaction. */
    @Transactional public void backjump(String plan,String id,String transitionId) {
        boundary.lock();loop(plan,id);
        text(transitionId,128);
        var previous=repo.jdbc().queryForList("SELECT plan_id,loop_id FROM execution_backjump WHERE transition_id=?",transitionId);
        if(!previous.isEmpty()) { if(!Objects.equals(previous.getFirst().get("plan_id"),plan) || !Objects.equals(previous.getFirst().get("loop_id"),id)) throw new IllegalStateException("BACKJUMP_TRANSITION_CONFLICT");return; }
        if(!available(plan,Dimension.LOOP_BACKJUMPS,id,1)) throw new IllegalStateException("LOOP_INCREMENT_REQUIRED");
        repo.jdbc().update("UPDATE execution_budget SET used_value=used_value+1 WHERE plan_id=? AND dimension='LOOP_BACKJUMPS' AND loop_id=?",plan,id);
        repo.jdbc().update("INSERT INTO execution_backjump VALUES(?,?,?,?)",transitionId,plan,id,System.currentTimeMillis());
    }
    static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
}
