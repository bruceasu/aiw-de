package me.asu.aidev.modelauth;

import com.sun.net.httpserver.*;
import jakarta.servlet.http.HttpSession;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Service;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

/** One short-lived loopback listener per explicit local browser login. */
@Service
public class OpenAiLogin {
    private static final class Pending {
        String id,actor,sessionId,label,client,state,nonce,verifier,redirect,authority;
        HttpSession session;HttpServer server;Map<String,Object> prior;
        long expires;volatile String status="PENDING",connection;AtomicBoolean consumed=new AtomicBoolean();
    }
    private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
    private final ModelConnections connections;private final CredentialVault vault;private final OpenAiOAuth oauth;private final WebConsoleSecurityService security;
    public OpenAiLogin(ModelConnections connections,CredentialVault vault,OpenAiOAuth oauth,WebConsoleSecurityService security) {
        this.connections=connections;this.vault=vault;this.oauth=oauth;this.security=security;
    }
    private static String random() {var data=new byte[32];new SecureRandom().nextBytes(data);return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
    public Object start(Login input,HttpSession session) {
        String actor=connections.actor();if(!vault.ready())throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
        if(input.label()==null||input.label().isBlank()||input.label().length()>160)throw new IllegalArgumentException("MODEL_CONNECTION_LABEL_REQUIRED");
        // Bounded pending listeners; the system browser is opened by the console, never by a server subprocess.
        if(pending.values().stream().filter(p->p.status.equals("PENDING")||p.status.equals("PROCESSING")).count()>=4)throw new IllegalStateException("MODEL_LOGIN_LIMIT");
        var p=new Pending();p.id="ML-"+UUID.randomUUID();p.actor=actor;p.session=session;p.sessionId=session.getId();p.label=input.label();
        p.authority=security.currentDelegationAuthority(actor,"A17_CONFIGURATION");
        if(p.authority==null||p.authority.isBlank())throw new SecurityException("MODEL_LOGIN_CURRENT_ADMIN_REQUIRED");
        if(input.connectionId()!=null) {
            p.prior=connections.owned(input.connectionId(),Objects.requireNonNull(input.expectedVersion()),actor);
            if(!Kind.CHATGPT_RESPONSES.name().equals(p.prior.get("kind")))throw new IllegalArgumentException("CODEX_IMPORT_REQUIRES_ITS_ORIGINAL_REGISTRATION");
        }
        p.client=p.prior==null?"dynamic_agent_client":(String)p.prior.get("client_id");
        p.state=random();p.nonce=random();p.verifier=random();p.expires=System.currentTimeMillis()+180000;
        try {
            p.server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
            p.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            p.redirect="http://127.0.0.1:"+p.server.getAddress().getPort()+"/auth/callback";
            p.server.createContext("/auth/callback",exchange->callback(p,exchange));p.server.start();pending.put(p.id,p);
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(p.verifier.getBytes(StandardCharsets.US_ASCII)));
            return Map.of("loginId",p.id,"authorizationUrl",oauth.authorize(p.client,connections.host(),p.redirect,p.state,p.nonce,challenge),"expiresAt",p.expires);
        }catch(Exception error) {if(p.server!=null)p.server.stop(0);pending.remove(p.id);throw new IllegalStateException("MODEL_LOCAL_CALLBACK_UNAVAILABLE");}
    }
    private boolean authenticated(Pending p) {
        try {return System.currentTimeMillis()<p.expires&&security.signedIn(p.session)&&p.actor.equals(security.actor(p.session))
                &&p.authority.equals(security.currentDelegationAuthority(p.actor,"A17_CONFIGURATION"));}
        catch(IllegalStateException error) {return false;}
    }
    private void callback(Pending p,HttpExchange exchange) throws java.io.IOException {
        String result="登录未完成，请返回 AIW 查看状态。";int status=400;
        try {
            if(!exchange.getRequestMethod().equals("GET")||!exchange.getRequestURI().getPath().equals("/auth/callback")
                    ||!exchange.getRemoteAddress().getAddress().isLoopbackAddress()
                    ||!Objects.equals(exchange.getRequestHeaders().getFirst("Host"),"127.0.0.1:"+p.server.getAddress().getPort()))throw new SecurityException();
            var query=parse(exchange.getRequestURI().getRawQuery());
            if(!MessageDigest.isEqual(p.state.getBytes(StandardCharsets.UTF_8),query.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new SecurityException();
            synchronized(p) {
                if(!authenticated(p)||!p.consumed.compareAndSet(false,true)||!p.status.equals("PENDING"))throw new SecurityException();
                p.status="PROCESSING";
            }
            if(query.containsKey("error"))throw new IllegalArgumentException();
            String client=query.getOrDefault("client_id",p.client);
            if(p.client.equals("dynamic_agent_client")?client.equals("dynamic_agent_client")||client.isBlank():!p.client.equals(client))throw new SecurityException();
            String code=query.get("code");if(code==null||code.isBlank())throw new IllegalArgumentException();
            var issued=oauth.exchange(client,p.redirect,code,p.verifier,p.nonce);
            synchronized(p) {
                if(!authenticated(p)||!p.status.equals("PROCESSING"))throw new SecurityException();
                var saved=connections.save(p.actor,Kind.CHATGPT_RESPONSES,p.label,issued,p.prior);
                p.connection=saved.id();p.status="COMPLETED";
            }
            status=200;result="已连接 ChatGPT，可以关闭此窗口并返回 AIW。";
        }catch(Exception error) {if(p.consumed.get()&&!p.status.equals("CANCELLED"))p.status="FAILED";}
        finally {
            exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'");
            byte[] body=result.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,body.length);try(var output=exchange.getResponseBody()){output.write(body);}
            if(p.consumed.get()) {p.verifier=null;p.nonce=null;p.server.stop(0);}
        }
    }
    static Map<String,String> parse(String raw) {
        if(raw==null||raw.length()>8192)throw new IllegalArgumentException();var result=new HashMap<String,String>();
        for(var pair:raw.split("&")) {var parts=pair.split("=",2);String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8),value=parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"";
            if(result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException();}
        return result;
    }
    public Object status(String id,HttpSession session,boolean cancel) {
        var p=pending.get(id);String actor=connections.actor();
        if(p==null||!p.actor.equals(actor)||!p.sessionId.equals(session.getId()))throw new SecurityException("MODEL_LOGIN_SCOPE_DENIED");
        synchronized(p) {
            if(cancel&&(p.status.equals("PENDING")||p.status.equals("PROCESSING"))) {p.status="CANCELLED";p.consumed.set(true);p.server.stop(0);p.verifier=null;p.nonce=null;}
            return Map.of("status",p.status,"connectionId",Objects.toString(p.connection,""));
        }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=15000) public void expire() {
        for(var p:pending.values())synchronized(p) {
            if(p.expires<System.currentTimeMillis()) {if(p.status.equals("PENDING")||p.status.equals("PROCESSING"))p.status="EXPIRED";p.consumed.set(true);p.server.stop(0);p.verifier=null;p.nonce=null;}
            if(p.expires+300000<System.currentTimeMillis())pending.remove(p.id,p);
        }
    }
    @jakarta.annotation.PreDestroy public void close() {for(var p:pending.values())p.server.stop(0);pending.clear();}
}
