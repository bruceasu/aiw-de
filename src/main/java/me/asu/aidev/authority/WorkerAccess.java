package me.asu.aidev.authority;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Worker credentials identify one execution process, never a console administrator. */
@Component
public class WorkerAccess {
    private final JdbcTemplate jdbc;
    private final Map<String,String> local=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    public WorkerAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Create(String requestId,String workerId,String label) {}
    public record Credential(String id,String workerId,String label,String state,long revision,long createdAt,Long lastUsedAt) {}
    public record Created(Credential credential,String token) { @Override public String toString(){return "Created[credential="+credential+", token=REDACTED]";} }
    public record Change(long revision,String state) {}
    private String secret(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    public String localToken(String worker){
        if(worker==null||!worker.matches("factory-[0-9a-f]{24}"))throw new IllegalArgumentException("Factory Worker identity required");
        return local.computeIfAbsent(worker,id->"local."+id+"."+secret());
    }
    private Credential value(Map<String,Object> row){return new Credential((String)row.get("id"),(String)row.get("worker_id"),(String)row.get("label"),(String)row.get("state"),((Number)row.get("revision")).longValue(),((Number)row.get("created_ms")).longValue(),row.get("last_used_ms")==null?null:((Number)row.get("last_used_ms")).longValue());}
    public List<Credential> list(){return jdbc.queryForList("SELECT * FROM worker_access_credential ORDER BY created_ms DESC,id LIMIT 200").stream().map(this::value).toList();}
    @Transactional public Created create(String actor,Create input){
        if(input==null||input.requestId()==null||!UUID.fromString(input.requestId()).toString().equals(input.requestId()))throw new IllegalArgumentException("Stable credential request ID required");
        String worker=input.workerId()==null?"":input.workerId().strip(),label=input.label()==null?"":input.label().strip();
        if(!worker.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")||worker.startsWith("factory-")||label.isBlank()||label.length()>120||label.indexOf(0)>=0)throw new IllegalArgumentException("Provide an external Worker ID and a short label");
        var prior=jdbc.queryForList("SELECT * FROM worker_access_credential WHERE id=?",input.requestId());
        if(!prior.isEmpty()){
            var row=prior.getFirst();if(!worker.equals(row.get("worker_id"))||!label.equals(row.get("label"))||!actor.equals(row.get("created_by")))throw new IllegalStateException("Credential request changed; start a new request");
            return new Created(value(row),null); // A lost creation reply cannot recover a plaintext token.
        }
        String token="external."+input.requestId()+"."+secret();long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO worker_access_credential VALUES(?,?,?,?,'ACTIVE',1,?,?,NULL)",input.requestId(),worker,label,AuthorityRepository.hash(token),actor,now);
        return new Created(value(jdbc.queryForMap("SELECT * FROM worker_access_credential WHERE id=?",input.requestId())),token);
    }
    @Transactional public Credential change(String id,Change input){
        if(input==null||!Set.of("ACTIVE","DRAINING","REVOKED").contains(input.state()))throw new IllegalArgumentException("Select active, stop new work, or revoke");
        var row=jdbc.queryForMap("SELECT * FROM worker_access_credential WHERE id=?",id);
        if("REVOKED".equals(row.get("state"))&&!"REVOKED".equals(input.state()))throw new IllegalStateException("Revoked credentials cannot be restored");
        if(jdbc.update("UPDATE worker_access_credential SET state=?,revision=revision+1 WHERE id=? AND revision=?",input.state(),id,input.revision())!=1)throw new IllegalStateException("Credential changed; refresh before deciding");
        return value(jdbc.queryForMap("SELECT * FROM worker_access_credential WHERE id=?",id));
    }
    public String authenticate(String token,boolean newWork){
        if(token==null||token.length()>512)throw new SecurityException("Invalid Worker credential");
        String[] parts=token.split("\\.",3);if(parts.length!=3)throw new SecurityException("Invalid Worker credential");
        if(parts[0].equals("local")&&equal(local.get(parts[1]),token))return parts[1];
        if(!parts[0].equals("external"))throw new SecurityException("Invalid Worker credential");
        var rows=jdbc.queryForList("SELECT * FROM worker_access_credential WHERE id=?",parts[1]);
        if(rows.size()!=1||!equal((String)rows.getFirst().get("token_hash"),AuthorityRepository.hash(token))||"REVOKED".equals(rows.getFirst().get("state")))throw new SecurityException("Invalid Worker credential");
        var row=rows.getFirst();if(newWork&&"DRAINING".equals(row.get("state")))throw new IllegalStateException("WORKER_ACCESS_DRAINING");
        jdbc.update("UPDATE worker_access_credential SET last_used_ms=? WHERE id=?",System.currentTimeMillis(),parts[1]);
        return (String)row.get("worker_id");
    }
}
