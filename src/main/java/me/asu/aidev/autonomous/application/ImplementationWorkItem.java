package me.asu.aidev.autonomous.application;

import java.util.LinkedHashSet;
import java.util.Set;

/** One independently implementable item materialized from an authoritative implementation plan. */
public record ImplementationWorkItem(String key, Set<String> capabilities, String instruction) {
    public ImplementationWorkItem {
        if (key == null || !key.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("work item key must match [a-z0-9][a-z0-9-]{0,31}");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("work item instruction is blank: " + key);
        }
        instruction = instruction.strip();
        var normalized = new LinkedHashSet<String>();
        if (capabilities != null) {
            for (String capability : capabilities) {
                if (capability == null || capability.isBlank()) continue;
                String value = capability.strip();
                if (!value.matches("[A-Za-z0-9._:-]{1,120}")) {
                    throw new IllegalArgumentException("invalid work item capability: " + value);
                }
                normalized.add(value);
            }
        }
        capabilities = Set.copyOf(normalized);
    }
}
