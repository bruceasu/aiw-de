package me.asu.aidev.approval.application;

import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalPolicyMode;
import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable process-level policy for automatic human-approval materialization. */
@Component
public class ApprovalPolicy {
    private final ApprovalPolicyMode mode;
    private final String requestedBy;
    private final ApprovalChannel channel;
    private final String channelRef;

    public ApprovalPolicy(
            @Value("${aidev.approval.policy.mode:MANUAL}") String mode,
            @Value("${aidev.approval.policy.requested-by:control-plane:approval-policy}") String requestedBy,
            @Value("${aidev.approval.policy.channel:API}") String channel,
            @Value("${aidev.approval.policy.channel-ref:}") String channelRef) {
        this.mode = parseMode(mode);
        this.requestedBy = requireText(requestedBy, "approval policy requested-by");
        this.channel = parseChannel(channel);
        this.channelRef = normalize(channelRef);
    }

    public ApprovalPolicyMode mode() {
        return mode;
    }

    public boolean requiresApproval(EngineeringGate gate) {
        return mode == ApprovalPolicyMode.REQUIRED && gate.status() == EngineeringGateStatus.PASSED;
    }

    public RequestApprovalCommand commandFor(EngineeringGate gate) {
        if (!requiresApproval(gate)) {
            throw new IllegalStateException("approval policy does not require approval for gate: "
                    + gate.sourceTaskId().value());
        }
        return new RequestApprovalCommand(gate.sourceTaskId(), requestedBy, channel, channelRef);
    }

    private static ApprovalPolicyMode parseMode(String raw) {
        try {
            return ApprovalPolicyMode.valueOf(requireText(raw, "approval policy mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported AIW_APPROVAL_POLICY: " + raw
                    + " (expected MANUAL or REQUIRED)", e);
        }
    }

    private static ApprovalChannel parseChannel(String raw) {
        try {
            return ApprovalChannel.valueOf(requireText(raw, "approval policy channel").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported AIW_APPROVAL_CHANNEL: " + raw, e);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value.strip();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }
}
