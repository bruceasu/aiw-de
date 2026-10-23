package me.asu.aidev.workflow.graph;

import java.util.*;
import org.springframework.stereotype.Component;
import static me.asu.aidev.workflow.graph.GraphContract.*;

/** Structural validation independent of SQL and execution timing. */
@Component
public class GraphValidator {
    public record Diagnostic(String node,String code,String detail) {}
    public List<Diagnostic> diagnostics(Spec spec) {
        var errors=new ArrayList<Diagnostic>();
        try { validate(spec); } catch(IllegalArgumentException e) {errors.add(new Diagnostic(null,"INVALID_GRAPH",e.getMessage()));}
        return List.copyOf(errors);
    }
    public void validate(Spec spec) {
        var nodes=nodes(spec);var regions=regions(spec);
        require(regions.containsKey("ROOT")&&regions.get("ROOT").parent()==null,"ROOT: unique root required");
        for(var r:regions.values()) {
            if(!r.key().equals("ROOT")) require(r.parent()!=null&&regions.containsKey(r.parent()),r.key()+": parent required");
            var ancestors=new HashSet<String>();String p=r.key();while(p!=null){require(ancestors.add(p),r.key()+": region cycle");p=regions.get(p).parent();}
            require(!r.nodeKeys().isEmpty()&&!r.entryKeys().isEmpty(),r.key()+": explicit nodes and entries required");unique(r.nodeKeys(),r.key());unique(r.entryKeys(),r.key());unique(r.imports(),r.key());
            for(String k:r.nodeKeys()) require(nodes.containsKey(k)&&nodes.get(k).region().equals(r.key()),k+": wrong node ownership");
            if(r.parent()==null) require(r.joinKey()==null,"ROOT: no external join");
            else require(nodes.containsKey(r.joinKey())&&nodes.get(r.joinKey()).region().equals(r.parent()),r.key()+": join must be in direct parent");
            require((r.loopId()==null)==(r.loopHeader()==null),r.key()+": loop ID/header pair required");
            if(r.loopId()!=null) require(r.nodeKeys().contains(r.loopHeader())&&r.entryKeys().equals(List.of(r.loopHeader())),r.key()+": loop has one declared header entry");
            for(String k:r.imports()) require(nodes.containsKey(k)&&ancestor(regions,nodes.get(k).region(),r.key())&&!nodes.get(k).region().equals(r.key()),k+": import must name ancestor node");
            var carryTargets=new HashSet<String>();for(var c:r.carry()) {
                require(r.loopId()!=null&&r.nodeKeys().contains(c.sourceNode())&&r.nodeKeys().contains(c.targetNode()),r.key()+": carry stays in loop");
                require(nodes.get(c.sourceNode()).kind()==Kind.TASK&&nodes.get(c.targetNode()).kind()==Kind.TASK&&carryTargets.add(c.targetNode()),r.key()+": one carry source per task");
                require(nodes.get(c.sourceNode()).task().repositoryRef().equals(nodes.get(c.targetNode()).task().repositoryRef()),r.key()+": carry repository mismatch");
            }
        }
        require(regions.values().stream().filter(r->r.parent()==null).count()==1,"Unique root required");
        var loopIds=new HashSet<String>();for(var r:regions.values()) if(r.loopId()!=null) require(loopIds.add(r.loopId()),r.key()+": loopId must identify one region");
        var branchOwners=new HashMap<String,String>();
        for(var n:nodes.values()) {
            require(regions.containsKey(n.region())&&regions.get(n.region()).nodeKeys().contains(n.key()),n.key()+": undeclared membership");unique(n.next(),n.key());unique(n.dependsOn(),n.key());
            for(String d:n.dependsOn()) require(nodes.containsKey(d)&&!d.equals(n.key())&&(nodes.get(d).region().equals(n.region())||regions.get(n.region()).imports().contains(d)),n.key()+": dependency must be local or explicit ancestor import");
            if(n.kind()==Kind.TASK&&n.task().baseFrom()!=null) require(n.dependsOn().contains(n.task().baseFrom())&&nodes.get(n.task().baseFrom()).kind()==Kind.TASK,n.key()+": baseFrom requires successful task dependency");
            for(String to:n.next()) local(nodes,n,to);
            if(n.kind()==Kind.TASK){unique(n.task().failureNext(),n.key());for(String to:n.task().failureNext()){local(nodes,n,to);require(!n.next().contains(to),n.key()+": failure and success edges must differ");}}
            if(n.kind()==Kind.IF) {
                require(n.next().isEmpty(),n.key()+": IF exits through child joins");var b=n.branch();
                require(n.dependsOn().contains(b.predicate().sourceNode()),n.key()+": predicate source must be a fixed dependency");
                require(nodes.get(b.predicate().sourceNode()).kind()==Kind.TASK,n.key()+": predicate needs formal task result");
                for(String child:List.of(b.trueRegion(),b.falseRegion())) {require(regions.containsKey(child)&&n.region().equals(regions.get(child).parent()),n.key()+": branch must be direct child");require(branchOwners.put(child,n.key())==null,child+": one branch owner");}
                require(regions.get(b.trueRegion()).joinKey().equals(regions.get(b.falseRegion()).joinKey()),n.key()+": branches must share parent join");predicate(b.predicate());
            }
            if(n.kind()==Kind.GOTO) {
                require(n.next().isEmpty(),n.key()+": goto has no implicit next");var j=n.jump();require(nodes.containsKey(j.target()),n.key()+": missing goto target");
                if(j.loopId()==null) local(nodes,n,j.target());
                else require(regions.values().stream().anyMatch(r->j.loopId().equals(r.loopId())&&j.target().equals(r.loopHeader())&&ancestor(regions,r.key(),n.region())),n.key()+": backward goto must target declared ancestor loop header");
            }
            if(n.kind()==Kind.END) require(n.next().isEmpty(),n.key()+": end closes this path");
        }
        for(var r:regions.values()) {
            if(r.parent()!=null){var parent=regions.get(r.parent());boolean entry=parent.entryKeys().containsAll(r.entryKeys());require(entry||branchOwners.containsKey(r.key()),r.key()+": child has no selection source");}
            for(String entry:r.entryKeys()) {
                require(nodes.containsKey(entry),r.key()+": unknown entry");String own=nodes.get(entry).region();
                require(own.equals(r.key())||r.key().equals(regions.get(own).parent())&&!branchOwners.containsKey(own),entry+": entry cannot enter conditional/deep/sibling region");
                if(!own.equals(r.key())) require(r.entryKeys().containsAll(regions.get(own).entryKeys())&&regions.get(own).entryKeys().contains(entry),entry+": complete child entry set required");
            }
            var edges=potential(spec,r.key());var combined=new LinkedHashMap<String,List<String>>();edges.forEach((k,v)->combined.put(k,new ArrayList<>(v)));
            for(String k:r.nodeKeys())for(String d:nodes.get(k).dependsOn())if(r.nodeKeys().contains(d))combined.get(d).add(k);
            var color=new HashMap<String,Integer>();for(String k:r.nodeKeys()) visit(k,combined,color);
            var reachable=new HashSet<String>();var queue=new ArrayDeque<String>();
            for(String entry:r.entryKeys()) {String own=nodes.get(entry).region();queue.add(own.equals(r.key())?entry:regions.get(own).joinKey());}
            while(!queue.isEmpty()){String k=queue.remove();if(reachable.add(k)) queue.addAll(edges.getOrDefault(k,List.of()));}
            require(reachable.containsAll(r.nodeKeys()),r.key()+": unreachable local node");
        }
    }
    public Map<String,Node> nodes(Spec s){var m=new LinkedHashMap<String,Node>();for(var n:s.nodes()) require(m.put(n.key(),n)==null,"Duplicate node "+n.key());return m;}
    public Map<String,Region> regions(Spec s){var m=new LinkedHashMap<String,Region>();for(var r:s.regions()) require(m.put(r.key(),r)==null,"Duplicate region "+r.key());return m;}
    /** Child-return edges are represented from the controlling IF or a synthetic entry. */
    public Map<String,List<String>> potential(Spec s,String region) {
        var r=regions(s);var result=new LinkedHashMap<String,List<String>>();
        for(var n:s.nodes()) if(n.region().equals(region)) {
            var to=new ArrayList<>(n.next());if(n.task()!=null) to.addAll(n.task().failureNext());
            if(n.branch()!=null) to.add(r.get(n.branch().trueRegion()).joinKey());
            if(n.jump()!=null&&n.jump().loopId()==null) to.add(n.jump().target());result.put(n.key(),List.copyOf(to));
        }return result;
    }
    public static boolean ancestor(Map<String,Region> rs,String ancestor,String child){for(String p=child;p!=null;p=rs.get(p).parent()) if(p.equals(ancestor)) return true;return false;}
    private void predicate(Predicate p){
        if(p.field()==Field.CHANGED_FILE_COUNT){try{require(Long.parseLong(p.value())>=0,"Nonnegative file count required");}catch(NumberFormatException e){throw new IllegalArgumentException("Numeric file count required");}}
        else {require(Set.of(Operator.EQ,Operator.NE).contains(p.operator()),"Only equality for string/boolean fields");
            if(p.field()==Field.SUCCESS) require(Set.of("true","false").contains(p.value()),"Boolean predicate required");
            if(p.field()==Field.VERDICT) require(Set.of("PASS","REPAIR_REQUIRED").contains(p.value()),"Formal verdict required");}
    }
    private static void local(Map<String,Node> nodes,Node n,String to){require(nodes.containsKey(to)&&nodes.get(to).region().equals(n.region())&&!to.equals(n.key()),n.key()+": edge must stay in region");}
    private static void unique(List<String> values,String key){require(new HashSet<>(values).size()==values.size(),key+": duplicate reference");}
    private static void visit(String k,Map<String,List<String>> edges,Map<String,Integer> colors){if(colors.getOrDefault(k,0)==2)return;require(colors.getOrDefault(k,0)!=1,k+": ordinary control cycle");colors.put(k,1);for(String t:edges.getOrDefault(k,List.of()))visit(t,edges,colors);colors.put(k,2);}
    private static void require(boolean value,String why){if(!value)throw new IllegalArgumentException(why);}
}
