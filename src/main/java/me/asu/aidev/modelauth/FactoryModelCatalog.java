package me.asu.aidev.modelauth;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.config.AgentFactorySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

/** Read-only model metadata. Never creates a Task, starts a model turn or returns credentials. */
@Service
public class FactoryModelCatalog {
    public record Query(String runtime,String mode,String credentialRef,Long credentialVersion,boolean force) {}
    public record Model(String id,String name,List<String> efforts,String defaultEffort) {}
    public record Catalog(List<Model> models,String source,String notice,long fetchedAt,boolean cached) {}
    private final ModelConnections connections;private final OpenAiOAuth oauth;private final AgentFactorySettings settings;private final AuthorityRepository repo;
    private final Semaphore probes=new Semaphore(2);
    private final Map<String,Catalog> cache=new LinkedHashMap<>();
    @Value("${aidev.agent-factory.worker-root:workers/process-python}") private String workerRoot;
    public FactoryModelCatalog(ModelConnections connections,OpenAiOAuth oauth,AgentFactorySettings settings,AuthorityRepository repo) {
        this.connections=connections;this.oauth=oauth;this.settings=settings;this.repo=repo;
    }
    String environment(String name) {return System.getenv(name);}
    public Object context() {
        connections.actor();
        return Map.of("apiKeyConfigured",!Objects.toString(environment("OPENAI_API_KEY"),"").isBlank(),
                "customEndpoint",!Objects.toString(environment("OPENAI_BASE_URL"),"").isBlank(),"scope","PLANE_LOCAL_ENVIRONMENT");
    }
    public Catalog models(Query input) {
        String actor=connections.actor();validate(input);
        var chosen=input.credentialRef()==null?null:new Selection(input.credentialRef(),Objects.requireNonNull(input.credentialVersion()));
        if(chosen!=null)connections.selection(chosen,input.runtime());
        var snapshot=settings.snapshot();var config=snapshot.settings();
        String key=AuthorityRepository.hash(actor+"\n"+input.runtime()+"\n"+input.mode()+"\n"+Objects.toString(input.credentialRef(),"")+":"+input.credentialVersion()+"\n"+snapshot.version());
        synchronized(cache) {
            var known=cache.get(key);
            if(!input.force()&&known!=null&&known.fetchedAt()+60000>System.currentTimeMillis())return new Catalog(known.models(),known.source(),known.notice(),known.fetchedAt(),true);
        }
        if(!probes.tryAcquire())throw new IllegalStateException("MODEL_DIRECTORY_BUSY");
        try {
            JsonNode raw;String source,notice;
            if(input.runtime().equals("codex-app-server")) {
                var token=chosen==null?null:connections.catalogAccess(chosen,input.runtime());
                raw=codex(config,token);source="CODEX_RPC";notice="CODEX_CATALOG_MAY_BE_CACHED";
            } else if(chosen!=null) {
                var token=connections.catalogAccess(chosen,input.runtime());
                raw=oauth.models(token.accessToken(),OpenAiOAuth.RESOURCE);source="CHATGPT_ACCOUNT";notice="DIRECTORY_IS_NOT_INFERENCE_VERIFICATION";
            } else {
                String token=Objects.toString(environment("OPENAI_API_KEY"),"");
                if(token.isBlank())throw new IllegalStateException("MODEL_DIRECTORY_API_KEY_NOT_CONFIGURED");
                String endpoint=Objects.toString(environment("OPENAI_BASE_URL"),"");
                raw=oauth.models(token,endpoint.isBlank()?OpenAiOAuth.RESOURCE:endpoint);source="OPENAI_API_ACCOUNT";notice="API_DIRECTORY_INCLUDES_OTHER_MODEL_TYPES";
            }
            var result=new Catalog(parse(raw,source),source,notice,System.currentTimeMillis(),false);
            // A disconnection or account replacement during the request invalidates late metadata too.
            if(chosen!=null)connections.selection(chosen,input.runtime());
            if(!Objects.equals(snapshot.version(),settings.version()))throw new IllegalStateException("MODEL_DIRECTORY_SOURCE_CHANGED");
            synchronized(cache) {if(cache.size()>=64)cache.remove(cache.keySet().iterator().next());cache.put(key,result);}
            return result;
        } finally {probes.release();}
    }
    static void validate(Query query) {
        if(query==null||query.runtime()==null||query.mode()==null)throw new IllegalArgumentException("MODEL_DIRECTORY_SELECTION_REQUIRED");
        boolean codex=query.runtime().equals("codex-app-server"),python=query.runtime().equals("openai-agents");
        if(!(codex&&Set.of("codex-local","codex-plane").contains(query.mode())||python&&Set.of("chatgpt","api-key").contains(query.mode())))throw new IllegalArgumentException("MODEL_DIRECTORY_MODE_MISMATCH");
        boolean plane=Set.of("codex-plane","chatgpt").contains(query.mode());
        if(plane&&(query.credentialRef()==null||query.credentialVersion()==null||query.credentialVersion()<1)
                ||!plane&&(query.credentialRef()!=null||query.credentialVersion()!=null))throw new IllegalArgumentException("MODEL_DIRECTORY_CREDENTIAL_REQUIRED");
    }
    static List<Model> parse(JsonNode raw,String source) {
        if(raw==null)throw new IllegalStateException("MODEL_DIRECTORY_FORMAT_UNSUPPORTED");
        boolean chatgpt=source.equals("CHATGPT_ACCOUNT"),codex=source.equals("CODEX_RPC");
        var rows=raw.path(chatgpt?"models":"data");if(!rows.isArray())throw new IllegalStateException("MODEL_DIRECTORY_FORMAT_UNSUPPORTED");
        var models=new LinkedHashMap<String,Model>();
        for(var row:rows) {
            if(chatgpt&&!row.path("visibility").asText().equals("list")||codex&&row.path("hidden").asBoolean(false))continue;
            String id=row.path(chatgpt?"slug":codex?"model":"id").asText();
            if(!id.matches("[A-Za-z0-9._-]{1,120}"))continue; // Match the existing Worker/Settings model contract.
            String name=row.path(chatgpt?"display_name":"displayName").asText(id);if(name.isBlank())name=id;if(name.length()>256)name=name.substring(0,256);
            var efforts=new ArrayList<String>();
            if(codex&&row.path("supportedReasoningEfforts").isArray())for(var item:row.path("supportedReasoningEfforts")) {
                String effort=item.path("reasoningEffort").asText();if(Set.of("none","minimal","low","medium","high","xhigh").contains(effort)&&!efforts.contains(effort))efforts.add(effort);
            }
            String fallback=row.path("defaultReasoningEffort").asText("");
            models.putIfAbsent(id,new Model(id,name,List.copyOf(efforts),efforts.contains(fallback)?fallback:null));
            if(models.size()>=500)break;
        }
        return List.copyOf(models.values());
    }
    private JsonNode codex(AgentFactorySettings.Settings config,AccessToken token) {
        var root=Path.of(workerRoot).toAbsolutePath().normalize();var helper=root.resolve("model_catalog.py");
        if(!Files.isRegularFile(helper,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("MODEL_DIRECTORY_HELPER_NOT_FOUND");
        String executable=config==null||config.executable().isBlank()?Objects.toString(environment("AIW_CODEX_PATH"),"codex"):config.executable();
        if(executable.isBlank())executable="codex";
        var args=new ArrayList<>(List.of(config==null?"python":config.python(),helper.toString(),"--codex",executable));
        if(config!=null&&!config.guardHome().isBlank()&&token==null)args.addAll(List.of("--guard-home",config.guardHome()));
        var builder=new ProcessBuilder(args).directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(name->name.startsWith("AIW_")||name.startsWith("AIDEV_")||name.startsWith("SPRING_")||Set.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","JAVA_OPTS","MAVEN_OPTS").contains(name));
        if(token!=null) {builder.environment().remove("OPENAI_API_KEY");builder.environment().remove("OPENAI_BASE_URL");}
        Process process=null;
        try {
            process=builder.start();var running=process;
            var output=new CompletableFuture<byte[]>();
            Thread.ofVirtual().start(()->{try(var stream=running.getInputStream()){output.complete(stream.readNBytes(131073));}catch(Exception error){output.completeExceptionally(error);}});
            // Only the access token crosses stdin. No credential is put in argv, environment or a file.
            try(var input=process.getOutputStream()) {input.write(repo.encode(token==null?Map.of():token).getBytes(StandardCharsets.UTF_8));}
            if(!process.waitFor(35,TimeUnit.SECONDS))throw new IllegalStateException("MODEL_DIRECTORY_TIMEOUT");
            byte[] bytes=output.get(2,TimeUnit.SECONDS);if(bytes.length>131072)throw new IllegalStateException("MODEL_DIRECTORY_LIMIT");
            var result=repo.decode(new String(bytes,StandardCharsets.UTF_8),JsonNode.class);
            if(process.exitValue()!=0) {
                String code=result.path("code").asText();
                throw new IllegalStateException(Set.of("MODEL_DIRECTORY_LOGIN_REQUIRED","MODEL_DIRECTORY_CODEX_UNAVAILABLE","MODEL_DIRECTORY_TIMEOUT").contains(code)?code:"MODEL_DIRECTORY_PROBE_FAILED");
            }
            return result;
        }catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_DIRECTORY_INTERRUPTED");}
        catch(java.io.IOException|ExecutionException|TimeoutException error) {throw new IllegalStateException("MODEL_DIRECTORY_PROBE_FAILED");}
        finally {
            if(process!=null&&process.isAlive()) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}
        }
    }
}
