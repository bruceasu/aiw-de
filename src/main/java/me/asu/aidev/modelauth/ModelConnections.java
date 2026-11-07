package me.asu.aidev.modelauth;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

/** Plane owns renewable credentials; Workers receive only an exact-task short-lived access token. */
@Service
public class ModelConnections {
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.guard.ExecutionAccounting accounting;
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final CredentialVault vault;private final OpenAiOAuth oauth;private final ExecutionGuard guard;
    private final TransactionTemplate transaction;
    @Value("${aidev.worker-protocol.token:}") private String workerToken;
    public ModelConnections(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,CredentialVault vault,
            OpenAiOAuth oauth,ExecutionGuard guard,PlatformTransactionManager manager) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.vault=vault;this.oauth=oauth;this.guard=guard;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    String actor() {
        if(boundary.human()==null||boundary.delegated())throw new SecurityException("MODEL_AUTH_HUMAN_REQUIRED");
        grants.human(boundary.human(),"A17_CONFIGURATION");return boundary.human();
    }
    public Map<String,Object> state() {
        String actor=actor();var values=repo.jdbc().queryForList("SELECT * FROM model_connection WHERE owner_actor=? ORDER BY updated_ms DESC",actor);
        return Map.of("vaultConfigured",vault.ready(),"connections",values.stream().map(this::projection).toList());
    }
    private Connection projection(Map<String,Object> row) {
        String state=(String)row.get("state");
        if(state.equals("REFRESHING")&&number(row,"refresh_started_ms")+30000<System.currentTimeMillis())state="REFRESH_OUTCOME_UNKNOWN";
        return new Connection((String)row.get("id"),Kind.valueOf((String)row.get("kind")),(String)row.get("label"),state,
                number(row,"version"),number(row,"generation"),number(row,"expires_ms"));
    }
    Map<String,Object> row(String id) {
        if(id==null||!id.matches("MC-[0-9a-f-]{36}"))throw new IllegalArgumentException("MODEL_CONNECTION_REQUIRED");
        var rows=repo.jdbc().queryForList("SELECT * FROM model_connection WHERE id=?",id);
        if(rows.size()!=1)throw new IllegalArgumentException("MODEL_CONNECTION_NOT_FOUND");return rows.getFirst();
    }
    Map<String,Object> owned(String id,long version,String actor) {
        var row=row(id);if(!actor.equals(row.get("owner_actor")))throw new SecurityException("MODEL_CONNECTION_OWNER_MISMATCH");
        if(version!=number(row,"version"))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");return row;
    }
    public void selection(Selection selected,String runtime) {
        var row=owned(selected.id(),selected.version(),actor());
        if(!runtime(row).equals(runtime)||!"READY".equals(row.get("state")))throw new IllegalArgumentException("MODEL_CONNECTION_NOT_READY_FOR_RUNTIME");
        if(!vault.ready())throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
    }
    private static String runtime(Map<String,Object> row) {return Kind.CODEX_APP_SERVER.name().equals(row.get("kind"))?"codex-app-server":"openai-agents";}
    public static String capability(String id,long version) {return "model-auth:"+AuthorityRepository.hash(id+":"+version);}
    public String registerWorker(String worker,String id,long version,String runtime,String model) {
        var connection=row(id);
        if(version!=number(connection,"version")||!runtime.equals(runtime(connection))||!"READY".equals(connection.get("state")))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
        repo.jdbc().update("INSERT INTO model_auth_worker(worker_id,connection_id,connection_version,runtime,model) VALUES(?,?,?,?,?) ON CONFLICT(worker_id) DO NOTHING",worker,id,version,runtime,model);
        var fixed=repo.jdbc().queryForMap("SELECT * FROM model_auth_worker WHERE worker_id=?",worker);
        if(!id.equals(fixed.get("connection_id"))||version!=number(fixed,"connection_version")||!model.equals(fixed.get("model"))||!runtime.equals(fixed.get("runtime")))throw new SecurityException("MODEL_WORKER_CONFIGURATION_CHANGED");
        String aad="aiw.model-worker.v1:"+worker+":"+id+":"+version;
        if(fixed.get("proof_cipher")==null&&fixed.get("proof_digest")==null) {
            var bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String proof=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            repo.jdbc().update("UPDATE model_auth_worker SET proof_digest=?,proof_cipher=? WHERE worker_id=? AND proof_digest IS NULL AND proof_cipher IS NULL",AuthorityRepository.hash(proof),vault.seal(aad,proof),worker);
            fixed=repo.jdbc().queryForMap("SELECT * FROM model_auth_worker WHERE worker_id=?",worker);
        }
        String proof=vault.open(aad,(String)fixed.get("proof_cipher"));
        if(!AuthorityRepository.hash(proof).equals(fixed.get("proof_digest")))throw new SecurityException("MODEL_WORKER_PROOF_INVALID");return proof;
    }
    String proofHeader() {
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet?Objects.toString(servlet.getRequest().getHeader("X-AIW-Model-Worker"),""):"";
    }
    private Map<String,Object> workerMapping(String id,String runtime,Set<String> capabilities) {
        var rows=repo.jdbc().queryForList("SELECT * FROM model_auth_worker WHERE worker_id=?",id);
        if(rows.size()!=1)throw new SecurityException("MODEL_WORKER_BINDING_REQUIRED");var fixed=rows.getFirst();
        if(!(fixed.get("proof_digest") instanceof String)||!runtime.startsWith(fixed.get("runtime")+":")
                ||capabilities.stream().filter(value->value.startsWith("model-auth:")).count()!=1
                ||!capabilities.contains(capability((String)fixed.get("connection_id"),number(fixed,"connection_version"))))throw new SecurityException("MODEL_WORKER_CONFIGURATION_CHANGED");
        return fixed;
    }
    private void requireWorkerProof(Map<String,Object> fixed) {
        if(!(fixed.get("proof_digest") instanceof String expected)||!java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),AuthorityRepository.hash(proofHeader()).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new SecurityException("MODEL_WORKER_PROOF_REQUIRED");
    }
    public void validateRegistration(me.asu.aidev.worker.application.RegisterWorkerCommand input) {
        boolean bound=!repo.jdbc().queryForList("SELECT worker_id FROM model_auth_worker WHERE worker_id=?",String.class,input.workerId().value()).isEmpty();
        if(bound||input.capabilities().stream().anyMatch(value->value.startsWith("model-auth:")))requireWorkerProof(workerMapping(input.workerId().value(),input.runtimeType(),input.capabilities()));
    }
    public Connection importCredential(Import input) {
        String actor=actor();if(!vault.ready())throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
        if(input.content()==null||input.content().length()>98304)throw new IllegalArgumentException("MODEL_IMPORT_LIMIT");
        var source=repo.decode(input.content(),com.fasterxml.jackson.databind.JsonNode.class);
        Kind kind=source.has("tokens")?Kind.CODEX_APP_SERVER:Kind.CHATGPT_RESPONSES;
        var issued=oauth.importTokens(source);
        var prior=input.connectionId()==null?null:owned(input.connectionId(),Objects.requireNonNull(input.expectedVersion()),actor);
        return save(actor,kind,input.label(),issued,prior);
    }
    Connection save(String actor,Kind kind,String label,Issued issued,Map<String,Object> prior) {
        if(label==null||label.isBlank()||label.length()>160)throw new IllegalArgumentException("MODEL_CONNECTION_LABEL_REQUIRED");
        return transaction.execute(status->{
            String id=prior==null?"MC-"+UUID.randomUUID():(String)prior.get("id");
            long version=prior==null?1:number(prior,"version"),generation=prior==null?1:number(prior,"generation")+1;
            if(prior!=null&&(!actor.equals(prior.get("owner_actor"))||!kind.name().equals(prior.get("kind"))
                    ||!issued.clientId().equals(prior.get("client_id"))||!issued.subject().equals(prior.get("subject_id"))
                    ||!Objects.equals(issued.accountId(),prior.get("account_id"))))throw new SecurityException("MODEL_ACCOUNT_OR_CLIENT_CHANGED");
            rememberToken(id,issued.tokens().refreshToken());
            String state=issued.inferenceAllowed()?"READY":"IDENTITY_ONLY";
            String cipher=vault.seal(aad(id,actor,issued.clientId(),generation),repo.encode(issued.tokens()));
            if(prior==null) repo.jdbc().update("INSERT INTO model_connection VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,actor,kind.name(),label,issued.clientId(),issued.subject(),issued.accountId(),issued.planType(),state,version,generation,issued.expiresAt(),issued.earliestRefreshAt(),cipher,null,System.currentTimeMillis());
            else if(repo.jdbc().update("UPDATE model_connection SET label=?,state=?,generation=?,expires_ms=?,earliest_refresh_ms=?,secret_cipher=?,refresh_started_ms=NULL,updated_ms=? WHERE id=? AND owner_actor=? AND version=? AND generation=? AND (state<>'REFRESHING' OR COALESCE(refresh_started_ms,0)<=?)",
                    label,state,generation,issued.expiresAt(),issued.earliestRefreshAt(),cipher,System.currentTimeMillis(),id,actor,version,generation-1,System.currentTimeMillis()-30000)!=1)throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
            repo.audit("MODEL_CONNECTION:"+id,null,null,actor,"MODEL_CREDENTIAL_SAVED","Validated token set; generation "+generation);
            return projection(row(id));
        });
    }
    private void rememberToken(String id,String refresh) {
        String digest=AuthorityRepository.hash(refresh);
        var previous=repo.jdbc().queryForList("SELECT connection_id FROM model_token_seen WHERE token_digest=?",String.class,digest);
        if(!previous.isEmpty())throw new IllegalStateException("MODEL_TOKEN_ALREADY_IMPORTED_OR_USED");
        repo.jdbc().update("INSERT INTO model_token_seen VALUES(?,?)",digest,id);
    }
    private static String aad(String id,String actor,String client,long generation) {return String.join("\n","aiw.model-auth.v1",id,actor,client,Long.toString(generation));}
    private TokenSet secrets(Map<String,Object> row) {return repo.decode(vault.open(aad((String)row.get("id"),(String)row.get("owner_actor"),(String)row.get("client_id"),number(row,"generation")),(String)row.get("secret_cipher")),TokenSet.class);}
    public Connection refresh(Selection selected) {
        var row=owned(selected.id(),selected.version(),actor());
        refresh(row,true,null);return projection(row(selected.id()));
    }
    /** Explicit console-admin catalog read, separate from machine task/Attempt access. */
    public AccessToken catalogAccess(Selection selected,String runtime) {
        selection(selected,runtime);String actor=actor();
        var current=refresh(owned(selected.id(),selected.version(),actor),false,null);
        var latest=owned(selected.id(),selected.version(),actor);
        if(!"READY".equals(latest.get("state"))||number(latest,"generation")!=number(current,"generation"))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
        return new AccessToken(secrets(current).accessToken(),number(current,"expires_ms"),number(current,"generation"),Kind.valueOf((String)current.get("kind")),(String)current.get("account_id"),(String)current.get("plan_type"));
    }
    private Map<String,Object> refresh(Map<String,Object> initial,boolean force,Long rejectedGeneration) {
        String id=(String)initial.get("id");
        var locked=transaction.execute(status->{
            var row=row(id);
            if(number(initial,"version")!=number(row,"version"))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
            if("REFRESHING".equals(row.get("state"))&&number(row,"refresh_started_ms")+30000<System.currentTimeMillis()) {
                repo.jdbc().update("UPDATE model_connection SET state='REFRESH_OUTCOME_UNKNOWN',updated_ms=? WHERE id=? AND state='REFRESHING' AND generation=?",System.currentTimeMillis(),id,number(row,"generation"));
                return null;
            }
            if("REFRESHING".equals(row.get("state"))) {var waiting=new LinkedHashMap<>(row);waiting.put("wait",true);return waiting;}
            if(!READY.contains(row.get("state")))throw new IllegalStateException("MODEL_AUTH_"+row.get("state"));
            if(rejectedGeneration!=null&&rejectedGeneration<number(row,"generation"))return row;
            if(!force&&number(row,"expires_ms")>System.currentTimeMillis()+180000)return row;
            if(number(row,"earliest_refresh_ms")>System.currentTimeMillis())throw new IllegalStateException("MODEL_REFRESH_TOO_EARLY");
            // Decode before consuming the durable intent; a missing/wrong master key sends nothing.
            secrets(row);
            if(repo.jdbc().update("UPDATE model_connection SET state='REFRESHING',refresh_started_ms=?,updated_ms=? WHERE id=? AND version=? AND generation=? AND state IN ('READY','IDENTITY_ONLY')",
                    System.currentTimeMillis(),System.currentTimeMillis(),id,number(row,"version"),number(row,"generation"))!=1) {
                var waiting=new LinkedHashMap<>(row);waiting.put("wait",true);return waiting;
            }
            var result=new LinkedHashMap<>(row);result.put("send",true);return result;
        });
        if(locked==null)throw new IllegalStateException("MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN");
        if(Boolean.TRUE.equals(locked.get("wait"))) {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while(System.nanoTime()<deadline) {
                var current=row(id);
                if(number(initial,"version")!=number(current,"version"))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
                if(!"REFRESHING".equals(current.get("state"))) {
                    if(READY.contains(current.get("state"))&&number(current,"generation")>number(initial,"generation"))return current;
                    throw new IllegalStateException("MODEL_AUTH_"+current.get("state"));
                }
                try {Thread.sleep(50);}catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_REFRESH_IN_PROGRESS");}
            }
            throw new IllegalStateException("MODEL_REFRESH_IN_PROGRESS");
        }
        if(!Boolean.TRUE.equals(locked.get("send")))return locked;
        try {
            var kind=Kind.valueOf((String)locked.get("kind"));
            var issued=oauth.refresh(kind,(String)locked.get("client_id"),(String)locked.get("subject_id"),secrets(locked));
            if(!Objects.equals(issued.accountId(),locked.get("account_id")))throw new SecurityException("MODEL_ACCOUNT_CHANGED");
            transaction.executeWithoutResult(status->{
                long generation=number(locked,"generation")+1;
                rememberToken(id,issued.tokens().refreshToken());
                String cipher=vault.seal(aad(id,(String)locked.get("owner_actor"),issued.clientId(),generation),repo.encode(issued.tokens()));
                if(repo.jdbc().update("UPDATE model_connection SET state=?,generation=?,expires_ms=?,earliest_refresh_ms=?,secret_cipher=?,refresh_started_ms=NULL,updated_ms=? WHERE id=? AND version=? AND generation=? AND state='REFRESHING'",
                        issued.inferenceAllowed()?"READY":"IDENTITY_ONLY",generation,issued.expiresAt(),issued.earliestRefreshAt(),cipher,System.currentTimeMillis(),id,number(locked,"version"),generation-1)!=1)throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
                repo.audit("MODEL_CONNECTION:"+id,null,null,"plane","MODEL_CREDENTIAL_REFRESHED","Token generation "+generation);
            });
            return row(id);
        }catch(RuntimeException error) {
            transaction.executeWithoutResult(status->repo.jdbc().update("UPDATE model_connection SET state='REFRESH_OUTCOME_UNKNOWN',updated_ms=? WHERE id=? AND version=? AND generation=? AND state='REFRESHING'",
                    System.currentTimeMillis(),id,number(locked,"version"),number(locked,"generation")));
            throw new IllegalStateException("MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN");
        }
    }
    /** Commit local denial first; remote failure cannot resurrect locally cleared credentials. */
    public Disconnection disconnect(Selection input) {
        String actor=actor();TokenSet[] retained={null};String[] client={null};boolean[] known={false};
        var connection=transaction.execute(status->{
            var old=owned(input.id(),input.version(),actor);client[0]=(String)old.get("client_id");known[0]=READY.contains(old.get("state"));
            try {if(old.get("secret_cipher")!=null)retained[0]=secrets(old);}catch(IllegalStateException ignored) { /* Local clearing remains possible after key loss. */ }
            if(repo.jdbc().update("UPDATE model_connection SET state='DISCONNECTED',version=version+1,secret_cipher=NULL,updated_ms=? WHERE id=? AND owner_actor=? AND version=? AND generation=? AND state=?",
                    System.currentTimeMillis(),input.id(),actor,input.version(),number(old,"generation"),old.get("state"))!=1)throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
            repo.audit("MODEL_CONNECTION:"+input.id(),null,null,actor,"MODEL_CONNECTION_DISCONNECTED","Local credentials cleared; remote revocation not confirmed");
            return projection(row(input.id()));
        });
        boolean confirmed=retained[0]!=null&&oauth.revoke(client[0],retained[0])&&known[0];retained[0]=null;
        return new Disconnection(connection,confirmed);
    }
    String host() {
        return transaction.execute(status->{
            repo.jdbc().update("INSERT INTO model_auth_host VALUES(1,?) ON CONFLICT(id) DO NOTHING","urn:uuid:"+UUID.randomUUID());
            return repo.jdbc().queryForObject("SELECT host_id FROM model_auth_host WHERE id=1",String.class);
        });
    }
    Map<String,Object> forWorker(Worker worker) {
        var auth=worker.capabilities().stream().filter(cap->cap.startsWith("model-auth:")).toList();
        var fixed=repo.jdbc().queryForList("SELECT * FROM model_auth_worker WHERE worker_id=?",worker.id().value());
        if(auth.isEmpty()&&fixed.isEmpty())return null;
        var binding=workerMapping(worker.id().value(),worker.runtimeType(),worker.capabilities());
        var row=row((String)binding.get("connection_id"));
        if(number(row,"version")!=number(binding,"connection_version")||!"READY".equals(row.get("state")))throw new IllegalStateException("MODEL_CONNECTION_NOT_READY");return row;
    }
    private String taskActor(WorkTask task) {
        var managed=repo.jdbc().queryForList("SELECT o.actor FROM execution_context c JOIN authority_owner o ON o.id=c.owner_id WHERE c.task_id=?",String.class,task.id().value());
        if(managed.size()==1)return managed.getFirst();
        if(task.subjectType().equals("flexible-plan"))return repo.jdbc().queryForObject("SELECT created_by FROM flexible_workflow_plan WHERE id=?",String.class,task.subjectId());
        if(task.subjectType().equals("goal-discussion"))return repo.jdbc().queryForObject("SELECT actor FROM goal_request WHERE id=?",String.class,task.id().value());
        if(task.subjectType().equals("workflow-recommendation"))return repo.jdbc().queryForObject("SELECT actor FROM workflow_recommendation_request WHERE task_id=?",String.class,task.id().value());
        throw new SecurityException("MODEL_CREDENTIAL_TASK_OWNER_UNSUPPORTED");
    }
    public boolean canClaim(WorkTask task,Worker worker) {
        try {
            var row=forWorker(worker);var bindings=repo.jdbc().queryForList("SELECT * FROM model_auth_task WHERE task_id=?",task.id().value());
            var plans=repo.jdbc().queryForList("SELECT * FROM model_auth_plan WHERE subject_type=? AND subject_id=?",task.subjectType(),task.subjectId());
            if(row==null)return bindings.isEmpty()&&plans.isEmpty();
            if(!plans.isEmpty()&&(!row.get("id").equals(plans.getFirst().get("connection_id"))||number(row,"version")!=number(plans.getFirst(),"connection_version")))return false;
            // Subscription usage has no verified API-key currency price. Never borrow that price table.
            if(accounting!=null&&accounting.enforced()&&guard.managed(task.id().value()))return false;
            if(!taskActor(task).equals(row.get("owner_actor")))return false;
            return bindings.isEmpty()||bindings.getFirst().get("connection_id").equals(row.get("id"))&&number(bindings.getFirst(),"connection_version")==number(row,"version");
        }catch(RuntimeException error) {return false;}
    }
    /** Runs in TaskClaimService's existing claim transaction. No network or secrets leave here. */
    public void claimed(WorkTask task,Worker worker) {
        if(!canClaim(task,worker))throw new SecurityException("MODEL_CREDENTIAL_SCOPE_DENIED");
        var row=forWorker(worker);if(row==null)return;
        requireWorkerProof(workerMapping(worker.id().value(),worker.runtimeType(),worker.capabilities()));
        if(Set.of("flexible-plan","configured-plan").contains(task.subjectType())) {
            repo.jdbc().update("INSERT INTO model_auth_plan VALUES(?,?,?,?) ON CONFLICT(subject_type,subject_id) DO NOTHING",task.subjectType(),task.subjectId(),row.get("id"),number(row,"version"));
            if(!canClaim(task,worker))throw new SecurityException("MODEL_PLAN_CONNECTION_CHANGED");
        }
        var fixed=repo.jdbc().queryForList("SELECT model FROM model_auth_worker WHERE worker_id=? AND connection_id=? AND connection_version=?",String.class,worker.id().value(),row.get("id"),number(row,"version"));
        if(fixed.size()!=1)throw new SecurityException("MODEL_WORKER_BINDING_REQUIRED");
        repo.jdbc().update("INSERT INTO model_auth_task VALUES(?,?,?,?,?,?) ON CONFLICT(task_id) DO NOTHING",task.id().value(),row.get("id"),number(row,"version"),row.get("owner_actor"),runtime(row),fixed.getFirst());
    }
    public void replaced(me.asu.aidev.work.domain.TaskId source,me.asu.aidev.work.domain.TaskId successor) {
        repo.jdbc().update("INSERT INTO model_auth_task SELECT ?,connection_id,connection_version,owner_actor,runtime,model FROM model_auth_task WHERE task_id=? ON CONFLICT(task_id) DO NOTHING",successor.value(),source.value());
        var old=repo.jdbc().queryForList("SELECT * FROM model_auth_task WHERE task_id=?",source.value());
        if(!old.isEmpty()) {
            var next=repo.jdbc().queryForMap("SELECT * FROM model_auth_task WHERE task_id=?",successor.value());var expected=new LinkedHashMap<>(old.getFirst());expected.put("task_id",successor.value());
            if(!expected.equals(next))throw new SecurityException("MODEL_SUCCESSOR_CONNECTION_CHANGED");
        }
    }
    public AccessToken access(String task,Access input,Worker worker) {
        if(workerToken==null||workerToken.isBlank())throw new SecurityException("MODEL_AUTH_WORKER_AUTHENTICATION_REQUIRED");
        var registered=workerMapping(worker.id().value(),worker.runtimeType(),worker.capabilities());requireWorkerProof(registered);
        guard.verifyLease(task,input.lease(),true,true);
        var binding=repo.jdbc().queryForMap("SELECT * FROM model_auth_task WHERE task_id=?",task);
        var row=owned(input.connectionId(),input.connectionVersion(),(String)binding.get("owner_actor"));
        if(!input.connectionId().equals(binding.get("connection_id"))||input.connectionVersion()!=number(binding,"connection_version")
                ||!input.connectionId().equals(registered.get("connection_id"))||input.connectionVersion()!=number(registered,"connection_version")
                ||!worker.capabilities().contains(capability(input.connectionId(),input.connectionVersion()))
                ||!worker.id().value().equals(input.lease().workerId())||!worker.runtimeType().startsWith(runtime(row)+":"))throw new SecurityException("MODEL_CREDENTIAL_SCOPE_DENIED");
        if(input.model()==null||!input.model().matches("[A-Za-z0-9._-]{1,120}"))throw new IllegalArgumentException("MODEL_REQUIRED");
        String selected=repo.jdbc().queryForObject("SELECT ai_model FROM execution_attempt WHERE id=?",String.class,input.lease().attemptId());
        if(selected==null||selected.isBlank())selected=(String)binding.get("model");
        if(!input.model().equals(selected))throw new SecurityException("MODEL_CONFIGURATION_CHANGED");
        row=refresh(row,input.rejectedGeneration()!=null,input.rejectedGeneration());
        guard.verifyLease(task,input.lease(),true,true); // Refresh must not outlive cancellation or revocation.
        var current=owned(input.connectionId(),input.connectionVersion(),(String)binding.get("owner_actor"));
        if(!"READY".equals(current.get("state"))||number(current,"generation")!=number(row,"generation"))throw new IllegalStateException("MODEL_CONNECTION_CHANGED");
        return new AccessToken(secrets(row).accessToken(),number(row,"expires_ms"),number(row,"generation"),Kind.valueOf((String)row.get("kind")),(String)row.get("account_id"),(String)row.get("plan_type"));
    }
    static long number(Map<String,Object> row,String key) {return row.get(key)==null?0:((Number)row.get(key)).longValue();}
}
