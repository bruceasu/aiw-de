package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.execution.config.ConfigurationContract.Intent;
import me.asu.aidev.execution.domain.TaskInput;

/** Persisted aggregate. Old iterations and physical facts remain in the aggregate/event journal. */
public final class GraphState {
    public String rootActivation;
    public List<Activation> activations=new ArrayList<>();
    public List<Execution> replaced=new ArrayList<>();
    public static final class Activation {
        public String id,region,parent,state="ACTIVE",interfaceDigest;
        public long definitionVersion;
        public int parentIteration,iteration;
        public Map<String,String> importBindings=new LinkedHashMap<>();
        public Map<String,Fact> imports=new LinkedHashMap<>();
        public Map<String,Long> childVersions=new LinkedHashMap<>();
        public List<Round> rounds=new ArrayList<>();
        public List<Fact> exports=new ArrayList<>();
        public String exitDigest;
    }
    public static final class Round {
        public int number;
        public String state="ACTIVE";
        public Map<String,Execution> nodes=new LinkedHashMap<>();
        public Map<String,String> children=new LinkedHashMap<>();
        public List<Edge> edges=new ArrayList<>();
        public Map<String,Fact> carry=new LinkedHashMap<>();
    }
    public static final class Execution {
        public String id,node,state="WAITING_SELECTION",taskId,questionId,reason,replacementFor;
        public long version;
        public Intent intent;
        public long preparationGeneration;
        public Fact fact;
        public boolean emitted,failureConsumed,cancelRequested;
        public GraphContract.Task replacement;
        public List<Fact> inputs=new ArrayList<>();
    }
    public static final class Edge {
        public String source,target,kind,state="PENDING",evidence;
        public Edge() {}
        public Edge(String source,String target,String kind){this.source=source;this.target=target;this.kind=kind;}
    }
    public record Failure(String taskId,String attemptId,String state,String category,String code,String evidenceDigest) {}
    public record Fact(String executionId,String kind,String digest,TaskInput input,Failure failure,String answer) {}
    public Activation activation(String id){return activations.stream().filter(a->a.id.equals(id)).findFirst().orElseThrow();}
    public Round round(Activation a){return a.rounds.stream().filter(r->r.number==a.iteration).findFirst().orElseThrow();}
    public Execution execution(String id){return activations.stream().flatMap(a->a.rounds.stream()).flatMap(r->r.nodes.values().stream()).filter(e->e.id.equals(id)).findFirst()
        .orElseGet(()->replaced.stream().filter(e->e.id.equals(id)).findFirst().orElseThrow());}
    public Activation containing(String execution){return activations.stream().filter(a->a.rounds.stream().anyMatch(r->r.nodes.values().stream().anyMatch(e->e.id.equals(execution)))).findFirst().orElseThrow();}
}
