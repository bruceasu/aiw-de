package me.asu.aidev.execution.application;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Runs completion handlers. Handlers may create tasks/dependencies but never invoke workers directly. */
@Component
public class TaskCompletionDispatcher {
    private final List<TaskCompletionHandler> handlers;

    public TaskCompletionDispatcher(List<TaskCompletionHandler> handlers) {
        this.handlers = List.copyOf(handlers);
        assertSingleAuthorityPerTaskType(this.handlers);
    }

    public List<TaskId> dispatch(TaskCompletionContext context) {
        var generated = new LinkedHashSet<TaskId>();
        for (TaskCompletionHandler handler : handlers) {
            if (!handler.supports(context)) continue;
            List<TaskId> created = handler.onCompleted(context);
            if (created != null) generated.addAll(created);
        }
        return List.copyOf(new ArrayList<>(generated));
    }

    private static void assertSingleAuthorityPerTaskType(List<TaskCompletionHandler> handlers) {
        Map<TaskType, TaskCompletionHandler> owners = new EnumMap<>(TaskType.class);
        for (TaskCompletionHandler handler : handlers) {
            if (handler.supportedTypes() == null || handler.supportedTypes().isEmpty()) {
                throw new IllegalStateException("completion handler declares no supported task types: "
                        + handler.getClass().getName());
            }
            for (TaskType type : handler.supportedTypes()) {
                TaskCompletionHandler previous = owners.putIfAbsent(type, handler);
                if (previous != null && previous != handler) {
                    throw new IllegalStateException("duplicate completion handler authority for " + type
                            + ": " + previous.getClass().getName() + " and " + handler.getClass().getName());
                }
            }
        }
    }
}
