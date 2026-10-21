package me.asu.aidev.authority;

import java.util.Map;


/** Fixed business adapters own target identity, locking and existing domain Gates. */
public interface AuthorityTargets {
    void validate(ActionScope rule);
    String key(ActionScope rule);
    void admit(ActionScope rule);
    long baseline(ActionScope rule);
    Map<String,Object> snapshot(ActionScope rule);
    Object execute(ActionScope rule, String actor, String grantId, long grantRevision);
}
