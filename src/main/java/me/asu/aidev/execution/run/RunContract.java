package me.asu.aidev.execution.run;

import java.util.*;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Metadata channel only; none of these messages grant tool authority. */
public final class RunContract {
    private RunContract() {}
    public static final String PROTOCOL="aiw.worker.loop.v1";
    public record Open(String protocol,Lease lease,int instructionRevision,Map<String,String> capabilities,String configurationCapability) {
        public Open(String protocol,Lease lease,int instructionRevision,Map<String,String> capabilities) { this(protocol,lease,instructionRevision,capabilities,null); }
    }
    public record Event(long sequence,String eventType,long occurredAt,Map<String,Object> payload,String payloadDigest) {}
    public record Events(Lease lease,List<Event> events) {}
    public record Receipt(long sequence,String status) {}
    public record Poll(Lease lease,List<Receipt> receipts) {}
    public record Seal(Lease lease,long expectedRunRevision,long acknowledgedSequence,String batchId) {}
    public record Command(String requestId,String taskId,String attemptId,int instructionRevision,long expectedRunRevision,String action) {}
    public record Query(String taskId,String attemptId,long afterSequence,String planId) {
        public Query(String taskId,String attemptId,long afterSequence) { this(taskId,attemptId,afterSequence,null); }
    }
    public record ReceiptQuery(String requestId) {}

    public static void capabilities(Map<String,String> value) {
        var choices=Map.ofEntries(
            Map.entry("requestLoopOwner",Set.of("HOST","BACKEND")),Map.entry("toolExecutor",Set.of("HOST","BACKEND")),
            Map.entry("contextVisibility",Set.of("WORKER_PROJECTION","BACKEND_OPAQUE")),
            Map.entry("dispatchBoundary",Set.of("TOOL","TURN")),Map.entry("supplementBoundary",Set.of("REQUEST","NONE")),
            Map.entry("sessionReuse",Set.of("SAME_ATTEMPT","NONE")),Map.entry("pauseBoundary",Set.of("TOOL","TURN")),
            Map.entry("cancelEvidence",Set.of("BEST_EFFORT")),Map.entry("usageScope",Set.of("REQUEST","UNKNOWN")),
            Map.entry("requestAccountingScope",Set.of("REQUEST","BACKEND_TURN","UNKNOWN")));
        if(value==null||!value.keySet().equals(choices.keySet())||value.entrySet().stream().anyMatch(e->!choices.get(e.getKey()).contains(e.getValue())))
            throw new IllegalArgumentException("Unsupported run capability declaration");
    }
    public static void metadata(Map<String,Object> payload) {
        if(payload==null||payload.size()>16)throw new IllegalArgumentException("Bounded metadata required");
        var numbers=Set.of("bytes","inputBytes","outputBytes","count","durationMs","inputTokens","outputTokens","cachedTokens");
        var booleans=Set.of("complete","truncated","changed","usageKnown");
        var strings=Map.ofEntries(
            Map.entry("visibility",Set.of("WORKER_PROJECTION","BACKEND_OPAQUE")),
            Map.entry("requestScope",Set.of("REQUEST","BACKEND_TURN","UNKNOWN")),
            Map.entry("purpose",Set.of("task","host-action","history-summary")),
            Map.entry("status",Set.of("running","pending","authorized","serial","parallel","correctable","valid","succeeded","failed","unknown")),
            Map.entry("phase",Set.of("host-action","candidate")),
            Map.entry("tool",Set.of("MODEL","READ","SEARCH","WRITE","GIT","COMMAND","QUESTION")));
        for(var e:payload.entrySet()) {
            String key=e.getKey();Object value=e.getValue();
            if(numbers.contains(key)&&(value instanceof Integer||value instanceof Long)&&((Number)value).longValue()>=0&&((Number)value).longValue()<=Long.MAX_VALUE/2)continue;
            if(booleans.contains(key)&&value instanceof Boolean)continue;
            if(strings.containsKey(key)&&value instanceof String s&&strings.get(key).contains(s))continue;
            if(Set.of("digest","operation").contains(key)&&value instanceof String s&&s.matches(key.equals("digest")?"[0-9a-f]{64}":"OP-[0-9a-f]{32,64}"))continue;
            if(Set.of("callId","parentCallId").contains(key)&&value instanceof String s&&s.matches("context-[0-9]{1,10}"))continue;
            if(key.equals("sourceKind")&&value instanceof String s&&Set.of("instructions","messages","tools","fixed_header","observations","new_observations","task","knowledge","upstream","skill","configuration").contains(s))continue;
            if(key.equals("code")&&value instanceof String s&&s.matches("[A-Z][A-Z0-9_]{0,79}"))continue;
            throw new IllegalArgumentException("Unknown or unsafe run metadata field");
        }
    }
    public static void id(String value) { text(value,128); }
}
