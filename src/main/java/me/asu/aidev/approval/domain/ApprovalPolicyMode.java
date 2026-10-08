package me.asu.aidev.approval.domain;

/** Controls whether a PASSED engineering gate automatically materializes human approval work. */
public enum ApprovalPolicyMode {
    /** Approval requests are created explicitly through API/CLI/operator action. */
    MANUAL,
    /** Every PASSED engineering gate must immediately materialize one durable approval request. */
    REQUIRED
}
