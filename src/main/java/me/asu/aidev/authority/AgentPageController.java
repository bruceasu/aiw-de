package me.asu.aidev.authority;

import java.util.Objects;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Human-only management surface for pairing Agents and approving certificate changes. */
@Controller
public class AgentPageController {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final WebConsoleSecurityService security;

    public AgentPageController(AuthorityRepository repo, AuthorityBoundary boundary, WebConsoleSecurityService security) {
        this.repo = repo;
        this.boundary = boundary;
        this.security = security;
    }

    @GetMapping("/ui/agents")
    public String agents(Model model) {
        String actor = Objects.requireNonNull(boundary.human(), "Authenticated human required");
        if (security.currentDelegationAuthority(actor, "A05").isBlank()
                && security.currentDelegationAuthority(actor, "A11_APPROVE").isBlank())
            throw new SecurityException("Current identified human role required");
        var identities = repo.jdbc().queryForList("""
                SELECT i.worker_id AS agent_id,i.owner_id,i.identity_kind,i.approval_state,i.connection_profile,i.client_cert_sha256,
                       i.generation,i.worker_boot,i.enabled_boot
                FROM authority_identity i JOIN authority_owner o ON o.id=i.owner_id
                WHERE o.actor=? AND i.identity_kind IN ('AGENT','REVOKED_AGENT') ORDER BY i.worker_id
                """, actor);
        identities.forEach(row -> {
            Object enabledBoot = row.remove("enabled_boot");
            row.put("enabled", "AGENT".equals(row.get("identity_kind")) && "APPROVED".equals(row.get("approval_state")) && boundary.boot().equals(enabledBoot));
            row.put("revoked", "REVOKED_AGENT".equals(row.get("identity_kind")));
        });
        model.addAttribute("agents", identities);
        model.addAttribute("actions", ActionScope.Action.values());
        return "ui/agents";
    }
}
