package me.asu.aidev.authority;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control Plane owned Agent pairing and one-step human approval of its exact initial Grant. */
@Service
public class AgentIdentityService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long INVITATION_TTL_MS = 5 * 60 * 1000;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final AgentTransportSecurity transport;

    public record InvitationInput(String profile, String serverOrigin) {}
    public record PairInput(String invitation) {}
    public record PairingApprovalInput(long generation, String agentBoot, ActionScope scope, long expiresAt) {}

    public AgentIdentityService(AuthorityRepository repo, AuthorityBoundary boundary, AuthorityGrantService grants,
            AgentTransportSecurity transport) {
        this.repo = repo;
        this.boundary = boundary;
        this.grants = grants;
        this.transport = transport;
    }

    @Transactional
    public Map<String, Object> invitation(InvitationInput input, String actor) {
        grants.identityHuman(actor);
        if (input == null || input.profile() == null || !Set.of("LOCAL", "REMOTE").contains(input.profile())) throw new IllegalArgumentException("Select a supported Agent connection profile");
        String origin = origin(input.serverOrigin(), input.profile());
        boundary.lock();
        String owner = grants.bind(actor);
        String id = UUID.randomUUID().toString();
        String code = token();
        long now = System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO agent_pairing_invitation(id,owner_id,code_hash,profile,server_origin,state,created_by,created_ms,expires_ms) VALUES(?,?,?,?,?,'OPEN',?,?,?)",
                id, owner, AuthorityRepository.hash(code), input.profile(), origin, actor, now, now + INVITATION_TTL_MS);
        repo.audit("OWNER:" + owner, null, id, actor, "AGENT_PAIRING_INVITATION_CREATED", "Profile=" + input.profile() + "; expires in 5 minutes; no Grant issued");
        String payload = repo.encode(Map.of("platform", origin, "invitation", code, "profile", input.profile(), "ownerId", owner));
        String connectionCode = "aiw-agent-v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return Map.of("invitationId", id, "connectionCode", connectionCode, "profile", input.profile(), "expiresIn", 300,
                "state", "OPEN; exact scope approval is required after Agent pairing");
    }

    @Transactional
    public Map<String, Object> pair(PairInput input, HttpServletRequest request) {
        if (input == null || input.invitation() == null || input.invitation().length() < 32 || input.invitation().length() > 128)
            throw new IllegalArgumentException("Invalid or unavailable Agent invitation");
        boundary.lock();
        String codeHash = AuthorityRepository.hash(input.invitation());
        var rows = repo.jdbc().queryForList("SELECT id,owner_id,profile,server_origin,created_by,expires_ms FROM agent_pairing_invitation WHERE code_hash=? AND state='OPEN'", codeHash);
        long now = System.currentTimeMillis();
        if (rows.size() != 1 || ((Number) rows.getFirst().get("expires_ms")).longValue() <= now)
            throw new SecurityException("Invalid, used or expired Agent invitation");
        var invite = rows.getFirst();
        String profile = String.valueOf(invite.get("profile"));
        String certificateFingerprint = transport.pairingFingerprint(request, profile);
        String agentId = "a-" + UUID.randomUUID();
        String credential = token();
        repo.jdbc().update("""
                INSERT INTO authority_identity(worker_id,owner_id,key_hash,generation,enabled_boot,changed_by,changed_ms,read_scope,worker_boot,identity_kind,connection_profile,client_cert_sha256,approval_state)
                VALUES(?,?,?,1,NULL,?,?,?,NULL,'AGENT',?,?,'PENDING')
                """, agentId, invite.get("owner_id"), AuthorityRepository.hash(credential), "agent-pairing", now,
                repo.encode(List.of()), profile, certificateFingerprint);
        if (repo.jdbc().update("UPDATE agent_pairing_invitation SET state='CONSUMED',consumed_ms=?,agent_id=? WHERE id=? AND state='OPEN' AND expires_ms>?",
                now, agentId, invite.get("id"), now) != 1) throw new IllegalStateException("Invitation expired or was consumed concurrently");
        repo.audit("AGENT:" + agentId, null, String.valueOf(invite.get("id")), "agent:" + agentId, "AGENT_PAIRED",
                "Owner=" + invite.get("owner_id") + "; profile=" + profile + "; certificate="
                        + Objects.toString(certificateFingerprint, "loopback") + "; awaiting one human approval of exact Grant");
        repo.audit("OWNER:" + invite.get("owner_id"), null, String.valueOf(invite.get("id")), String.valueOf(invite.get("created_by")),
                "AGENT_PAIRING_PENDING", "Agent=" + agentId + "; process remains disabled until exact Grant approval");
        return Map.of("ownerId", invite.get("owner_id"), "agentId", agentId, "credential", credential,
                "profile", profile, "serverOrigin", invite.get("server_origin"), "state", "PENDING_HUMAN_APPROVAL");
    }

    @Transactional
    public Map<String, Object> approvePairing(String agentId, PairingApprovalInput input, String actor) {
        grants.identityHuman(actor);
        if (input == null || input.scope() == null || input.agentBoot() == null || input.agentBoot().isBlank())
            throw new IllegalArgumentException("Current Agent session and exact initial Grant are required");
        boundary.lock();
        var rows = repo.jdbc().queryForList("SELECT owner_id,approval_state,generation,worker_boot,enabled_boot FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", agentId);
        if (rows.size() != 1) throw new IllegalArgumentException("Pending Agent identity not found");
        var identity = rows.getFirst();
        String owner = String.valueOf(identity.get("owner_id"));
        grants.owns(owner, actor);
        if (!"PENDING".equals(identity.get("approval_state"))) throw new IllegalStateException("Agent pairing was already approved or revoked");
        if (((Number) identity.get("generation")).longValue() != input.generation()
                || !Objects.equals(identity.get("worker_boot"), input.agentBoot()) || identity.get("worker_boot") == null
                || identity.get("enabled_boot") != null)
            throw new IllegalStateException("Agent process session changed; refresh before approving");

        var grant = grants.issueForPendingPairing(owner, agentId, input.scope(), input.expiresAt(), actor);
        long now = System.currentTimeMillis();
        if (repo.jdbc().update("UPDATE authority_identity SET approval_state='APPROVED',enabled_boot=?,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND identity_kind='AGENT' AND approval_state='PENDING' AND generation=? AND worker_boot=? AND enabled_boot IS NULL",
                boundary.boot(), actor, now, agentId, input.generation(), input.agentBoot()) != 1)
            throw new IllegalStateException("Agent process changed during approval");
        repo.jdbc().update("UPDATE authority_metadata SET human_auth_required=TRUE WHERE id=1");
        repo.audit(grant.targetKey(), grant.id(), null, actor, "AGENT_PAIRING_APPROVED",
                "Agent=" + agentId + "; exact initial Grant approved and current process enabled; restart requires human re-enable");
        return Map.of("state", "APPROVED_ENABLED", "agentId", agentId, "grantId", grant.id(), "generation", input.generation() + 1);
    }

    @Transactional
    public Map<String, Object> revokeCredential(String agentId, String actor) {
        grants.identityHuman(actor);
        boundary.lock();
        String owner = repo.jdbc().queryForObject(
                "SELECT owner_id FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", String.class, agentId);
        grants.owns(owner, actor);
        long now = System.currentTimeMillis();
        int revokedGrants = repo.jdbc().update(
                "UPDATE authority_grant SET state='REVOKED',revision=revision+1 WHERE worker_id=? AND state='ENABLED'", agentId);
        int revokedPermissions = repo.jdbc().update(
                "UPDATE authority_action_permission SET state='REVOKED',revision=revision+1 WHERE worker_id=? AND state='ENABLED'", agentId);
        repo.jdbc().update("UPDATE agent_certificate_rotation SET state='CANCELLED',decided_ms=?,decided_by=? WHERE agent_id=? AND state='PENDING'",
                now, actor, agentId);
        if (repo.jdbc().update("UPDATE authority_identity SET identity_kind='REVOKED_AGENT',approval_state='REVOKED',key_hash=?,worker_boot=NULL,enabled_boot=NULL,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND identity_kind='AGENT'",
                AuthorityRepository.hash(token()), actor, now, agentId) != 1)
            throw new IllegalStateException("Agent credential already revoked or identity changed");
        repo.audit("AGENT:" + agentId, null, null, actor, "AGENT_CREDENTIAL_REVOKED",
                "Old credential and sessions invalidated; revoked Grants=" + revokedGrants + "; revoked permissions="
                        + revokedPermissions + "; records retained for audit; new pairing required");
        return Map.of("state", "CREDENTIAL_REVOKED", "agentId", agentId, "generationChanged", true);
    }

    @Transactional
    public Map<String, Object> requestCertificateRotation(AuthorityBoundary.Principal principal, HttpServletRequest request) {
        boundary.lock();
        String observedFingerprint = transport.authenticate(request, principal.worker(), true);
        var identity = repo.jdbc().queryForMap(
                "SELECT owner_id,connection_profile,client_cert_sha256,approval_state FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'",
                principal.worker());
        if (!"APPROVED".equals(identity.get("approval_state"))) throw new SecurityException("Agent pairing approval is required before certificate rotation");
        if (!"REMOTE".equals(identity.get("connection_profile"))
                || MessageDigest.isEqual(String.valueOf(identity.get("client_cert_sha256")).getBytes(StandardCharsets.US_ASCII),
                        observedFingerprint.getBytes(StandardCharsets.US_ASCII)))
            throw new SecurityException("A different trusted remote certificate is required for rotation");
        var pending = repo.jdbc().queryForList(
                "SELECT id,client_cert_sha256 FROM agent_certificate_rotation WHERE agent_id=? AND state='PENDING' ORDER BY requested_ms DESC",
                principal.worker());
        if (!pending.isEmpty()) {
            if (MessageDigest.isEqual(String.valueOf(pending.getFirst().get("client_cert_sha256")).getBytes(StandardCharsets.US_ASCII),
                    observedFingerprint.getBytes(StandardCharsets.US_ASCII)))
                return Map.of("rotationId", pending.getFirst().get("id"), "state", "PENDING_HUMAN_APPROVAL");
            throw new IllegalStateException("A different certificate rotation is already awaiting human approval");
        }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO agent_certificate_rotation(id,agent_id,client_cert_sha256,state,requested_ms) VALUES(?,?,?,'PENDING',?)",
                id, principal.worker(), observedFingerprint, now);
        repo.audit("AGENT:" + principal.worker(), null, id, "agent:" + principal.worker(), "AGENT_CERTIFICATE_ROTATION_REQUESTED",
                "New certificate fingerprint=" + observedFingerprint + "; waiting for human approval");
        return Map.of("rotationId", id, "state", "PENDING_HUMAN_APPROVAL");
    }

    @Transactional
    public List<Map<String, Object>> certificateRotations(String agentId, String actor) {
        grants.identityHuman(actor);
        String owner = repo.jdbc().queryForObject(
                "SELECT owner_id FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", String.class, agentId);
        grants.owns(owner, actor);
        return repo.jdbc().queryForList("""
                SELECT id,client_cert_sha256,state,requested_ms,decided_ms,decided_by
                FROM agent_certificate_rotation WHERE agent_id=? ORDER BY requested_ms DESC
                """, agentId);
    }

    @Transactional
    public Map<String, Object> decideCertificateRotation(String agentId, String rotationId, boolean approve, String actor) {
        grants.identityHuman(actor);
        boundary.lock();
        String owner = repo.jdbc().queryForObject(
                "SELECT owner_id FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", String.class, agentId);
        grants.owns(owner, actor);
        var rotation = repo.jdbc().queryForMap(
                "SELECT client_cert_sha256,state FROM agent_certificate_rotation WHERE id=? AND agent_id=?", rotationId, agentId);
        if (!"PENDING".equals(rotation.get("state"))) throw new IllegalStateException("Certificate rotation is no longer pending");
        long now = System.currentTimeMillis();
        if (approve) {
            if (repo.jdbc().update("UPDATE authority_identity SET client_cert_sha256=?,worker_boot=NULL,enabled_boot=NULL,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND identity_kind='AGENT' AND connection_profile='REMOTE'",
                    rotation.get("client_cert_sha256"), actor, now, agentId) != 1)
                throw new IllegalStateException("Remote Agent identity changed before certificate approval");
        }
        String state = approve ? "APPROVED" : "REJECTED";
        if (repo.jdbc().update("UPDATE agent_certificate_rotation SET state=?,decided_ms=?,decided_by=? WHERE id=? AND state='PENDING'",
                state, now, actor, rotationId) != 1) throw new IllegalStateException("Certificate rotation decision raced");
        repo.audit("AGENT:" + agentId, null, rotationId, actor,
                approve ? "AGENT_CERTIFICATE_ROTATION_APPROVED" : "AGENT_CERTIFICATE_ROTATION_REJECTED",
                "Certificate fingerprint=" + rotation.get("client_cert_sha256") + "; new process session required");
        return Map.of("state", state, "agentId", agentId, "generationChanged", approve);
    }

    private static String origin(String raw, String profile) {
        if (raw == null || raw.length() > 512) throw new IllegalArgumentException("Invalid Control Plane origin");
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
                throw new IllegalArgumentException("Use a Control Plane origin without credentials, path or query");
            boolean loopback = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
            if (profile.equals("LOCAL") && (!uri.getScheme().equals("http") || !loopback))
                throw new IllegalArgumentException("Local profile requires an explicit loopback HTTP origin");
            if (profile.equals("REMOTE") && !uri.getScheme().equals("https"))
                throw new IllegalArgumentException("Remote profile requires HTTPS");
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException) throw error;
            throw new IllegalArgumentException("Invalid Control Plane origin");
        }
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
