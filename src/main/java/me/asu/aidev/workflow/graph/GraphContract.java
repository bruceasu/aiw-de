package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.execution.config.ConfigurationContract;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Declarative data only. No script, shell or model supplied expression is evaluated. */
public final class GraphContract {
    public static final String PROTOCOL="aiw.workflow.graph.v1";
    private GraphContract() {}
    public enum Kind { TASK, IF, GOTO, ASK, END }
    public enum Field { VERDICT, RESULT_REVISION, CHANGED_FILE_COUNT, SUCCESS }
    public enum Operator { EQ, NE, GT, GE, LT, LE }
    public enum Conclusion { SUCCESS, FAILURE }
    public record Spec(String protocol,String goal,String scope,String acceptance,List<Region> regions,List<Node> nodes) {
        public Spec { if(!PROTOCOL.equals(protocol)) throw new IllegalArgumentException("Unsupported graph protocol");
            text(goal,8000);text(scope,8000);text(acceptance,8000);regions=List.copyOf(regions);nodes=List.copyOf(nodes); }
    }
    public record Region(String key,String parent,List<String> nodeKeys,List<String> entryKeys,String joinKey,
                         String loopId,String loopHeader,List<String> imports,List<Carry> carry) {
        public Region { id(key);if(parent!=null) id(parent);nodeKeys=List.copyOf(nodeKeys);entryKeys=List.copyOf(entryKeys);
            imports=imports==null?List.of():List.copyOf(imports);carry=carry==null?List.of():List.copyOf(carry);
            if(joinKey!=null) id(joinKey);if(loopId!=null) id(loopId);if(loopHeader!=null) id(loopHeader); }
    }
    public record Carry(String sourceNode,String targetNode,boolean asBase) { public Carry {id(sourceNode);id(targetNode);} }
    public record Task(String repositoryRef,String baseRevision,String baseFrom,String runtime,String aiProfileId,
                       String defaultRole,ConfigurationContract.PreparedConfigRef configRef,
                       ConfigurationContract.Candidate candidate,String instruction,List<String> failureNext) {
        public Task {text(repositoryRef,120);text(instruction,12000);ConfigurationContract.id(aiProfileId);
            if(baseRevision==null||!baseRevision.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Immutable initial base required");
            if(baseFrom!=null) id(baseFrom);if(!Set.of("openai-agents","codex-app-server").contains(runtime)) throw new IllegalArgumentException("Unsupported runtime");
            if((defaultRole==null?0:1)+(configRef==null?0:1)+(candidate==null?0:1)!=1) throw new IllegalArgumentException("Choose one configuration source");
            failureNext=failureNext==null?List.of():List.copyOf(failureNext); }
    }
    public record Predicate(String sourceNode,Field field,Operator operator,String value) {
        public Predicate {id(sourceNode);Objects.requireNonNull(field);Objects.requireNonNull(operator);text(value,256);}
    }
    public record Branch(Predicate predicate,String trueRegion,String falseRegion) {
        public Branch {Objects.requireNonNull(predicate);id(trueRegion);id(falseRegion);if(trueRegion.equals(falseRegion)) throw new IllegalArgumentException("Distinct branches required");}
    }
    public record Jump(String target,String loopId) {public Jump {id(target);if(loopId!=null) id(loopId);} }
    public record Ask(String question,String context,List<String> options,String answerRequirements) {
        public Ask {text(question,8000);context=context==null?"":context;answerRequirements=answerRequirements==null?"":answerRequirements;
            if(context.length()>8000||answerRequirements.length()>2000||context.indexOf('\0')>=0||answerRequirements.indexOf('\0')>=0) throw new IllegalArgumentException("Question projection limit");
            options=options==null?List.of():List.copyOf(options);if(options.size()>12) throw new IllegalArgumentException("Question option limit");options.forEach(v->text(v,1000));}
    }
    public record Node(String key,String region,Kind kind,List<String> dependsOn,List<String> next,
                       Task task,Branch branch,Jump jump,Ask ask,Conclusion end) {
        public Node {id(key);id(region);Objects.requireNonNull(kind);dependsOn=dependsOn==null?List.of():List.copyOf(dependsOn);next=next==null?List.of():List.copyOf(next);
            if((task==null?0:1)+(branch==null?0:1)+(jump==null?0:1)+(ask==null?0:1)+(end==null?0:1)!=1
                ||switch(kind){case TASK->task==null;case IF->branch==null;case GOTO->jump==null;case ASK->ask==null;case END->end==null;}) throw new IllegalArgumentException("Kind must match exactly one payload"); }
    }
    public record Change(String requestId,String ownerId,String planId,long expectedVersion,String source,String reason,String region,Spec spec) {
        public Change {uuid(requestId);text(ownerId,128);id(planId);text(source,128);text(reason,2000);Objects.requireNonNull(spec);if(expectedVersion<0) throw new IllegalArgumentException("Expected version required");}
    }
    public record Decision(String requestId,String planId,long expectedVersion,String executionId,long expectedExecutionVersion,
                           Long expectedTaskVersion,String expectedAttemptId,String action,String reason,Task replacement) {
        public Decision {uuid(requestId);id(planId);text(action,32);text(reason,2000);if(expectedVersion<1) throw new IllegalArgumentException("Expected version required");}
    }
    public record Recommendation(String requestId,String planId,long expectedVersion,Task planner) {public Recommendation {uuid(requestId);id(planId);Objects.requireNonNull(planner);} }
    public static void id(String value) {ConfigurationContract.id(value);}
    public static void uuid(String value) {me.asu.aidev.questions.QuestionContract.uuid(value);}
}
