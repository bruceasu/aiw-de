package me.asu.aidev.approval.application;

import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.port.EngineeringGateFinalizedListener;
import org.springframework.stereotype.Component;

/** Materializes exactly one ApprovalRequest when configured policy requires human authority. */
@Component
public class ApprovalPolicyGateListener implements EngineeringGateFinalizedListener {
    private final ApprovalPolicy policy;
    private final ApprovalService approvals;

    public ApprovalPolicyGateListener(ApprovalPolicy policy, ApprovalService approvals) {
        this.policy = policy;
        this.approvals = approvals;
    }

    @Override
    public void onFinalized(EngineeringGate gate) {
        if (!policy.requiresApproval(gate)) return;
        approvals.request(policy.commandFor(gate));
    }
}
