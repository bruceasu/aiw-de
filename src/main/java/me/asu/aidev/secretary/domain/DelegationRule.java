package me.asu.aidev.secretary.domain;

import java.util.Map;
import java.util.Set;

/** Closed action vocabulary. Model text cannot add an operation or an executable expression. */
public record DelegationRule(Action action, String targetId, Integer step, String effect,
                             Set<String> states, int minimumRounds, long maximumUses,
                             Map<String,String> parameters, Set<String> supersedes) {
    public enum Action {
        A01, A02, A03, A04, A05, A06, A07, A08, A09, A10,
        A11_APPROVE, A11_REJECT, A12_CREATE, A12_EXECUTE,
        A13, A14, A15, A16, A17_SCHEDULE, A17_UNDO
    }
    public DelegationRule {
        if (action == null || targetId == null || targetId.isBlank() || targetId.length() > 128)
            throw new IllegalArgumentException("An action and exact target identity are required");
        if (effect == null || !Set.of("ALLOW", "DENY").contains(effect)) throw new IllegalArgumentException("Choose ALLOW or DENY");
        if (states == null || states.isEmpty() || states.size() > 16 || states.stream().anyMatch(s -> s == null || !s.matches("[A-Z_]{1,32}")))
            throw new IllegalArgumentException("Explicit allowed target states are required");
        if (minimumRounds < 0 || maximumUses < 1 || maximumUses > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Configure nonnegative trigger rounds and a positive bounded cumulative limit");
        if (step != null && (step < 0 || step > 31)) throw new IllegalArgumentException("Invalid Plan step");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (parameters.size() > 16 || parameters.entrySet().stream().anyMatch(e -> e.getKey().length() > 64 || e.getValue().length() > 4000))
            throw new IllegalArgumentException("Too many or oversized action parameters");
        supersedes = supersedes == null ? Set.of() : Set.copyOf(supersedes);
        if (supersedes.size() > 64 || supersedes.stream().anyMatch(s -> !s.matches("DG-[a-f0-9-]+:[0-9]+")))
            throw new IllegalArgumentException("Replacement references must name exact grant versions");
        states = Set.copyOf(states);
        targetId = targetId.strip();
    }
}
