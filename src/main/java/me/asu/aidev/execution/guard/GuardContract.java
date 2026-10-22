package me.asu.aidev.execution.guard;

import java.util.*;

/** Closed, versioned transport. A configuration describes needs; only a confirmed policy grants them. */
public final class GuardContract {
    public static final String PROTOCOL = "aiw.execution.guard.v1";
    public static final String CAPABILITY = "execution-guard-v1";
    private GuardContract() {}
    public enum Tool { READ, SEARCH, WRITE, GIT, COMMAND, MODEL, QUESTION }
    public enum Dimension { TIME_MS, TASKS, COST_MICROS, LOOP_BACKJUMPS }
    public record Rule(Tool tool, String repositoryRef, String pathPrefix) {
        public Rule { Objects.requireNonNull(tool); text(repositoryRef,120); path(pathPrefix); }
    }
    public record Command(String id, List<String> argv, String cwd, String permissionProfile,
                          long timeoutMs, int outputBytes, long memoryBytes, int processLimit) {
        public Command {
            text(id,80); text(permissionProfile,100); path(cwd);
            argv=List.copyOf(argv);
            if(argv.isEmpty() || argv.size()>64) throw new IllegalArgumentException("Bounded argv required");
            argv.forEach(value->text(value,4096));
            if(timeoutMs<1 || timeoutMs>3_600_000 || outputBytes<512 || outputBytes>1_048_576
                    || memoryBytes<16_777_216 || processLimit<1 || processLimit>512)
                throw new IllegalArgumentException("Explicit command resource bounds required");
        }
    }
    public record Price(String unit, String revision, long inputPerMillion, long cachedPerMillion, long outputPerMillion) {
        public Price { text(unit,32); text(revision,100); nonnegative(inputPerMillion);nonnegative(cachedPerMillion);nonnegative(outputPerMillion); }
    }
    public record Budget(Long timeMs, Long tasks, Long costMicros, long attemptTimeMs, long attemptCostMicros, Price price) {
        public Budget {
            if(timeMs!=null) nonnegative(timeMs); if(tasks!=null) nonnegative(tasks); if(costMicros!=null) nonnegative(costMicros);
            if(attemptTimeMs<1 || attemptCostMicros<1) throw new IllegalArgumentException("Approved per-attempt reservations required");
        }
        public boolean configured() { return timeMs!=null && tasks!=null && costMicros!=null && price!=null; }
    }
    public record Policy(String protocol, String ownerId, String planId, long revision, long expiresAt,
                         List<Rule> rules, List<Command> commands, Budget budget) {
        public Policy {
            GuardContract.protocol(protocol);text(ownerId,128);text(planId,128);
            if(revision<1 || expiresAt<1) throw new IllegalArgumentException("Policy revision/expiry required");
            rules=List.copyOf(rules);commands=List.copyOf(commands); Objects.requireNonNull(budget);
            if(rules.size()>32 || commands.size()>16 || commands.stream().map(Command::id).distinct().count()!=commands.size())
                throw new IllegalArgumentException("Bounded unique policy rules required");
        }
    }
    public record FrozenRef(String kind, String id, String revision, String sha256) {
        public FrozenRef { text(kind,32);text(id,512);text(revision,256);hash(sha256); }
    }
    public record Bind(String planId, long policyRevision, String sourceDecisionId, String configurationDigest,
                       List<FrozenRef> references) {
        public Bind { text(planId,128);text(sourceDecisionId,128);hash(configurationDigest); references=List.copyOf(references);
            if(policyRevision<1 || references.size()>128 || references.stream().map(r->r.kind()+":"+r.id()).distinct().count()!=references.size()) throw new IllegalArgumentException("Invalid or duplicate fixed context"); }
    }
    public record Lease(String workerId, String attemptId, String leaseToken) {
        public Lease { text(workerId,128);text(attemptId,128);text(leaseToken,128); }
    }
    public record Begin(String protocol, String platformInstanceId, Lease lease, String operationId, Tool tool, String repositoryRef,
                        String resource, String parameterDigest, long expectedGrantRevision) {
        public Begin { GuardContract.protocol(protocol);text(platformInstanceId,128);Objects.requireNonNull(lease);text(operationId,128); Objects.requireNonNull(tool);
            text(repositoryRef,120);path(resource);hash(parameterDigest);if(expectedGrantRevision<1) throw new IllegalArgumentException("Exact grant revision required"); }
    }
    public record Usage(long inputTokens, long cachedTokens, long outputTokens) {
        public Usage { nonnegative(inputTokens);nonnegative(cachedTokens);nonnegative(outputTokens);
            if(cachedTokens>inputTokens) throw new IllegalArgumentException("Cached usage exceeds input"); }
    }
    public record Finish(Lease lease, String operationId, String state, String resultDigest, String evidenceRef, Usage usage) {
        public Finish { Objects.requireNonNull(lease);text(operationId,128);hash(resultDigest);text(evidenceRef,1024);
            if(!Set.of("SUCCEEDED","FAILED","UNKNOWN").contains(state)) throw new IllegalArgumentException("Invalid actual outcome"); }
    }
    public record Increment(String decisionId, String planId, Dimension dimension, String loopId, long amount, long cumulativeCeiling) {
        public Increment { text(decisionId,128);text(planId,128);Objects.requireNonNull(dimension);
            if(dimension==Dimension.LOOP_BACKJUMPS) text(loopId,128); else if(loopId!=null) throw new IllegalArgumentException("Unexpected loopId");
            if(amount<1 || cumulativeCeiling<amount) throw new IllegalArgumentException("Explicit bounded increment required"); }
    }
    public record Reconcile(String operationId, String conclusion, String evidenceRef, String evidenceDigest) {
        public Reconcile { text(operationId,128);text(evidenceRef,1024);hash(evidenceDigest);
            if(!Set.of("SUCCEEDED","FAILED","NOT_STARTED").contains(conclusion)) throw new IllegalArgumentException("Explicit reconciliation conclusion required"); }
    }
    public record Yield(Lease lease, String operationId, FrozenRef checkpoint) {
        public Yield { Objects.requireNonNull(lease);text(operationId,128);Objects.requireNonNull(checkpoint); }
    }
    public record MeterReconcile(String attemptId, Long actualElapsedMs, Long actualCostMicros, String evidenceRef, String evidenceDigest) {
        public MeterReconcile { text(attemptId,128);text(evidenceRef,1024);hash(evidenceDigest);
            if(actualElapsedMs!=null) nonnegative(actualElapsedMs);if(actualCostMicros!=null) nonnegative(actualCostMicros);
            if(actualElapsedMs==null && actualCostMicros==null) throw new IllegalArgumentException("Actual measured consumption required"); }
    }
    public record StopEvidence(String attemptId, String evidenceRef, String evidenceDigest, boolean allProcessesQuiescent) {
        public StopEvidence { text(attemptId,128);text(evidenceRef,1024);hash(evidenceDigest);
            if(!allProcessesQuiescent) throw new IllegalArgumentException("Actual process quiescence must be confirmed"); }
    }
    public record Delivery(String scope, String baseline, String resultRevision, List<String> changes,
                           List<String> commandsRun, List<String> checksNotRun, List<String> unresolved, List<FrozenRef> sources) {
        public Delivery { text(scope,8192);changes=List.copyOf(changes);commandsRun=List.copyOf(commandsRun);
            checksNotRun=List.copyOf(checksNotRun);unresolved=List.copyOf(unresolved);sources=List.copyOf(sources); }
    }
    public static void text(String value,int max) {
        if(value==null || value.isBlank() || value.length()>max || value.indexOf('\0')>=0) throw new IllegalArgumentException("Missing or oversized field");
    }
    public static void hash(String value) { if(value==null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 required"); }
    public static void path(String value) {
        if(value==null || value.length()>1024 || value.indexOf('\\')>=0 || value.indexOf('\0')>=0 || value.startsWith("/")
                || value.contains(":")) throw new IllegalArgumentException("Relative resource required");
        if(!value.isEmpty() && !value.equals(".") && Arrays.stream(value.split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))
            throw new IllegalArgumentException("Canonical relative resource required");
    }
    private static void nonnegative(long n) { if(n<0) throw new IllegalArgumentException("Negative quantity"); }
    private static void protocol(String p) { if(!PROTOCOL.equals(p)) throw new IllegalArgumentException("Unsupported execution guard protocol"); }
}
