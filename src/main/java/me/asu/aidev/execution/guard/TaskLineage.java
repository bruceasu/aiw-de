package me.asu.aidev.execution.guard;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import me.asu.aidev.work.domain.TaskId;

/** All consumers resolve the same logical output slot; historical inputs remain immutable. */
@Component
public class TaskLineage {
    private final JdbcTemplate jdbc;
    public TaskLineage(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public TaskId current(TaskId id) { return current(jdbc,id); }
    public static TaskId current(JdbcTemplate jdbc,TaskId id) {
        Set<String> visited=new HashSet<>();String task=id.value();
        while(visited.add(task)) {
            var rows=jdbc.queryForList("SELECT successor_task_id FROM execution_context WHERE task_id=?",String.class,task);
            if(rows.isEmpty() || rows.getFirst()==null) return new TaskId(task);
            task=rows.getFirst();
            if(visited.size()>1024) throw new IllegalStateException("Continuation lineage exceeds supported size");
        }
        throw new IllegalStateException("Continuation cycle detected");
    }
    public List<TaskId> ancestors(TaskId id) {
        var result=new ArrayList<TaskId>();Set<String> visited=new HashSet<>();String task=id.value();
        while(visited.add(task)) {
            result.add(new TaskId(task));
            var rows=jdbc.queryForList("SELECT resumes_task_id FROM execution_context WHERE task_id=?",String.class,task);
            if(rows.isEmpty() || rows.getFirst()==null) return List.copyOf(result);
            task=rows.getFirst();if(visited.size()>1024) throw new IllegalStateException("Continuation lineage exceeds supported size");
        }
        throw new IllegalStateException("Continuation cycle detected");
    }
}
