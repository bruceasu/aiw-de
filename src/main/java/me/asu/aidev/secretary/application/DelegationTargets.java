package me.asu.aidev.secretary.application;

import java.util.Map;
import me.asu.aidev.secretary.domain.DelegationRule;

/** Fixed business adapters own target identity, locking and existing domain Gates. */
public interface DelegationTargets {
    void validate(DelegationRule rule);
    String key(DelegationRule rule);
    void admit(DelegationRule rule);
    long baseline(DelegationRule rule);
    Map<String,Object> snapshot(DelegationRule rule);
    Object execute(DelegationRule rule, String actor, String grantId, long grantRevision);
}
