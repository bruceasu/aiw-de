package me.asu.aidev.workflow.application;

import java.util.List;
import me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile;

/** Exact constraints on existing approved profiles; no profile synthesis. */
public final class RecommendationProfiles {
    private RecommendationProfiles() {}
    public static List<NamedProfile> filter(List<NamedProfile> approved, String planner, String model, String effort) {
        var constrained = approved.stream().filter(p -> planner == null || planner.equals(p.id()))
                .filter(p -> model == null || model.equals(p.profile().model()))
                .filter(p -> effort == null || effort.equals(p.profile().effort())).toList();
        var selected = constrained.stream().filter(p -> compatible(p.profile())).toList();
        if (selected.isEmpty() && !constrained.isEmpty()) throw new IllegalArgumentException(
                "Configured planner profiles are incompatible with Worker runtimes: openai provider required; "
                + "codex-app-server requires fast=false; openai-api/openai-agents require fast=false and supported effort. "
                + "Profiles: " + constrained.stream().map(p -> p.id() + " (" + p.profile().runtime() + ", fast="
                + p.profile().fast() + ", effort=" + p.profile().effort() + ")").toList());
        if (selected.isEmpty()) throw new IllegalArgumentException(
                "No approved profile matches planner=" + planner + ", model=" + model + ", effort=" + effort
                + "; explicit settings cannot fall back");
        return selected;
    }

    public static boolean compatible(me.asu.aidev.ai.application.AiProfileCatalog.Profile p) {
        if (!"openai".equals(p.provider())) return false;
        if ("codex-app-server".equals(p.runtime()) && p.fast()) return false;
        if (List.of("openai-api", "openai-agents").contains(p.runtime())) return !p.fast()
                && List.of("none", "minimal", "low", "medium", "high").contains(p.effort());
        return List.of("codex-cli", "codex-sdk", "codex-app-server").contains(p.runtime())
                && List.of("none", "minimal", "low", "medium", "high", "xhigh").contains(p.effort());
    }
}
