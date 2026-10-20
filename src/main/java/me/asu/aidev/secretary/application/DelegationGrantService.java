package me.asu.aidev.secretary.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.asu.aidev.secretary.domain.DelegationRule;
import me.asu.aidev.secretary.jdbc.DelegationRepository;
import me.asu.aidev.secretary.jdbc.DelegationRepository.Grant;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelegationGrantService {
    private final JdbcTemplate jdbc;
    private final DelegationBoundary boundary;
    private final DelegationRepository repo;
    private final DelegationTargets targets;
    private final WebConsoleSecurityService security;
    public DelegationGrantService(JdbcTemplate jdbc, DelegationBoundary boundary, DelegationRepository repo,
                                  DelegationTargets targets, WebConsoleSecurityService security) {
        this.jdbc = jdbc; this.boundary = boundary; this.repo = repo; this.targets = targets; this.security = security;
    }
    public record Draft(String id, Long revision, String workerId, String originalText,
                        DelegationRule rule, long expiresMs, String templateId, Long templateRevision) {}
    public Grant owned(String id, String actor) {
        Grant grant = repo.get(id);
        if (!grant.owner().equals(actor)) throw new SecurityException("Grant belongs to another operator");
        return grant;
    }
    public List<Grant> list(String actor, boolean administrator) {
        return repo.grants().stream().filter(g -> administrator || g.owner().equals(actor)).toList();
    }
    @Transactional public Grant save(Draft input, String actor) {
        boundary.lock();
        if (input.rule() == null || input.workerId() == null || input.workerId().isBlank()
                || input.originalText() == null || input.originalText().isBlank() || input.originalText().length() > 16000)
            throw new IllegalArgumentException("Provide a delegate, original text and explicit structured rule");
        targets.validate(input.rule());
        String id = input.id() == null || input.id().isBlank() ? "DG-" + UUID.randomUUID() : input.id();
        long now = Instant.now().toEpochMilli();
        long revision = 1;
        if (input.id() != null && !input.id().isBlank()) {
            Grant old = owned(id, actor);
            if (input.revision() == null || old.revision() != input.revision()) throw new IllegalStateException("Grant changed; reload");
            if (old.state().equals("ENABLED") && old.rule().effect().equals("DENY"))
                throw new IllegalStateException("Explicitly revoke or replace the active prohibition before editing it; a draft cannot remove it");
            revision = old.revision() + 1;
            jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE grant_id=? AND state='PENDING'",
                    "Grant edited; the old candidate is invalid.", now, id);
            jdbc.update("DELETE FROM delegation_grant WHERE id=?", id);
        }
        if ((input.templateId() == null) != (input.templateRevision() == null)) throw new IllegalArgumentException("Template identity and version must be supplied together");
        if (input.templateId() != null) {
            Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM delegation_template_history WHERE template_id=? AND owner=? AND revision=?",
                    Integer.class, input.templateId(), actor, input.templateRevision());
            if (found == null || found != 1) throw new IllegalArgumentException("Template version is not available to this owner");
        }
        jdbc.update("INSERT INTO delegation_grant VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", id, actor, input.workerId(), revision,
                "DRAFT", input.originalText(), repo.encode(input.rule()), "", input.templateId(), input.templateRevision(), now, input.expiresMs());
        Grant result = repo.get(id);
        repo.history(result, actor);
        repo.audit(null, id, null, actor, "GRANT_DRAFT_SAVED", "Revision " + revision + "; not enabled");
        return result;
    }
    @Transactional public Grant enable(String id, long revision, boolean confirmed, String actor) {
        boundary.lock();
        Grant grant = owned(id, actor);
        if (!confirmed || grant.revision() != revision) throw new IllegalStateException("Confirm the exact current structured revision");
        long now = Instant.now().toEpochMilli();
        if (grant.expiresMs() <= now) throw new IllegalArgumentException("Configure an explicit future expiry before enabling");
        String authority = security.currentDelegationAuthority(actor, grant.rule().action().name());
        if (authority.isBlank()) throw new SecurityException("Current owner role does not authorize this action");
        Integer enrolled = jdbc.queryForObject("SELECT COUNT(*) FROM delegation_identity WHERE worker_id=?", Integer.class, grant.workerId());
        if (enrolled == null || enrolled != 1) throw new IllegalArgumentException("Enroll the dedicated delegate identity first");
        String key = targets.key(grant.rule());
        targets.validate(grant.rule());
        targets.admit(grant.rule());
        for (String reference : grant.rule().supersedes()) {
            int split = reference.lastIndexOf(':');
            Grant previous = repo.get(reference.substring(0,split));
            if (!previous.owner().equals(actor) || previous.revision() != Long.parseLong(reference.substring(split+1))
                    || !targets.key(previous.rule()).equals(key) || previous.rule().action() != grant.rule().action())
                throw new IllegalArgumentException("Replacement must reference an owned, applicable exact rule version");
            jdbc.update("UPDATE delegation_grant SET state='REPLACED' WHERE id=? AND revision=?", previous.id(), previous.revision());
            repo.audit(key, previous.id(), null, actor, "GRANT_REPLACED", "Explicitly replaced by " + id + ":" + revision);
        }
        jdbc.update("INSERT INTO delegation_target VALUES(?,0,?) ON CONFLICT(target_key) DO NOTHING", key, now);
        if(grant.rule().effect().equals("ALLOW"))
            jdbc.update("INSERT INTO delegation_budget VALUES(?,?,?, ?,0) ON CONFLICT(target_key,action) DO NOTHING",
                    key, grant.rule().action().name(), grant.rule().maximumUses(), targets.baseline(grant.rule()));
        jdbc.update("UPDATE delegation_grant SET state='ENABLED',authority=?,effective_ms=? WHERE id=? AND revision=?", authority, now, id, revision);
        repo.audit(key, id, null, actor, "GRANT_ENABLED", "Confirmed revision " + revision + "; expiry " + grant.expiresMs());
        return repo.get(id);
    }
    @Transactional public Grant revoke(String id, long revision, String actor) {
        boundary.lock();
        Grant grant = owned(id, actor);
        if (grant.revision() != revision) throw new IllegalStateException("Grant changed; reload");
        jdbc.update("UPDATE delegation_grant SET state='REVOKED' WHERE id=?", id);
        jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE grant_id=? AND state='PENDING'",
                "Human revoked authority; the old candidate cannot be applied.", Instant.now().toEpochMilli(), id);
        repo.audit(targets.key(grant.rule()), id, null, actor, "GRANT_REVOKED", "Revision " + revision);
        return repo.get(id);
    }

    public boolean current(Grant grant) {
        return grant.state().equals("ENABLED") && grant.expiresMs() > Instant.now().toEpochMilli()
                && !grant.authority().isBlank() && grant.authority().equals(security.currentDelegationAuthority(grant.owner(), grant.rule().action().name()));
    }
    public boolean permitted(Grant selected) {
        return permitted(selected, targets.snapshot(selected.rule()));
    }
    public boolean permitted(Grant selected, java.util.Map<String,Object> snapshot) {
        if (!current(selected) || !selected.rule().effect().equals("ALLOW") || !DelegationConditions.matches(selected.rule(), snapshot)) return false;
        String key = targets.key(selected.rule());
        var denial = repo.grants().stream().filter(this::current).filter(g -> g.rule().action() == selected.rule().action())
                .filter(g -> targets.key(g.rule()).equals(key) && g.rule().effect().equals("DENY"))
                .filter(g -> DelegationConditions.matches(g.rule(), snapshot)).findFirst();
        if (denial.isPresent()) throw new IllegalStateException("Applicable prohibition " + denial.get().id() + ":" + denial.get().revision() + " requires human handling");
        return true;
    }
}
