package me.asu.aidev.modelauth;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import me.asu.aidev.execution.config.AgentFactorySettings;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

/** Local script bridge. Python owns provider protocols and protected token files. */
@Component
public class OpenAiOAuth {
    private final ObjectMapper json=new ObjectMapper();
    private final AgentFactorySettings settings;
    @Value("${aidev.agent-factory.worker-root:workers/process-python}") private String workerRoot="workers/process-python";
    @Value("${aidev.model-auth.directory:${user.home}/.aiw/model-credentials}") private String directory;
    public OpenAiOAuth(AgentFactorySettings settings){this.settings=settings;}
    JsonNode call(Map<String,Object> request){
        Process process=null;
        try{
            var input=new LinkedHashMap<>(request);input.put("directory",Path.of(directory).toAbsolutePath().normalize().toString());
            var config=settings.current();String python=config==null?"python":config.python();
            process=new ProcessBuilder(python,"-I",Path.of(workerRoot).toAbsolutePath().resolve("model_auth_protocol.py").toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var child=process;var output=new CompletableFuture<byte[]>();
            Thread.ofVirtual().start(()->{try{output.complete(child.getInputStream().readNBytes(262145));}catch(Exception e){output.completeExceptionally(e);}});
            try(var stream=process.getOutputStream()){stream.write(json.writeValueAsBytes(input));}
            if(!process.waitFor(55,TimeUnit.SECONDS))throw new IllegalStateException("MODEL_AUTH_PROTOCOL_TIMEOUT");
            byte[] bytes=output.get(1,TimeUnit.SECONDS);if(bytes.length>262144)throw new IllegalStateException("MODEL_AUTH_RESPONSE_LIMIT");
            var result=json.readTree(bytes);
            if(result==null||result.has("error")||process.exitValue()!=0){
                String code=result==null?"":result.path("error").asText();
                throw new IllegalStateException(code.matches("(?:MODEL|CODEX|ISSUED)_[A-Z0-9_]{1,100}")?code:"MODEL_AUTH_PROTOCOL_FAILED");
            }
            if(!result.has("result"))throw new IllegalStateException("MODEL_AUTH_PROTOCOL_RESPONSE_INVALID");
            return result.get("result");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_AUTH_OUTCOME_UNKNOWN");}
        catch(IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalStateException("MODEL_AUTH_PROTOCOL_UNAVAILABLE");}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    private Issued issued(Map<String,Object> request){return json.convertValue(call(request),Issued.class);}
    public String authorize(String client,String host,String redirect,String state,String nonce,String challenge){
        return call(Map.of("operation","authorize","client",client,"host",host,"redirect",redirect,"state",state,"nonce",nonce,"challenge",challenge)).path("url").asText();
    }
    public Issued exchange(String client,String redirect,String code,String verifier,String nonce){
        return issued(Map.of("operation","exchange","client",client,"redirect",redirect,"code",code,"verifier",verifier,"nonce",nonce));
    }
    public Issued importTokens(JsonNode source){return issued(Map.of("operation","import","source",source));}
    public Issued refresh(Kind kind,String client,String subject,String file){return issued(Map.of("operation","refresh","kind",kind.name(),"clientId",client,"subject",subject,"credentialFile",file));}
    public String access(String file){return call(Map.of("operation","access","credentialFile",file)).path("accessToken").asText();}
    public JsonNode models(String access,String base){return call(Map.of("operation","models","access",access,"base",base));}
    public boolean revoke(String file){try{return call(Map.of("operation","revoke","credentialFile",file)).path("confirmed").asBoolean();}catch(RuntimeException e){return false;}}
    public void discard(String file){call(Map.of("operation","discard","credentialFile",file));}
}
