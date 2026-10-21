package me.asu.aidev.authority;

import java.util.Map;
import java.util.Set;

/** Minimal effective capability, never a private policy or executable expression. */
public record ActionScope(Action action,String targetId,Integer step,String effect,Set<String> states,
                          int minimumRounds,long maximumUses,Map<String,String> parameters) {
    public enum Action {
        A01,A02,A03,A04,A05,A06,A07,A08,A09,A10,A11_APPROVE,A11_REJECT,
        A12_CREATE,A12_EXECUTE,A13,A14,A15,A16,A17_SCHEDULE,A17_UNDO
    }
    public ActionScope {
        if(action==null || targetId==null || targetId.isBlank() || targetId.length()>128 || !targetId.equals(targetId.strip()))
            throw new IllegalArgumentException("Exact action and target required");
        if(effect==null || !Set.of("ALLOW","DENY").contains(effect) || states==null || states.isEmpty() || states.size()>16
                || states.stream().anyMatch(s->s==null || !s.matches("[A-Z_]{1,32}"))) throw new IllegalArgumentException("Explicit effect and target states required");
        if(step!=null && (step<0 || step>31) || minimumRounds<0 || maximumUses<1 || maximumUses>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Bounded step, conditions and total ceiling required");
        parameters=parameters==null?Map.of():Map.copyOf(parameters);states=Set.copyOf(states);
        if(parameters.size()>16 || parameters.entrySet().stream().anyMatch(e->e.getKey().length()>64 || e.getValue().length()>4000))
            throw new IllegalArgumentException("Action parameters exceed the closed envelope limit");
        if(parameters.getOrDefault("reason","").length()>2000) throw new IllegalArgumentException("Business reason exceeds 2000 characters");
    }
}
