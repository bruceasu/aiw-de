package me.asu.aidev.autonomous.application;

import java.util.LinkedHashSet;
import java.util.List;

/** Machine-readable implementation-work decomposition embedded in the planner's completion summary. */
public record ImplementationPlanManifest(List<ImplementationWorkItem> items) {
    public static final int MAX_ITEMS = 16;

    public ImplementationPlanManifest {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("implementation plan has no work items");
        if (items.size() > MAX_ITEMS) throw new IllegalArgumentException("implementation plan exceeds " + MAX_ITEMS + " work items");
        items = List.copyOf(items);
        var keys = new LinkedHashSet<String>();
        for (ImplementationWorkItem item : items) {
            if (!keys.add(item.key())) throw new IllegalArgumentException("duplicate work item key: " + item.key());
        }
    }
}
