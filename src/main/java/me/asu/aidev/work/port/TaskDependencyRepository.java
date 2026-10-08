package me.asu.aidev.work.port;

import me.asu.aidev.work.domain.TaskDependency;
import me.asu.aidev.work.domain.TaskId;
import java.util.List;

public interface TaskDependencyRepository {
    void insert(TaskDependency dependency);
    boolean insertIfAbsent(TaskDependency dependency);
    List<TaskId> findDependencies(TaskId taskId);
    List<TaskId> findDependents(TaskId dependsOnTaskId);
}
