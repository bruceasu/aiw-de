package me.asu.aidev.webconsole.security;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
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

    private final boolean enabled;
    private final String configuredUsername;
    private final String configuredPassword;
    private final WebConsoleRole configuredRole;

    public WebConsoleSecurityService(
            @Value("${aidev.web-console.auth.enabled:false}") boolean enabled,
            @Value("${aidev.web-console.auth.username:admin}") String username,
            @Value("${aidev.web-console.auth.password:}") String password,
            @Value("${aidev.web-console.auth.role:ADMIN}") String role) {
        this.enabled = enabled;
        this.configuredUsername = normalize(username, "username");
        this.configuredPassword = password == null ? "" : password;
        try {
            this.configuredRole = WebConsoleRole.valueOf(normalize(role, "role").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid Web Console role; expected VIEWER, OPERATOR, APPROVER or ADMIN", e);
        }
    }

    /** Whether otherwise-local console routes require sign-in; the login page is always available. */
    public boolean enabled() {
        return enabled;
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
        session.setAttribute(SESSION_ROLE, configuredRole.name());
        ensureCsrf(session);
    }

    public boolean authenticated(HttpSession session) {
        return !enabled || signedIn(session);
    }

    public boolean signedIn(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTHENTICATED));
    }

    public String username(HttpSession session) {
        if (!enabled && !signedIn(session)) return "local-console";
        Object value = session.getAttribute(SESSION_USERNAME);
        return value == null ? configuredUsername : value.toString();
    }

    public WebConsoleRole role(HttpSession session) {
        if (!enabled && !signedIn(session)) return WebConsoleRole.ADMIN;
        Object value = session.getAttribute(SESSION_ROLE);
        if (value == null) return configuredRole;
        return WebConsoleRole.valueOf(value.toString());
    }

    public String actor(HttpSession session) {
        return "web:" + username(session);
    }

    /** Re-evaluate durable human delegations against current configuration, not an old session. */
    public String currentOperatorAuthority(String actor) {
        boolean identified = ("web:" + configuredUsername).equals(actor);
        if (identified) {
            if (!loginConfigured() || !configuredRole.canOperate()) return "";
        } else if (enabled || !"web:local-console".equals(actor)) return "";
        try {
            String source = identified + "\n" + configuredUsername + "\n" + configuredRole;
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** A grant cannot retain a role that its owner no longer has. */
    public String currentDelegationAuthority(String actor, String action) {
        String expected = "web:" + configuredUsername;
        if (!loginConfigured() || !expected.equals(actor)) return "";
        WebConsoleRole current = configuredRole;
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
