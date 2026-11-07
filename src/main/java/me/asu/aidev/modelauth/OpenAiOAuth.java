package me.asu.aidev.modelauth;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import org.springframework.stereotype.Component;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

/** Fixed-origin OAuth/OIDC client. No redirects, token logging, model calls or automatic retries. */
@Component
public class OpenAiOAuth {
    static final String ISSUER="https://auth.openai.com", RESOURCE="https://api.openai.com/v1";
    // Codex's public registration is deliberately distinct from issued dynamic clients.
    static final String CODEX_CLIENT="app_EMoamEEZ73f0CkXaXp7hrann";
    static final String SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile JWKSet keys;private volatile long keysUntil;
    static String form(Map<String,String> values) {
        return values.entrySet().stream().map(e->escape(e.getKey())+"="+escape(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
    }
    static String escape(String value) {return java.net.URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public String authorize(String client,String host,String redirect,String state,String nonce,String challenge) {
        var params=new LinkedHashMap<String,String>();params.put("client_id",client);params.put("ext_agent_host_id",host);
        if(client.equals("dynamic_agent_client"))params.put("agent_name_hint","AIW DE");
        params.put("response_type","code");params.put("redirect_uri",redirect);params.put("scope",SCOPES);
        params.put("resource",RESOURCE);params.put("state",state);params.put("nonce",nonce);
        params.put("code_challenge_method","S256");params.put("code_challenge",challenge);
        return ISSUER+"/api/accounts/authorize?"+form(params);
    }
    public Issued exchange(String client,String redirect,String code,String verifier,String nonce) {
        var response=token(Kind.CHATGPT_RESPONSES,Map.of("grant_type","authorization_code","client_id",client,"redirect_uri",redirect,
                "code",code,"code_verifier",verifier,"resource",RESOURCE));
        return validate(response,Kind.CHATGPT_RESPONSES,client,nonce,null);
    }
    public Issued refresh(Kind kind,String client,String subject,TokenSet old) {
        var params=new LinkedHashMap<String,String>();params.put("grant_type","refresh_token");params.put("client_id",client);params.put("refresh_token",old.refreshToken());
        if(kind==Kind.CHATGPT_RESPONSES)params.put("resource",RESOURCE);
        var response=token(kind,params);
        if(!response.hasNonNull("scope"))((com.fasterxml.jackson.databind.node.ObjectNode)response).put("scope",old.scope());
        return validate(response,kind,client,null,subject);
    }
    private JsonNode token(Kind kind,Map<String,String> params) {
        var url=ISSUER+(kind==Kind.CHATGPT_RESPONSES?"/api/accounts/oauth/token":"/oauth/token");
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params))).build();
        return request(request);
    }
    public JsonNode models(String access,String base) {
        var uri=URI.create(base);
        boolean local=Set.of("127.0.0.1","localhost","::1","[::1]").contains(Objects.toString(uri.getHost(),""));
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                ||!("https".equals(uri.getScheme())||local&&"http".equals(uri.getScheme())))throw new IllegalArgumentException("MODEL_DIRECTORY_ENDPOINT_INVALID");
        String endpoint=base.replaceFirst("/+$","")+"/models";
        return request(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+access).header("Accept","application/json").GET().build());
    }
    public boolean revoke(String client,TokenSet tokens) {
        try {
            var discovery=request(HttpRequest.newBuilder(URI.create(ISSUER+"/.well-known/openid-configuration")).timeout(Duration.ofSeconds(8)).GET().build());
            if(!ISSUER.equals(discovery.path("issuer").asText()))return false;
            var uri=URI.create(required(discovery,"revocation_endpoint"));
            if(!"https".equals(uri.getScheme())||!"auth.openai.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null)return false;
            var result=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("client_id",client,"token",tokens.refreshToken(),"token_type_hint","refresh_token")))).build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=result.body()) {return result.statusCode()==200;}
        }catch(InterruptedException error) {Thread.currentThread().interrupt();return false;}
        catch(Exception error) {return false;}
    }
    /** Imported access and ID tokens must both be valid, signed and belong to the same client/account. */
    public Issued importTokens(JsonNode source) {
        var codex=source.has("tokens");var value=codex?source.path("tokens"):source;
        var client=codex?CODEX_CLIENT:required(source,"client_id");
        if(!codex&&!source.path("issuer").asText().equals(ISSUER))throw new IllegalArgumentException("MODEL_IMPORT_ISSUER_MISMATCH");
        var token=json.createObjectNode();for(var name:List.of("access_token","refresh_token","id_token"))token.put(name,required(value,name));
        token.put("token_type","Bearer");
        var claims=signed(required(value,"access_token"),RESOURCE);
        token.put("scope",Objects.toString(claims.getClaim("scope"),""));
        long expiry=claims.getExpirationTime().getTime();token.put("expires_in",Math.max(1,(expiry-System.currentTimeMillis())/1000));
        return validate(token,codex?Kind.CODEX_APP_SERVER:Kind.CHATGPT_RESPONSES,client,null,null);
    }
    Issued validate(JsonNode response,Kind kind,String client,String nonce,String expectedSubject) {
        if(client==null||client.length()>256||client.equals("dynamic_agent_client")||client.isBlank())throw new IllegalArgumentException("ISSUED_CLIENT_REQUIRED");
        String access=required(response,"access_token"),refresh=required(response,"refresh_token"),id=required(response,"id_token");
        if(!"Bearer".equalsIgnoreCase(required(response,"token_type")))throw new IllegalArgumentException("MODEL_TOKEN_TYPE_INVALID");
        var identity=signed(id,client);var bearer=signed(access,RESOURCE);
        if(nonce!=null&&!nonce.equals(identity.getClaim("nonce")))throw new SecurityException("MODEL_LOGIN_NONCE_MISMATCH");
        String subject=identity.getSubject();
        if(subject==null||subject.isBlank()||!subject.equals(bearer.getSubject())||!client.equals(bearer.getClaim("client_id"))
                ||expectedSubject!=null&&!expectedSubject.equals(subject))throw new SecurityException("MODEL_ACCOUNT_OR_CLIENT_CHANGED");
        String scopes=required(response,"scope");var scopeSet=Set.of(scopes.strip().split("\\s+"));
        var claimScopes=new HashSet<>(Arrays.asList(Objects.toString(bearer.getClaim("scope"),"").strip().split("\\s+")));
        if(!claimScopes.containsAll(scopeSet))throw new SecurityException("MODEL_TOKEN_SCOPE_MISMATCH");
        String account=null,plan=null;
        if(kind==Kind.CODEX_APP_SERVER) {
            if(!CODEX_CLIENT.equals(client))throw new SecurityException("CODEX_CLIENT_REQUIRED");
            Object metadata=bearer.getClaim("https://api.openai.com/auth");
            if(!(metadata instanceof Map<?,?> map)||!(map.get("chatgpt_account_id") instanceof String text)||text.isBlank())throw new SecurityException("CODEX_ACCOUNT_REQUIRED");
            account=text;plan=Objects.toString(map.get("chatgpt_plan_type"),null);
        }
        var lifetime=response.path("expires_in");
        if(!lifetime.isIntegralNumber()||lifetime.asLong()<1||lifetime.asLong()>86400)throw new IllegalArgumentException("MODEL_TOKEN_EXPIRY_INVALID");
        long expires=Math.min(bearer.getExpirationTime().getTime(),System.currentTimeMillis()+lifetime.asLong()*1000);
        if(expires<=System.currentTimeMillis()+30000)throw new IllegalArgumentException("MODEL_IMPORT_EXPIRED_RECONNECT");
        long earliest=0;var refreshAt=response.get("earliest_refresh_at");
        if(refreshAt!=null&&!refreshAt.isNull()) {
            if(refreshAt.isIntegralNumber())earliest=Math.multiplyExact(refreshAt.longValue(),1000);
            else if(refreshAt.isTextual())try {earliest=java.time.Instant.parse(refreshAt.textValue()).toEpochMilli();}
                catch(java.time.format.DateTimeParseException error) {throw new IllegalArgumentException("MODEL_REFRESH_TIME_INVALID");}
            else throw new IllegalArgumentException("MODEL_REFRESH_TIME_INVALID");
        }
        return new Issued(new TokenSet(access,refresh,id,scopes),client,subject,account,plan,expires,earliest,
                kind==Kind.CODEX_APP_SERVER||scopeSet.containsAll(Set.of("resource.invoke","chatgpt.tokens.use.direct")));
    }
    private JWTClaimsSet signed(String token,String audience) {
        try {
            if(token.length()>32768)throw new IllegalArgumentException();
            var jwt=SignedJWT.parse(token);
            if(!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())||jwt.getHeader().getKeyID()==null||jwt.getHeader().getCriticalParams()!=null)throw new IllegalArgumentException();
            var key=jwks().getKeyByKeyId(jwt.getHeader().getKeyID());
            if(!(key instanceof RSAKey rsa)||rsa.size()<2048||!jwt.verify(new RSASSAVerifier(rsa.toRSAPublicKey())))throw new IllegalArgumentException();
            var c=jwt.getJWTClaimsSet();long now=System.currentTimeMillis();
            if(!ISSUER.equals(c.getIssuer())||!c.getAudience().contains(audience)||c.getExpirationTime()==null||c.getExpirationTime().getTime()<=now
                    ||c.getNotBeforeTime()!=null&&c.getNotBeforeTime().getTime()>now+30000
                    ||c.getIssueTime()==null||c.getIssueTime().getTime()>now+30000)throw new IllegalArgumentException();
            return c;
        }catch(Exception error) {throw new SecurityException("MODEL_TOKEN_SIGNATURE_OR_IDENTITY_INVALID");}
    }
    private synchronized JWKSet jwks() throws Exception {
        if(keys!=null&&keysUntil>System.currentTimeMillis())return keys;
        var discovery=request(HttpRequest.newBuilder(URI.create(ISSUER+"/.well-known/openid-configuration")).timeout(Duration.ofSeconds(8)).GET().build());
        if(!ISSUER.equals(discovery.path("issuer").asText()))throw new SecurityException();
        var uri=URI.create(required(discovery,"jwks_uri"));
        if(!"https".equals(uri.getScheme())||!"auth.openai.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null)throw new SecurityException();
        keys=JWKSet.parse(request(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build()).toString());keysUntil=System.currentTimeMillis()+300000;return keys;
    }
    JsonNode request(HttpRequest request) {
        try {
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                if(response.statusCode()!=200)throw new IllegalStateException("MODEL_AUTH_REMOTE_REJECTED_"+response.statusCode());
                var received=new java.util.concurrent.CompletableFuture<byte[]>();
                var reader=Thread.ofVirtual().start(()->{try {received.complete(body.readNBytes(131073));}catch(Exception error){received.completeExceptionally(error);}});
                byte[] bytes;
                try {bytes=received.get(12,java.util.concurrent.TimeUnit.SECONDS);}
                catch(Exception error) {reader.interrupt();if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_AUTH_OUTCOME_UNKNOWN");}
                if(bytes.length>131072)throw new IllegalStateException("MODEL_AUTH_RESPONSE_LIMIT");
                return json.readTree(bytes);
            }
        }catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_AUTH_OUTCOME_UNKNOWN");}
        catch(java.io.IOException error) {throw new IllegalStateException("MODEL_AUTH_OUTCOME_UNKNOWN");}
    }
    static String required(JsonNode node,String name) {
        var value=node.path(name);if(!value.isTextual()||value.asText().isBlank()||value.asText().length()>32768)throw new IllegalArgumentException("MODEL_AUTH_FIELD_REQUIRED");return value.asText();
    }
}
