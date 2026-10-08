package me.asu.aidev.autonomous.application;

import java.util.LinkedHashSet;
import java.util.Set;

final class AutonomousCapabilities {
    private static final Set<String> ROLES = Set.of(
            "requirement-analysis", "solution-architecture", "implementation-planning",
            "coding", "integration", "code-review", "verification", "integration-verification", "repair");

    private AutonomousCapabilities() {}

    static Set<String> forRole(Set<String> source, String role) {
        var result = new LinkedHashSet<String>();
        if (source != null) {
            for (String capability : source) {
                if (capability == null || capability.isBlank() || ROLES.contains(capability)) continue;
                result.add(capability);
            }
        }
        result.add(role);
        return Set.copyOf(result);
    }
}
