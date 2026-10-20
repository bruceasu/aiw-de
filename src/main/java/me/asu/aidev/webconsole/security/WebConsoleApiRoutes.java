package me.asu.aidev.webconsole.security;

import java.util.List;

/** Console JSON/file APIs keep their session boundary after moving out of /ui. */
public final class WebConsoleApiRoutes {
    private static final List<String> ROOTS = List.of("/api/r1/goals", "/api/r1/secretary",
            "/api/r1/delegation", "/api/r1/task-outputs", "/api/r1/workflow-plans", "/api/r1/plan-editor");
    private WebConsoleApiRoutes() {}
    public static boolean matches(String path) {
        return ROOTS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }
    public static String[] patterns() {
        return ROOTS.stream().flatMap(root -> java.util.stream.Stream.of(root, root + "/**")).toArray(String[]::new);
    }
}
