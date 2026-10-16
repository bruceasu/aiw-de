package me.asu.aidev.workflow.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;

/** A bounded, explicitly selected delegation graph; a proposal never grants execution authority. */
public record WorkflowPlanSpec(String goal, Source source, List<Step> steps) {
    public enum Source { MANAGER, AI }

    public WorkflowPlanSpec {
        if (goal == null || goal.isBlank() || goal.length() > 16_384) {
            throw new IllegalArgumentException("goal must contain 1 to 16384 characters");
        }
        goal = goal.strip();
        if (source == null) throw new IllegalArgumentException("source is required");
        if (steps == null || steps.isEmpty() || steps.size() > 32 || steps.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("steps must contain 1 to 32 non-null items");
        }
        steps = List.copyOf(steps);
        Set<String> seen = new HashSet<>();
        for (Step step : steps) {
            if (seen.contains(step.key())) throw new IllegalArgumentException("duplicate step key: " + step.key());
            if (!seen.containsAll(step.dependsOn())) {
                throw new IllegalArgumentException("steps must follow their dependencies: " + step.key());
            }
            seen.add(step.key());
        }
    }

    public record Step(String key, Set<String> requiredSkills, Set<String> preferredSkills,
                       String skillRef, String instruction, String repositoryRef,
                       String baseRevision, Set<String> dependsOn, String model, String effort) {
        public Step(String key, Set<String> requiredSkills, Set<String> preferredSkills,
                    String skillRef, String instruction, String repositoryRef,
                    String baseRevision, Set<String> dependsOn) {
            this(key, requiredSkills, preferredSkills, skillRef, instruction, repositoryRef,
                    baseRevision, dependsOn, null, null);
        }

        public Step {
            model = preference(model);
            effort = preference(effort);
            if (key == null || !key.matches("[a-z][a-z0-9._-]{0,31}")) {
                throw new IllegalArgumentException("invalid step key");
            }
            requiredSkills = skills(requiredSkills);
            preferredSkills = skills(preferredSkills);
            if (requiredSkills.isEmpty()) throw new IllegalArgumentException("requiredSkills must not be empty");
            if (skillRef == null || skillRef.isBlank() || skillRef.length() > 256) {
                throw new IllegalArgumentException("skillRef must contain 1 to 256 characters");
            }
            skillRef = skillRef.strip();
            if (instruction == null || instruction.isBlank() || instruction.length() > 16_384) {
                throw new IllegalArgumentException("instruction must contain 1 to 16384 characters");
            }
            if (dependsOn != null && dependsOn.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("dependency key is null");
            }
            dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
            // Reuse the established task's workspace and instruction validation at draft creation.
            WorkTask validated = new WorkTask(new TaskId(key), TaskType.EXTERNAL_WORK, TaskState.READY,
                    "flexible-plan", "draft", requiredSkills, skillRef, instruction, repositoryRef,
                    baseRevision, TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH);
            instruction = validated.instruction();
            repositoryRef = validated.repositoryRef();
            baseRevision = validated.baseRevision();
        }

        private static String preference(String value) {
            if (value == null || value.isBlank()) return null;
            if (value.length() > 160) throw new IllegalArgumentException("AI preference must not exceed 160 characters");
            return value.strip();
        }

        private static Set<String> skills(Set<String> input) {
            if (input != null && input.size() > 32) throw new IllegalArgumentException("at most 32 skills");
            Set<String> normalized = CapabilitySetCodec.decode(CapabilitySetCodec.encode(input));
            if (normalized.stream().anyMatch(s -> !s.matches("[a-z0-9][a-z0-9:._/-]{0,127}"))) {
                throw new IllegalArgumentException("invalid skill name");
            }
            return normalized;
        }
    }
}
