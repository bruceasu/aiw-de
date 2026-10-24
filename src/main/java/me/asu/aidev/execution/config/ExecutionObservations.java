package me.asu.aidev.execution.config;

import java.util.*;
import java.util.function.Function;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract;
import static me.asu.aidev.execution.config.ConfigurationContract.*;

/** A bounded read projection of existing facts; it creates no usage or acceptance authority. */
public final class ExecutionObservations {
    public static final String PROTOCOL="aiw.execution.observations.v1";
    public record Snapshot(String protocol,String ownerId,long observedAt,int limit,List<Entry> entries) {}
    public record Entry(String planId,String taskId,String attemptId,long attemptNo,String title,String taskState,
                        String attemptState,String methodName,String primarySkill,String profileId,String profileVersion,
                        String runtime,String model,Long startedAt,boolean settled,boolean usageUnknown,
                        Long inputTokens,Long cachedTokens,Long outputTokens,boolean timeUnknown,Long elapsedMs,
                        Long costMicros,String priceUnit,String reportedVerdict,String failureCategory,
                        String resultRevision,String publicationRef,PreparedConfigRef configuration,
                        String deliveryDigest,int sourceCount,List<GuardContract.FrozenRef> sources,
                        Integer checksNotRunCount,Integer unresolvedCount,long repeatedReads) {}
    private final AuthorityRepository repo;
    public ExecutionObservations(AuthorityRepository repo) { this.repo=repo; }

    public Snapshot read(String owner,Function<Binding,String> methodName) {
        var rows=repo.jdbc().queryForList("""
                SELECT m.*,t.instruction,t.skill_ref,t.state AS task_state,
                       a.attempt_no,a.state AS attempt_state,a.ai_profile_id,a.ai_profile_version,
                       a.ai_runtime,a.ai_model,a.completion_verdict,a.failure_category,
                       a.result_revision,a.publication_ref,c.binding_json,d.digest AS delivery_digest,d.delivery_json
                  FROM execution_meter m
                  JOIN execution_policy p ON p.plan_id=m.plan_id
                  JOIN work_task t ON t.id=m.task_id
                  JOIN execution_attempt a ON a.id=m.attempt_id AND a.task_id=m.task_id
                  LEFT JOIN execution_task_configuration c ON c.task_id=m.task_id
                  LEFT JOIN execution_delivery d ON d.attempt_id=m.attempt_id AND d.task_id=m.task_id
                 WHERE p.owner_id=?
                 ORDER BY a.created_at_epoch_ms DESC,m.attempt_id DESC LIMIT 100
                """,owner);
        var repeated=repeatedReads(rows);
        var labels=new HashMap<String,String>();var entries=new ArrayList<Entry>();
        for(var row:rows) {
            Binding binding=row.get("binding_json")==null?null:repo.decode((String)row.get("binding_json"),Binding.class);
            String label=null;
            if(binding!=null) {
                if(!owner.equals(binding.configOwner())) throw new SecurityException("Observation configuration belongs to another owner");
                String key=binding.ref().id()+":"+binding.ref().revision()+":"+binding.ref().digest();
                label=labels.computeIfAbsent(key,ignored->methodName.apply(binding));
            }
            String deliveryJson=string(row,"delivery_json"),digest=string(row,"delivery_digest");
            GuardContract.Delivery delivery=null;
            if(deliveryJson!=null) {
                if(!AuthorityRepository.hash(deliveryJson).equals(digest)) throw new IllegalStateException("Recorded delivery corrupted");
                delivery=repo.decode(deliveryJson,GuardContract.Delivery.class);
            }
            var price=repo.decode((String)row.get("price_json"),GuardContract.Price.class);
            Long started=number(row,"started_ms");boolean unknown=AuthorityRepository.flag(row.get("usage_unknown"));
            boolean measured=started!=null&&!unknown;
            String task=string(row,"task_id"),attempt=string(row,"attempt_id");
            entries.add(new Entry(string(row,"plan_id"),task,attempt,number(row,"attempt_no"),title(string(row,"instruction"),task),
                    string(row,"task_state"),string(row,"attempt_state"),label,string(row,"skill_ref"),
                    string(row,"ai_profile_id"),string(row,"ai_profile_version"),string(row,"ai_runtime"),string(row,"ai_model"),
                    started,AuthorityRepository.flag(row.get("settled")),unknown,
                    measured?number(row,"input_tokens"):null,measured?number(row,"cached_tokens"):null,measured?number(row,"output_tokens"):null,
                    AuthorityRepository.flag(row.get("time_unknown")),number(row,"elapsed_ms"),number(row,"cost_micros"),price==null?null:price.unit(),
                    string(row,"completion_verdict"),string(row,"failure_category"),string(row,"result_revision"),string(row,"publication_ref"),
                    binding==null?null:binding.ref(),digest,delivery==null?0:delivery.sources().size(),
                    delivery==null?List.of():delivery.sources().stream().limit(16).toList(),
                    delivery==null?null:delivery.checksNotRun().size(),delivery==null?null:delivery.unresolved().size(),repeated.getOrDefault(attempt,0L)));
        }
        return new Snapshot(PROTOCOL,owner,System.currentTimeMillis(),100,List.copyOf(entries));
    }

    private Map<String,Long> repeatedReads(List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return Map.of();
        String placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
        var counts=repo.jdbc().queryForList("""
                SELECT attempt_id,SUM(copies-1) AS repeated_reads FROM (
                    SELECT attempt_id,resource,result_digest,COUNT(*) AS copies FROM execution_operation
                     WHERE tool_id='READ' AND state='SUCCEEDED' AND result_digest IS NOT NULL
                       AND attempt_id IN (%s)
                     GROUP BY attempt_id,resource,result_digest HAVING COUNT(*)>1
                ) repeated GROUP BY attempt_id
                """.formatted(placeholders),rows.stream().map(r->r.get("attempt_id")).toArray());
        var result=new HashMap<String,Long>();counts.forEach(r->result.put(string(r,"attempt_id"),number(r,"repeated_reads")));return result;
    }
    private static String string(Map<String,Object> row,String key) { Object value=row.get(key);return value==null?null:value.toString(); }
    private static Long number(Map<String,Object> row,String key) { Object value=row.get(key);return value==null?null:((Number)value).longValue(); }
    private static String title(String instruction,String fallback) {
        if(instruction==null||instruction.isBlank()) return fallback;
        String first=instruction.strip().split("\\R",2)[0];return first.length()>100?first.substring(0,100)+"…":first;
    }
}
