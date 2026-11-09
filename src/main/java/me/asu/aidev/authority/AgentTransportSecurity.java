package me.asu.aidev.authority;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Enforces the transport profile independently from the bearer credential. */
@Component
public class AgentTransportSecurity {
    private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    private final AuthorityRepository repo;
    private final boolean localProfileEnabled;

    public AgentTransportSecurity(AuthorityRepository repo, @Value("${aiw.agent.local-profile-enabled:true}") boolean localProfileEnabled) {
        this.repo = repo;
        this.localProfileEnabled = localProfileEnabled;
    }

    public String pairingFingerprint(HttpServletRequest request, String profile) {
        if ("LOCAL".equals(profile)) {
            requireLocalProfileEnabled();
            requireLoopback(request);
            return null;
        }
        if (!"REMOTE".equals(profile)) throw new SecurityException("Unsupported Agent transport profile");
        return certificateFingerprint(request);
    }

    /** Unauthenticated enrollment is restricted to a native direct-loopback request, never a browser or proxy. */
    public void requireLocalEnrollment(HttpServletRequest request) {
        requireLocalProfileEnabled();
        requireLoopback(request);
        if (request.getHeader("Origin") != null || request.getHeader("Sec-Fetch-Site") != null)
            throw new SecurityException("Local Agent enrollment accepts direct native requests only");
    }

    public String certificateFingerprint(HttpServletRequest request) {
        if (!request.isSecure()) throw new SecurityException("Remote Agent requests require TLS");
        Object value = request.getAttribute(CERTIFICATE_ATTRIBUTE);
        if (!(value instanceof X509Certificate[] chain) || chain.length == 0 || chain[0] == null)
            throw new SecurityException("Trusted Agent client certificate required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
            return HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException error) {
            throw new SecurityException("Unable to verify Agent client certificate", error);
        }
    }

    /** Rotation requests still need a CA-trusted certificate, but its pin is pending human approval. */
    public String authenticate(HttpServletRequest request, String agentId, boolean rotationRequest) {
        List<java.util.Map<String, Object>> rows = repo.jdbc().queryForList(
                "SELECT connection_profile,client_cert_sha256 FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", agentId);
        if (rows.size() != 1) throw new SecurityException("Unknown Agent transport identity");
        var row = rows.getFirst();
        if ("LOCAL".equals(row.get("connection_profile"))) {
            requireLocalProfileEnabled();
            requireLoopback(request);
            return null;
        }
        if (!"REMOTE".equals(row.get("connection_profile"))) throw new SecurityException("Unsupported Agent transport profile");
        String observed = certificateFingerprint(request);
        if (!rotationRequest && !MessageDigest.isEqual(
                observed.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                String.valueOf(row.get("client_cert_sha256")).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new SecurityException("Agent client certificate does not match the approved pin");
        return observed;
    }

    private static void requireLoopback(HttpServletRequest request) {
        try {
            for (String header : List.of("Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto",
                    "X-Forwarded-Port", "X-Forwarded-Prefix", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "Via")) {
                if (request.getHeader(header) != null)
                    throw new SecurityException("Local Agent profile cannot be used through a forwarding proxy");
            }
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress())
                throw new SecurityException("Local Agent profile accepts loopback connections only");
        } catch (java.net.UnknownHostException error) {
            throw new SecurityException("Invalid Agent remote address", error);
        }
    }

    private void requireLocalProfileEnabled() {
        if (!localProfileEnabled) throw new SecurityException("Local Agent profiles are disabled on this Control Plane listener");
    }
}
