package me.asu.aidev.questions;

import java.util.*;
import me.asu.aidev.authority.ActionAdmissionService.Envelope;
import me.asu.aidev.execution.guard.GuardContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** One formal question across transports. Text never creates tool or budget authority. */
public final class QuestionContract {
    public static final String PROTOCOL="aiw.questions.v1";
    public static final String CAPABILITY="questions-v1";
    private QuestionContract() {}
    public enum SourceKind { LEGACY_PLAN_TASK, WORKFLOW_GRAPH_EXECUTION }
    public enum Kind { PROFESSIONAL, AUTHORIZATION }
    public enum State { OPEN, ANSWERED, APPLIED, SUPERSEDED }
    public record Source(SourceKind kind,String planId,long planVersion,String taskId,String executionId) {
        public Source { Objects.requireNonNull(kind);text(planId,128);if(planVersion<1) throw new IllegalArgumentException("Source version required");
            if(taskId!=null) text(taskId,128);if(executionId!=null) text(executionId,128); }
    }
    public record Checkpoint(String repositoryRef,String baseRevision,String resultRevision,List<String> changedFiles,String configurationDigest,String storageCapability) {
        public Checkpoint { text(repositoryRef,120);commit(baseRevision);commit(resultRevision);changedFiles=List.copyOf(changedFiles);
            if(changedFiles.size()>512) throw new IllegalArgumentException("Checkpoint file limit");changedFiles.forEach(me.asu.aidev.execution.guard.GuardContract::path);
            me.asu.aidev.execution.guard.GuardContract.hash(configurationDigest);
            if(storageCapability==null||!storageCapability.matches("checkpoint-store:[0-9a-f]{64}")) throw new IllegalArgumentException("Registered persistent Git store required"); }
    }
    public record Open(String protocol,String sourceQuestionKey,Source source,Lease lease,String question,String context,
                       List<String> options,String answerRequirements,Checkpoint checkpoint) {
        public Open { QuestionContract.protocol(protocol);text(sourceQuestionKey,128);Objects.requireNonNull(source);Objects.requireNonNull(lease);
            text(question,8000);context=context==null?"":context;answerRequirements=answerRequirements==null?"":answerRequirements;
            if(context.length()>8000||answerRequirements.length()>2000) throw new IllegalArgumentException("Question projection limit");
            options=options==null?List.of():List.copyOf(options);if(options.size()>12) throw new IllegalArgumentException("Option limit");options.forEach(s->text(s,1000)); }
    }
    public record Authorization(String requestId,Dimension dimension,String loopId,long amount,long cumulativeCeiling) {
        public Authorization { uuid(requestId);Objects.requireNonNull(dimension);new Increment(requestId,"validation",dimension,loopId,amount,cumulativeCeiling); }
        public Increment increment(String plan) { return new Increment(requestId,plan,dimension,loopId,amount,cumulativeCeiling); }
    }
    public record Question(String protocol,String questionId,String ownerId,Source source,String sourceAttemptId,String sourceQuestionKey,
                           Kind kind,String question,String context,List<String> options,String answerRequirements,
                           long requestVersion,State state,long answerRevision,Answer answer,Checkpoint checkpoint,
                           Authorization authorization,String successorTaskId,String blockedReason) {}
    public record Answer(String rawAnswer,String actor,String channel,String messageId,long revision,boolean delegated,String overrideReason) {}
    public record Reply(String questionId,long requestVersion,String rawAnswer,String messageId,Long expectedAnswerRevision,String overrideReason) {
        public Reply { text(questionId,128);text(rawAnswer,8000);text(messageId,128);if(requestVersion<1) throw new IllegalArgumentException("Request version required");
            if(overrideReason!=null) text(overrideReason,2000); }
    }
    public record Projection(Reply reply,String selectedGoal,String necessaryContext) {
        public Projection { Objects.requireNonNull(reply);selectedGoal=selectedGoal==null?"":selectedGoal;necessaryContext=necessaryContext==null?"":necessaryContext;
            if(selectedGoal.length()>8000||necessaryContext.length()>8000) throw new IllegalArgumentException("Projection limit"); }
    }
    public record PersonalReply(Envelope envelope,String projectionJson) { public PersonalReply { Objects.requireNonNull(envelope);text(projectionJson,28000); } }
    public record Binding(String userId,String privateChatId) { public Binding { positiveId(userId);positiveId(privateChatId); } }
    public record BudgetQuestion(String planId,long policyRevision,String question,Authorization authorization) {
        public BudgetQuestion { text(planId,128);text(question,8000);Objects.requireNonNull(authorization);if(policyRevision<1) throw new IllegalArgumentException("Policy revision required"); }
    }
    public record Application(String successorTaskId) {}
    public record QuestionReceipt(Lease lease,String sourceQuestionKey) {public QuestionReceipt {Objects.requireNonNull(lease);text(sourceQuestionKey,128);}}
    public interface SourceResolver {
        SourceKind kind();
        default Source sourceFor(String task,String owner) { return null; }
        /** Must validate the real source/version and caller; returns immutable semantic-source digest. */
        String validate(Source source,String owner,boolean opening);
        /** Called in the question APPLIED transaction. Return null when controls or prerequisites block. */
        Application apply(Question question);
    }
    private static void protocol(String value) { if(!PROTOCOL.equals(value)) throw new IllegalArgumentException("Unsupported question protocol"); }
    private static void commit(String value) { if(value==null||!value.matches("(?i)[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Immutable Git checkpoint required"); }
    public static void uuid(String value) { if(value==null||!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Canonical request UUID required"); }
    private static void positiveId(String value) { if(value==null||!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Exact private Telegram identity required"); }
}
