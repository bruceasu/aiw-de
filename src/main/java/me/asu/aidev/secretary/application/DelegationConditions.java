package me.asu.aidev.secretary.application;

import java.util.Map;
import me.asu.aidev.secretary.domain.DelegationRule;

/** One interpretation for ALLOW and DENY, including publication side authority. */
public final class DelegationConditions {
    private DelegationConditions() {}
    public static boolean matches(DelegationRule rule, Map<String,Object> snapshot) {
        return rule.states().contains(String.valueOf(snapshot.get("state")))
                && ((Number)snapshot.getOrDefault("rounds",0)).longValue() >= rule.minimumRounds();
    }
}
