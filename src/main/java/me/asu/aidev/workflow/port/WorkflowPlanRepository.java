package me.asu.aidev.workflow.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.workflow.domain.WorkflowPlan;

public interface WorkflowPlanRepository {
    void insert(WorkflowPlan plan);
    Optional<WorkflowPlan> find(String id);
    List<WorkflowPlan> list();
    List<WorkflowPlan> list(boolean archived);
    boolean transition(String id, long version, WorkflowPlan.State from, WorkflowPlan.State to,
                       String actor, String reason, Instant now);
    void savePreferences(TaskId task, Set<String> skills);
    Map<TaskId, Set<String>> preferences();
}
