package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Repository;

@Repository
public class GraphStore {
    public record Plan(String id,String owner,long version,String state,long revision,GraphState runtime) {}
    private final AuthorityRepository repo;
    public GraphStore(AuthorityRepository repo){this.repo=repo;}
    public Plan get(String id){var row=repo.jdbc().queryForMap("SELECT * FROM workflow_graph WHERE id=?",id);return new Plan(id,(String)row.get("owner_id"),number(row,"edit_version"),(String)row.get("state"),number(row,"runtime_revision"),repo.decode((String)row.get("runtime_json"),GraphState.class));}
    public GraphContract.Spec spec(String plan,long version){var row=repo.jdbc().queryForMap("SELECT spec_json,digest FROM workflow_graph_version WHERE plan_id=? AND version=?",plan,version);String json=(String)row.get("spec_json");if(!AuthorityRepository.hash(json).equals(row.get("digest")))throw new IllegalStateException("GRAPH_DEFINITION_CORRUPTED");return repo.decode(json,GraphContract.Spec.class);}
    public void save(Plan p,String state){if(repo.jdbc().update("UPDATE workflow_graph SET state=?,runtime_revision=runtime_revision+1,runtime_json=? WHERE id=? AND runtime_revision=?",state,repo.encode(p.runtime()),p.id(),p.revision())!=1)throw new IllegalStateException("GRAPH_RUNTIME_CHANGED");}
    /** Publish to other consumers in this same transaction; the final save owns the revision CAS. */
    public void stage(Plan p){if(repo.jdbc().update("UPDATE workflow_graph SET runtime_json=? WHERE id=? AND runtime_revision=?",repo.encode(p.runtime()),p.id(),p.revision())!=1)throw new IllegalStateException("GRAPH_RUNTIME_CHANGED");}
    public void event(String plan,String id,String execution,String kind,Object payload){String json=repo.encode(payload);var old=repo.jdbc().queryForList("SELECT kind,payload_json FROM workflow_graph_event WHERE plan_id=? AND event_id=?",plan,id);
        if(!old.isEmpty()){if(!Objects.equals(old.getFirst().get("kind"),kind)||!json.equals(old.getFirst().get("payload_json")))throw new IllegalStateException("GRAPH_EVENT_CONFLICT");return;}
        repo.jdbc().update("INSERT INTO workflow_graph_event VALUES(?,?,?,?,?,?)",plan,id,execution,kind,json,System.currentTimeMillis());}
    public static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
}
