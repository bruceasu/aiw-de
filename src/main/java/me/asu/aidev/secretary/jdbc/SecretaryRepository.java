package me.asu.aidev.secretary.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.secretary.domain.SecretaryData.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecretaryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public SecretaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** Held to transaction commit. All secretary writers use this single-machine lock first. */
    public void lock() { jdbc.update("UPDATE secretary_lock SET version=version WHERE id=1"); }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode secretary data", e); }
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Content decode(String value) {
        try { return json.readValue(value, Content.class); }
        catch (Exception e) { throw new IllegalStateException("Cannot read secretary data", e); }
    }
    private org.springframework.jdbc.core.RowMapper<Work> mapper() {
        return (rs,n) -> {
            Number due=(Number)rs.getObject("due_ms");
            return new Work(rs.getString("id"),rs.getString("actor"),rs.getLong("revision"),rs.getLong("generation"),
                State.valueOf(rs.getString("state")),rs.getString("plan_id"),due==null?null:due.longValue(),decode(rs.getString("payload")));
        };
    }
    public Optional<Work> find(String id) { return jdbc.query("SELECT * FROM secretary_work WHERE id=?",mapper(),id).stream().findFirst(); }
    public Optional<Work> plan(String id) { return jdbc.query("SELECT * FROM secretary_work WHERE plan_id=?",mapper(),id).stream().findFirst(); }
    public Work owned(String id, String actor) {
        Work w = find(id).orElseThrow(() -> new IllegalArgumentException("Secretary work not found"));
        if (!w.actor().equals(actor)) throw new SecurityException("Secretary work belongs to another operator");
        return w;
    }
    public List<Work> list(String actor) { return jdbc.query("SELECT * FROM secretary_work WHERE actor=? ORDER BY updated_ms DESC,id LIMIT 100",mapper(),actor); }
    public List<String> due(long now) { return jdbc.query("SELECT id FROM secretary_work WHERE state='SCHEDULED' AND due_ms<=? ORDER BY due_ms,id LIMIT 100",(rs,n)->rs.getString(1),now); }
    public Work create(String actor) {
        String id = "SW-"+UUID.randomUUID();
        Content c = new Content("New work","","REMINDER","Asia/Tokyo","",-1,"","","");
        jdbc.update("INSERT INTO secretary_work VALUES (?,?,0,0,'DRAFT',NULL,NULL,?,?)",id,actor,encode(c),Instant.now().toEpochMilli());
        return find(id).orElseThrow();
    }
    public Work save(Work old, String planId, State state, Long due, Content content, boolean invalidate) {
        if (jdbc.update("UPDATE secretary_work SET revision=revision+1,generation=generation+?,state=?,plan_id=?,due_ms=?,payload=?,updated_ms=? WHERE id=? AND revision=?",
            invalidate?1:0,state.name(),planId,due,encode(content),Instant.now().toEpochMilli(),old.id(),old.revision()) != 1)
            throw new IllegalStateException("Secretary work changed; reload");
        return find(old.id()).orElseThrow();
    }
    public Work state(Work old, State state, String detail) {
        Content c=old.content();
        return save(old,old.planId(),state,old.dueMs(),new Content(c.title(),c.body(),c.kind(),c.zone(),c.snapshot(),c.planVersion(),c.planHash(),c.authority(),detail),false);
    }
    public void lockPlan(String id) { jdbc.update("UPDATE flexible_workflow_plan SET version=version WHERE id=?",id); }
    public void lockDraftPlan(String id) { jdbc.update("UPDATE flexible_workflow_plan SET version=version WHERE id=? AND state='DRAFT'",id); }
    public String planHash(String id) {
        return jdbc.query("SELECT spec_json FROM flexible_workflow_plan WHERE id=?",(rs,n)->hash(rs.getString(1)),id).stream().findFirst().orElse("");
    }
    public boolean current(Work w) {
        if(w.planId()==null) return true;
        var matches=jdbc.query("SELECT version,state,spec_json FROM flexible_workflow_plan WHERE id=?",(rs,n)->
            rs.getLong(1)==w.content().planVersion() && hash(rs.getString(3)).equals(w.content().planHash())
                && rs.getString(2).equals(w.state()==State.QUEUED?"DRAFT":"CONFIRMED"),w.planId());
        return !matches.isEmpty() && matches.getFirst() && !planBlocked(w.planId());
    }
    public boolean planBlocked(String id) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM flexible_workflow_plan p WHERE p.id=? AND (EXISTS(SELECT 1 FROM web_console_plan_archive a WHERE a.plan_id=p.id) OR EXISTS(SELECT 1 FROM flexible_workflow_control c WHERE c.plan_id=p.id AND c.status='PENDING_STOP') OR EXISTS(SELECT 1 FROM flexible_workflow_human_outcome h WHERE h.plan_id=p.id))",Integer.class,id);
        return n!=null && n>0;
    }
    public Optional<Request> request(String id) {
        return jdbc.query("SELECT * FROM secretary_request WHERE id=?",(rs,n)->new Request(rs.getString("id"),rs.getString("work_id"),rs.getLong("generation"),rs.getString("phase"),rs.getLong("plan_version"),rs.getString("source_attempt"),rs.getBoolean("consumed")),id).stream().findFirst();
    }
    public Optional<Request> phase(Work w,String phase) {
        return jdbc.query("SELECT id FROM secretary_request WHERE work_id=? AND generation=? AND phase=?",(rs,n)->rs.getString(1),w.id(),w.generation(),phase).stream().findFirst().flatMap(this::request);
    }
    public void insert(Request r) { jdbc.update("INSERT INTO secretary_request VALUES (?,?,?,?,?,?,FALSE)",r.id(),r.workId(),r.generation(),r.phase(),r.planVersion(),r.sourceAttempt()); }
    public boolean consume(String id) { return jdbc.update("UPDATE secretary_request SET consumed=TRUE WHERE id=? AND consumed=FALSE",id)==1; }
    public boolean participant(Work w,String worker,String role,boolean reserve) {
        var roles=jdbc.query("SELECT role FROM secretary_participant WHERE work_id=? AND worker_id=?",(rs,n)->rs.getString(1),w.id(),worker);
        if (!roles.isEmpty()) return roles.getFirst().equals(role);
        if (reserve) jdbc.update("INSERT INTO secretary_participant VALUES (?,?,?)",w.id(),worker,role);
        return true;
    }
    public boolean interrupted(String task) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM work_task WHERE id=? AND attempt_no>0 AND state='READY'",Integer.class,task);
        return n!=null && n>0;
    }
    public void admit(String task,Work w) {
        jdbc.update("INSERT INTO secretary_admitted_task VALUES (?,?,?) ON CONFLICT(task_id) DO NOTHING",task,w.id(),w.generation());
    }
    public boolean admitted(String task,Work w) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM secretary_admitted_task WHERE task_id=? AND work_id=? AND generation=?",Integer.class,task,w.id(),w.generation());
        return n!=null && n==1;
    }
    public List<Message> messages(String id) { return jdbc.query("SELECT id,user_text,reply,source_revision FROM secretary_message WHERE work_id=? ORDER BY created_ms,id",(rs,n)->new Message(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4)),id); }
    public Optional<String> messageHash(String id,String work,String actor) { return jdbc.query("SELECT input_hash FROM secretary_message WHERE id=? AND work_id=? AND actor=?",(rs,n)->rs.getString(1),id,work,actor).stream().findFirst(); }
    public void message(String id,Work w,String hash,String text) { jdbc.update("INSERT INTO secretary_message VALUES (?,?,?,?,?,NULL,?,?)",id,w.id(),w.actor(),hash,text,Instant.now().toEpochMilli(),w.revision()); }
    public void reply(String id,String text) { jdbc.update("UPDATE secretary_message SET reply=? WHERE id=? AND reply IS NULL",text,id); }
}
