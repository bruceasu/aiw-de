package me.asu.aidev.autonomous.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the deliberately small R8-S2 work-item manifest embedded in planner output.
 * No JSON/YAML dependency is needed and prose outside the manifest remains human-readable.
 */
public final class ImplementationPlanParser {
    public static final String PLAN_BEGIN = "AIW_IMPLEMENTATION_WORK_ITEMS_V1";
    public static final String PLAN_END = "AIW_IMPLEMENTATION_WORK_ITEMS_END";
    public static final String ITEM_BEGIN = "AIW_WORK_ITEM_BEGIN ";
    public static final String ITEM_END = "AIW_WORK_ITEM_END";
    public static final String CAPABILITIES = "AIW_CAPABILITIES ";

    public Optional<ImplementationPlanManifest> parse(String summary) {
        if (summary == null || summary.isBlank()) return Optional.empty();
        List<String> lines = summary.lines().toList();
        int start = indexOf(lines, PLAN_BEGIN);
        if (start < 0) return Optional.empty();
        int end = indexOfAfter(lines, PLAN_END, start + 1);
        if (end < 0) throw new IllegalArgumentException("implementation work-item manifest is missing " + PLAN_END);

        var items = new ArrayList<ImplementationWorkItem>();
        String key = null;
        Set<String> capabilities = Set.of();
        var instruction = new StringBuilder();

        for (int i = start + 1; i < end; i++) {
            String raw = lines.get(i);
            String stripped = raw.strip();
            if (stripped.isEmpty() && key == null) continue;

            if (stripped.startsWith(ITEM_BEGIN)) {
                if (key != null) throw new IllegalArgumentException("nested work item at manifest line " + (i + 1));
                key = stripped.substring(ITEM_BEGIN.length()).strip();
                capabilities = Set.of();
                instruction.setLength(0);
                continue;
            }
            if (stripped.equals(ITEM_END)) {
                if (key == null) throw new IllegalArgumentException("work item end without begin at manifest line " + (i + 1));
                items.add(new ImplementationWorkItem(key, capabilities, instruction.toString()));
                key = null;
                capabilities = Set.of();
                instruction.setLength(0);
                continue;
            }
            if (stripped.startsWith(CAPABILITIES)) {
                if (key == null) throw new IllegalArgumentException("capabilities outside work item at manifest line " + (i + 1));
                if (instruction.length() > 0) throw new IllegalArgumentException("capabilities must precede work item instruction: " + key);
                capabilities = parseCapabilities(stripped.substring(CAPABILITIES.length()));
                continue;
            }
            if (key == null) {
                throw new IllegalArgumentException("unexpected content inside work-item manifest at line " + (i + 1));
            }
            if (instruction.length() > 0) instruction.append('\n');
            instruction.append(raw.stripTrailing());
        }
        if (key != null) throw new IllegalArgumentException("work item is missing " + ITEM_END + ": " + key);
        return Optional.of(new ImplementationPlanManifest(items));
    }

    private static Set<String> parseCapabilities(String raw) {
        var result = new LinkedHashSet<String>();
        if (raw == null || raw.isBlank()) return Set.of();
        for (String value : raw.split(",")) {
            String capability = value.strip();
            if (!capability.isEmpty()) result.add(capability);
        }
        return Set.copyOf(result);
    }

    private static int indexOf(List<String> lines, String marker) {
        return indexOfAfter(lines, marker, 0);
    }

    private static int indexOfAfter(List<String> lines, String marker, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(marker)) return i;
        }
        return -1;
    }
}
