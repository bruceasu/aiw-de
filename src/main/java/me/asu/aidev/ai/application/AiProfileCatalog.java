package me.asu.aidev.ai.application;

import me.asu.aidev.execution.domain.AiAttemptHistory;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.work.domain.TaskType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Human-reviewed profile catalog. Workflow policy chooses the allowed path; Workers choose within it. */
@Component
public class AiProfileCatalog {
    private final String version;
    private final Map<String, Profile> profiles;
    private final Map<TaskType, List<String>> policies;

    public AiProfileCatalog(Environment environment) {
        Config config = Binder.get(environment).bind("aidev.ai", Bindable.of(Config.class))
                .orElse(new Config(null, Map.of(), List.of()));
        this.version = config.version() == null ? "" : config.version().strip();
        this.profiles = Map.copyOf(config.profiles() == null ? Map.of() : config.profiles());
        Map<TaskType, List<String>> configured = new HashMap<>();
        for (var entry : profiles.entrySet()) {
            if (!entry.getKey().matches("[a-z][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("invalid AI profile id: " + entry.getKey());
            Profile p = Objects.requireNonNull(entry.getValue(), "AI profile");
            if (!valid(p.provider()) || !valid(p.runtime()) || !valid(p.model()) || !valid(p.effort()) || p.level() < 1) {
                throw new IllegalArgumentException("invalid AI profile: " + entry.getKey());
            }
        }
        for (Policy policy : config.policies() == null ? List.<Policy>of() : config.policies()) {
            TaskType type = TaskType.valueOf(policy.taskType());
            List<String> path = List.copyOf(policy.profiles());
            if (path.isEmpty() || new HashSet<>(path).size() != path.size() || !profiles.keySet().containsAll(path)) {
                throw new IllegalArgumentException("invalid AI profile path for " + type);
            }
            if (configured.putIfAbsent(type, path) != null) throw new IllegalArgumentException("duplicate AI policy: " + type);
        }
        if (!configured.isEmpty() && !version.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("aidev.ai.version is required when AI policies are configured");
        }
        this.policies = Map.copyOf(configured);
    }

    private static boolean valid(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,120}");
    }

    public boolean hasPolicy(TaskType type) { return policies.containsKey(type); }

    public String version() { return version; }
    public List<NamedProfile> approved(TaskType type) {
        return policies.getOrDefault(type, List.of()).stream().map(id -> new NamedProfile(id, profiles.get(id))).toList();
    }

    public boolean canRun(TaskType type, String workerRuntime) {
        if (!hasPolicy(type)) return true;
        return policies.get(type).stream().map(profiles::get)
                .anyMatch(profile -> workerRuntime.startsWith(profile.runtime() + ":"));
    }

    public Guidance guidance(TaskType type, String workerRuntime, AiAttemptHistory previous) {
        if (!hasPolicy(type)) return null;
        List<NamedProfile> allowed = new ArrayList<>();
        for (String id : policies.get(type)) {
            Profile profile = profiles.get(id);
            if (workerRuntime.startsWith(profile.runtime() + ":")) allowed.add(new NamedProfile(id, profile));
        }
        if (allowed.isEmpty()) throw new IllegalStateException("worker has no approved AI profile for " + type);
        String suggestion = allowed.get(0).id();
        if (previous != null) {
            int index = -1;
            for (int i = 0; i < allowed.size(); i++) {
                if (allowed.get(i).id().equals(previous.profileId())) { index = i; break; }
            }
            if (index >= 0) {
                boolean upgrade = "MODEL_ERROR".equals(previous.failureCategory())
                        || "OUTPUT_INVALID".equals(previous.failureCategory());
                suggestion = allowed.get(upgrade ? Math.min(index + 1, allowed.size() - 1) : index).id();
            }
        }
        return new Guidance(version, allowed, previous, suggestion);
    }

    public AiSelection select(TaskType type, String workerRuntime, String profileId,
                              String profileVersion, String reason) {
        if (!hasPolicy(type)) throw new IllegalStateException("AI profile is not configured for " + type);
        if (!version.equals(profileVersion)) throw new IllegalArgumentException("AI profile version mismatch");
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("AI selection reason is required (max 512 characters)");
        }
        if (!policies.get(type).contains(profileId)) throw new IllegalArgumentException("AI profile is not approved for " + type);
        Profile profile = profiles.get(profileId);
        if (!workerRuntime.startsWith(profile.runtime() + ":")) throw new IllegalArgumentException("AI profile runtime does not match worker");
        return new AiSelection(profileId, version, profile.provider(), profile.runtime(), profile.model(),
                profile.effort(), profile.fast(), reason.strip());
    }

    public record Config(String version, Map<String, Profile> profiles, List<Policy> policies) {}
    public record Profile(String provider, String runtime, String model, String effort, boolean fast, int level) {}
    public record Policy(String taskType, List<String> profiles) {}
    public record NamedProfile(String id, Profile profile) {}
    public record Guidance(String version, List<NamedProfile> allowedProfiles,
                           AiAttemptHistory previous, String suggestedProfile) {}
}
