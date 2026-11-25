package me.asu.aidev.execution.tools;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SharedToolAccess {
    public static final String CAPABILITY="shared-http-tools-v1";
    private static final int MAX_BODY=64*1024;
    private static final String MCP_VERSION="2026-07-28";
    private static final Set<String> TYPES=Set.of("string","integer","boolean");
    private final AuthorityRepository db;
    private final AuthorityBoundary boundary;
    private final ExecutionGuard guard;
    private final SharedToolCatalog catalog;
    private final Set<String> endpoints;
    private final TransactionTemplate transaction;
    @Value("${aidev.shared-tools.mcp-endpoints:}") private String mcpEndpoints="";

    public SharedToolAccess(AuthorityRepository db,AuthorityBoundary boundary,ExecutionGuard guard,SharedToolCatalog catalog,
            PlatformTransactionManager transactions,@Value("${aidev.shared-tools.http-endpoints:}") String endpoints) {
        this.db=db;this.boundary=boundary;this.guard=guard;this.catalog=catalog;
        this.endpoints=new HashSet<>(Arrays.stream(endpoints.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList());
        transaction=new TransactionTemplate(transactions);
    }

    public record Ref(String id,long revision) {
        public Ref {
            if(id==null||!id.matches("[a-z][a-z0-9._-]{0,63}")||revision<1)
                throw new IllegalArgumentException("Specify a shared tool ID and an exact revision.");
        }
    }
    public record Call(Lease lease,String operationId,Ref tool,Map<String,Object> arguments) {
        public Call {
            Objects.requireNonNull(lease,"Lease required");
            Objects.requireNonNull(tool,"Tool required");
            if(operationId==null||!operationId.matches("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}"))
                throw new IllegalArgumentException("A UUID operation ID is required.");
            arguments=arguments==null?Map.of():new TreeMap<>(arguments);
        }
    }
    public record Result(String operationId,String state,Integer httpStatus,String output) {}

    public void validate(List<Ref> refs) {
        refs.forEach(ref->eligible(current(ref)));
    }

    public List<SharedToolCatalog.Item> executable() {
        if(endpoints.isEmpty()&&mcpEndpoints.isBlank())return List.of();
        var items=new ArrayList<SharedToolCatalog.Item>();
        var refs=db.jdbc().query("SELECT id,revision FROM shared_tool WHERE enabled AND (kind='MCP' OR http_method='GET') ORDER BY id",
                (rs,row)->new Ref(rs.getString(1),rs.getLong(2)));
        for(var ref:refs) {
            try {var entry=current(ref);eligible(entry);items.add(entry.tool());}
            catch(IllegalArgumentException unavailable) { /* The catalogue can hold tools ahead of deployment. */ }
        }
        return List.copyOf(items);
    }

    @Transactional
    public void bind(String task,List<Ref> refs) {
        if(refs.isEmpty())return;
        boundary.lock();validate(refs);
        for(var ref:refs)db.jdbc().update("INSERT INTO shared_tool_task_grant VALUES(?,?,?,FALSE)",task,ref.id(),ref.revision());
    }

    @Transactional
    public void inherit(String source,String successor) {
        boundary.lock();requireSettled(source);
        db.jdbc().update("""
                INSERT INTO shared_tool_task_grant(task_id,tool_id,tool_revision,revoked)
                SELECT ?,tool_id,tool_revision,revoked FROM shared_tool_task_grant WHERE task_id=?
                ON CONFLICT(task_id,tool_id) DO NOTHING
                """,successor,source);
    }

    public void requireSettled(String task) {
        boundary.lock();
        if(db.jdbc().queryForObject("SELECT COUNT(*) FROM shared_tool_operation WHERE task_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,task)>0)
            throw new IllegalStateException("A shared tool operation needs human reconciliation.");
    }

    @Transactional
    public void revoke(String actor,String task,Ref ref) {
        boundary.lock();
        requireOwner(actor,task);
        int changed=db.jdbc().update("""
                UPDATE shared_tool_task_grant SET revoked=TRUE
                 WHERE task_id=? AND tool_id=? AND tool_revision=? AND revoked=FALSE
                """,task,ref.id(),ref.revision());
        if(changed==0)throw new IllegalStateException("The task tool grant is missing or already revoked.");
        db.audit("SHARED_TOOL:"+ref.id(),null,null,actor,"SHARED_TOOL_GRANT_REVOKED",task+" v"+ref.revision());
    }

    public record Reconciliation(String operationId,String reason) {}
    public record Receipt(String operationId,String toolId,long revision,String state,long createdMs,Long finishedMs) {}

    public List<Receipt> receipts(String actor,String task) {
        requireOwner(actor,task);
        return db.jdbc().query("""
                SELECT id,tool_id,tool_revision,state,created_ms,finished_ms FROM shared_tool_operation
                 WHERE task_id=? ORDER BY created_ms,id
                """,(rs,row)->new Receipt(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4),
                        rs.getLong(5),rs.getObject(6)==null?null:rs.getLong(6)),task);
    }

    @Transactional
    public void reconcile(String actor,String task,Reconciliation input) {
        boundary.lock();requireOwner(actor,task);
        if(input==null||input.operationId()==null||input.reason()==null||input.reason().isBlank()||input.reason().length()>1000)
            throw new IllegalArgumentException("Provide an operation ID and the evidence reviewed.");
        long now=System.currentTimeMillis();
        if(db.jdbc().queryForObject("SELECT COUNT(*) FROM work_task WHERE id=? AND lease_token IS NOT NULL",Long.class,task)>0)
            throw new IllegalStateException("Wait for the task lease to end before reconciliation.");
        int changed=db.jdbc().update("""
                UPDATE shared_tool_operation SET state='RECONCILED',finished_ms=?
                 WHERE task_id=? AND id=? AND (state='UNKNOWN' OR (state='STARTED' AND created_ms<?))
                """,now,task,input.operationId(),now-60_000);
        if(changed!=1)throw new IllegalStateException("The shared tool operation is not ready for reconciliation.");
        db.audit("WORK_TASK:"+task,null,input.operationId(),actor,"SHARED_TOOL_RECONCILED",input.reason().strip());
    }

    private void requireOwner(String actor,String task) {
        if(db.jdbc().queryForObject("""
                SELECT COUNT(*) FROM work_task t JOIN plan_run_owner o ON o.plan_id=t.subject_id
                 WHERE t.id=? AND t.subject_type='flexible-plan' AND o.actor=?
                """,Long.class,task,actor)!=1)throw new SecurityException("Task owner required.");
    }

    public List<Ref> bindings(String task) {
        return db.jdbc().query("""
                SELECT tool_id,tool_revision FROM shared_tool_task_grant
                 WHERE task_id=? AND revoked=FALSE ORDER BY tool_id
                """,(rs,row)->new Ref(rs.getString(1),rs.getLong(2)),task);
    }

    @Transactional
    public List<SharedToolCatalog.Item> discover(String task,Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,true,true);
        var result=new ArrayList<SharedToolCatalog.Item>();
        for(var ref:bindings(task)) {
            try {var entry=current(ref);eligible(entry);result.add(entry.tool());}
            catch(IllegalArgumentException unavailable) { /* The old grant cannot authorize the changed entry. */ }
        }
        return List.copyOf(result);
    }

    public Result invoke(String task,Call call) {
        var admission=transaction.execute(status->reserve(task,call));
        if(admission.previous()!=null)return admission.previous();
        Result result;
        try {result=request(call.operationId(),admission);}
        catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result=new Result(call.operationId(),"UNKNOWN",null,null);
        } catch(IOException failure) {
            result=new Result(call.operationId(),"UNKNOWN",null,null);
        }
        Result outcome=result;
        boolean deliver=Boolean.TRUE.equals(transaction.execute(status->finish(task,call,outcome)));
        return deliver?result:new Result(result.operationId(),result.state(),null,null);
    }

    private record Admission(URI uri,String mcpName,byte[] mcpRequest,Result previous) {}

    private Admission reserve(String task,Call call) {
        boundary.lock();guard.verifyLease(task,call.lease(),true,true);
        var ref=call.tool();
        if(!bindings(task).contains(ref))throw new SecurityException("The task has no live grant for this tool revision.");
        var entry=current(ref);eligible(entry);
        String query=query(entry,call.arguments());
        boolean mcp=entry.tool().kind()==SharedToolCatalog.Kind.MCP;
        var uri=URI.create(entry.endpoint()+(mcp||query.isEmpty()?"":"?"+query));
        String digest=AuthorityRepository.hash(db.encode(call.arguments()));
        var existing=db.jdbc().queryForList("SELECT * FROM shared_tool_operation WHERE id=?",call.operationId());
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(!task.equals(row.get("task_id"))||!call.lease().attemptId().equals(row.get("attempt_id"))
                    ||!call.lease().workerId().equals(row.get("worker_id"))||!ref.id().equals(row.get("tool_id"))
                    ||ref.revision()!=((Number)row.get("tool_revision")).longValue()||!digest.equals(row.get("request_digest")))
                throw new IllegalStateException("Shared tool operation ID has a different request.");
            return new Admission(null,null,null,new Result(call.operationId(),(String)row.get("state"),null,null));
        }
        requireSettled(task);
        if(db.jdbc().queryForObject("SELECT COUNT(*) FROM shared_tool_operation WHERE task_id=?",Long.class,task)>=8)
            throw new IllegalStateException("This task has reached its shared tool call limit.");
        db.jdbc().update("""
                INSERT INTO shared_tool_operation(id,task_id,attempt_id,worker_id,tool_id,tool_revision,request_digest,state,created_ms)
                VALUES(?,?,?,?,?,?,?,'STARTED',?)
                """,call.operationId(),task,call.lease().attemptId(),call.lease().workerId(),ref.id(),ref.revision(),digest,System.currentTimeMillis());
        String name=mcp?entry.tool().operation():null;
        byte[] payload=mcp?db.encode(Map.of("jsonrpc","2.0","id",call.operationId(),"method","tools/call",
                "params",Map.of("name",name,"arguments",call.arguments(),"_meta",Map.of("io.modelcontextprotocol/protocolVersion",
                        MCP_VERSION,"io.modelcontextprotocol/clientInfo",Map.of("name","AIW","version","1"),
                        "io.modelcontextprotocol/clientCapabilities",Map.of())))).getBytes(StandardCharsets.UTF_8):null;
        return new Admission(uri,name,payload,null);
    }

    private boolean finish(String task,Call call,Result outcome) {
        boundary.lock();
        String digest=outcome.output()==null?null:AuthorityRepository.hash(outcome.output());
        if(db.jdbc().update("""
                UPDATE shared_tool_operation SET state=?,result_digest=?,finished_ms=? WHERE id=? AND state='STARTED'
                """,outcome.state(),digest,System.currentTimeMillis(),call.operationId())!=1)
            throw new IllegalStateException("The shared tool receipt already changed.");
        try {
            guard.verifyLease(task,call.lease(),true,true);
            if(!bindings(task).contains(call.tool()))return false;
            eligible(current(call.tool()));
            return true;
        } catch(SecurityException|IllegalArgumentException unavailable) {return false;}
    }

    private SharedToolCatalog.Entry current(Ref ref) {
        try {
            var entry=catalog.get(ref.id());
            if(entry.tool().enabled()&&entry.tool().revision()==ref.revision())return entry;
        } catch(NoSuchElementException missing) { /* Report the same unavailable state. */ }
        throw new IllegalArgumentException("Shared tool revision unavailable: "+ref.id()+" v"+ref.revision());
    }

    private void eligible(SharedToolCatalog.Entry entry) {
        var endpoint=URI.create(entry.endpoint());
        boolean https="https".equals(endpoint.getScheme());
        boolean allowed=entry.tool().kind()==SharedToolCatalog.Kind.MCP
                ? (https || "http".equals(endpoint.getScheme())&&"127.0.0.1".equals(endpoint.getHost()))
                    && Arrays.stream(mcpEndpoints.split(",")).map(String::strip).anyMatch(entry.endpoint()::equals)
                    && entry.tool().operation().matches("[A-Za-z0-9_.-]{1,120}")
                : https&&"GET".equals(entry.tool().httpMethod())&&endpoints.contains(entry.endpoint());
        if(!allowed)
            throw new IllegalArgumentException("Shared tool is not enabled for read-only execution: "+entry.tool().id());
        parameters(entry.tool().inputSchema());
    }

    private static String query(SharedToolCatalog.Entry entry,Map<String,Object> arguments) {
        var parameters=parameters(entry.tool().inputSchema());
        var required=entry.tool().inputSchema().getOrDefault("required",List.of());
        if(!(required instanceof List<?> names)||!arguments.keySet().containsAll(names)||!parameters.keySet().containsAll(arguments.keySet()))
            throw new IllegalArgumentException("Shared tool arguments do not match the declared parameters.");
        var pairs=new StringJoiner("&");
        for(var argument:arguments.entrySet()) {
            Object value=argument.getValue();
            boolean valid=switch(parameters.get(argument.getKey())) {
                case "string" -> value instanceof String s&&s.length()<=256&&s.chars().noneMatch(Character::isISOControl);
                case "integer" -> value instanceof Integer||value instanceof Long;
                case "boolean" -> value instanceof Boolean;
                default -> false;
            };
            if(!valid)throw new IllegalArgumentException("Invalid shared tool parameter: "+argument.getKey());
            pairs.add(argument.getKey()+"="+URLEncoder.encode(value.toString(),StandardCharsets.UTF_8));
        }
        if(pairs.length()>4096)throw new IllegalArgumentException("Shared tool query is too large.");
        return pairs.toString();
    }

    private static Map<String,String> parameters(Map<String,Object> schema) {
        if(!"object".equals(schema.get("type"))||!Set.of("type","properties","required","additionalProperties","description","title").containsAll(schema.keySet())
                ||Boolean.TRUE.equals(schema.get("additionalProperties"))
                ||!(schema.getOrDefault("properties",Map.of()) instanceof Map<?,?> properties)||properties.size()>12)
            throw new IllegalArgumentException("HTTP tools require a flat object input schema.");
        var result=new HashMap<String,String>();
        for(var entry:properties.entrySet()) {
            if(!(entry.getKey() instanceof String key)||!key.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}")
                    ||!(entry.getValue() instanceof Map<?,?> rule)||!Set.of("type","description","title").containsAll(rule.keySet())
                    ||!(rule.get("type") instanceof String type)||!TYPES.contains(type))
                throw new IllegalArgumentException("HTTP tool parameters must be named strings, integers or booleans.");
            result.put(key,type);
        }
        if(!(schema.getOrDefault("required",List.of()) instanceof List<?> names)||!result.keySet().containsAll(names))
            throw new IllegalArgumentException("HTTP tool required parameters must be declared.");
        return result;
    }

    private Result request(String operation,Admission admission) throws IOException,InterruptedException {
        var connection=(HttpURLConnection)admission.uri().toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
        boolean mcp=admission.mcpRequest()!=null;
        connection.setRequestMethod(mcp?"POST":"GET");
        connection.setRequestProperty("User-Agent","AIW-SharedTools/1.0");
        connection.setRequestProperty("Accept",mcp?"application/json, text/event-stream":"application/json");
        long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
        try {
            if(mcp) {
                connection.setRequestProperty("Content-Type","application/json");
                connection.setRequestProperty("MCP-Protocol-Version",MCP_VERSION);
                connection.setRequestProperty("Mcp-Method","tools/call");
                connection.setRequestProperty("Mcp-Name",admission.mcpName());
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(admission.mcpRequest().length);
                try(var request=connection.getOutputStream()){request.write(admission.mcpRequest());}
            }
            int status=connection.getResponseCode();
            if(status<200||status>=300)return new Result(operation,"REJECTED",status,null);
            if(connection.getContentLengthLong()>MAX_BODY)return new Result(operation,"REJECTED",status,null);
            try(var input=connection.getInputStream();var output=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];
                for(int read;(read=input.read(buffer))!=-1;) {
                    if(Thread.interrupted())throw new InterruptedException("Shared HTTP call interrupted.");
                    if(System.nanoTime()>deadline)throw new IOException("Shared HTTP deadline.");
                    if(output.size()+read>MAX_BODY)return new Result(operation,"REJECTED",status,null);
                    output.write(buffer,0,read);
                }
                String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString();
                if(mcp) {
                    if(!Objects.toString(connection.getContentType(),"").toLowerCase(Locale.ROOT).startsWith("application/json"))
                        throw new IOException("Unsupported MCP response transport.");
                    Map<?,?> message;
                    try {message=db.decodeComplete(text,Map.class);}
                    catch(IllegalArgumentException invalid) {throw new IOException("Invalid MCP response.");}
                    if(message==null||!"2.0".equals(message.get("jsonrpc"))||!operation.equals(message.get("id")))
                        throw new IOException("MCP response ID mismatch.");
                    if(message.containsKey("error")||!(message.get("result") instanceof Map<?,?> result)
                            ||!"complete".equals(result.get("resultType"))||Boolean.TRUE.equals(result.get("isError")))
                        return new Result(operation,"REJECTED",status,null);
                    text=db.encode(result);
                }
                return new Result(operation,"COMPLETED",status,text);
            }
        } finally {connection.disconnect();}
    }
}
