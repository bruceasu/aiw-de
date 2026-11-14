package me.asu.aidev.webconsole.security;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Lightweight browser-console authentication and CSRF support without creating new persistent authority. */
@Service
public class WebConsoleSecurityService {
    public static final String SESSION_AUTHENTICATED = "aiw.web.authenticated";
    public static final String SESSION_USERNAME = "aiw.web.username";
    public static final String SESSION_ROLE = "aiw.web.role";
    public static final String SESSION_CSRF = "aiw.web.csrf";

    private final String configuredUsername;
    private final String configuredPassword;
    @Value("${server.address:}") private String bindAddress="";
    @Value("${aidev.remote-access:false}") private boolean remoteAccess;
    @Value("${server.forward-headers-strategy:none}") private String forwardedStrategy="none";

    public WebConsoleSecurityService(
            @Value("${aidev.web-console.auth.username:admin}") String username,
            @Value("${aidev.web-console.auth.password:}") String password) {
        this.configuredUsername = normalize(username, "username");
        this.configuredPassword = password == null ? "" : password;
    }

    public boolean localOnly() {return !remoteAccess&&"none".equalsIgnoreCase(forwardedStrategy)&&loopback(bindAddress);}
    public static boolean loopback(String value) {return java.util.Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(value==null?"":value);}
    public boolean localRequest(jakarta.servlet.http.HttpServletRequest request) {
        String host=request.getServerName();
        return localOnly()&&loopback(request.getRemoteAddr())&&(loopback(host)||"localhost".equalsIgnoreCase(host)||"[::1]".equals(host))
            &&request.getHeader("Forwarded")==null&&request.getHeader("X-Forwarded-For")==null&&request.getHeader("X-Forwarded-Host")==null&&request.getHeader("X-Forwarded-Proto")==null
            &&(request.getHeader("Origin")==null||request.getHeader("Origin").equals(request.getScheme()+"://"+request.getHeader("Host")));
    }
    public void localLogin(jakarta.servlet.http.HttpServletRequest request,HttpSession session) {
        if(!signedIn(session)&&localRequest(request))login(session);
    }
    @jakarta.annotation.PostConstruct public void validateDeployment() {
        if(!localOnly()&&!loginConfigured())throw new IllegalStateException("远程访问必须配置 AIW_WEB_CONSOLE_PASSWORD；仅本机自动信任需绑定 127.0.0.1 或 ::1。反向代理部署应设置 AIW_REMOTE_ACCESS=true。");
    }

    /** Both automatic local sessions and remote login keep authentication and CSRF. */
    public boolean enabled() {
        return true;
    }

    public boolean loginConfigured() {
        return !configuredPassword.isBlank();
    }

    public boolean authenticate(String username, String password) {
        if (!loginConfigured()) return false;
        byte[] expectedUser = configuredUsername.getBytes(StandardCharsets.UTF_8);
        byte[] actualUser = (username == null ? "" : username).getBytes(StandardCharsets.UTF_8);
        byte[] expectedPassword = configuredPassword.getBytes(StandardCharsets.UTF_8);
        byte[] actualPassword = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedUser, actualUser) && MessageDigest.isEqual(expectedPassword, actualPassword);
    }

    public void login(HttpSession session) {
        session.setAttribute(SESSION_AUTHENTICATED, Boolean.TRUE);
        session.setAttribute(SESSION_USERNAME, configuredUsername);
        session.setAttribute(SESSION_ROLE, WebConsoleRole.ADMIN.name());
        ensureCsrf(session);
    }

    public boolean authenticated(HttpSession session) {
        return signedIn(session);
    }

    public boolean signedIn(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTHENTICATED));
    }

    public String username(HttpSession session) {
        if (!signedIn(session)) return "";
        Object value = session.getAttribute(SESSION_USERNAME);
        return value == null ? configuredUsername : value.toString();
    }

    public WebConsoleRole role(HttpSession session) {
        return signedIn(session)?WebConsoleRole.ADMIN:WebConsoleRole.ANONYMOUS;
    }

    public String actor(HttpSession session) {
        if (!signedIn(session)) throw new SecurityException("Authenticated console actor required");
        return "web:" + username(session);
    }

    /** Local Agent enrollment uses the configured administrator identity without creating a browser session. */
    public String configuredAdministratorActor() {
        if (!localOnly()&&!loginConfigured())
            throw new SecurityException("Configured Control Plane administrator is required for local Agent enrollment");
        return "web:" + configuredUsername;
    }

    /** Re-evaluate durable human delegations against current configuration, not an old session. */
    public String currentOperatorAuthority(String actor) {
        boolean identified = ("web:" + configuredUsername).equals(actor);
        if (!identified || !localOnly()&&!loginConfigured()) return "";
        try {
            String source = identified + "\n" + configuredUsername + "\n" + WebConsoleRole.ADMIN;
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** A grant cannot retain a role that its owner no longer has. */
    public String currentDelegationAuthority(String actor, String action) {
        String expected = "web:" + configuredUsername;
        if (!expected.equals(actor)||!localOnly()&&!loginConfigured()) return "";
        WebConsoleRole current = WebConsoleRole.ADMIN;
        boolean allowed = action.startsWith("A11_") || action.equals("A18_BUDGET") ? current.canApprove()
                : action.startsWith("A17_") ? current.canAdmin() : current.canOperate();
        if (!allowed) return "";
        return me.asu.aidev.authority.AuthorityRepository.hash(true + "\n" + expected + "\n" + current + "\n" + action);
    }

    public String ensureCsrf(HttpSession session) {
        Object existing = session.getAttribute(SESSION_CSRF);
        if (existing != null) return existing.toString();
        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF, token);
        return token;
    }

    public boolean validCsrf(HttpSession session, String candidate) {
        if (candidate == null) return false;
        String expected = ensureCsrf(session);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    public void requireOperator(HttpSession session) {
        if (!role(session).canOperate()) throw new SecurityException("Web Console role does not allow operator actions");
    }

    public void requireAdmin(HttpSession session) {
        if (!role(session).canAdmin()) throw new SecurityException("Web Console role does not allow administrator actions");
    }

    public void requireApprover(HttpSession session) {
        if (!role(session).canApprove()) throw new SecurityException("Web Console role does not allow approval actions");
    }

    private static String normalize(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Web Console " + name + " is blank");
        return value.strip();
    }
}
