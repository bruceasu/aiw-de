package me.asu.aidev.execution.guard;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.WorkTask;

/** All consumers resolve the same logical output slot; historical inputs remain immutable. */
@Component
public class TaskLineage {
    private final JdbcTemplate jdbc;
    public TaskLineage(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public TaskId current(TaskId id) { return current(jdbc,id); }
    /** Current business slots only. The caller still keeps the full history for audit and Lease actions. */
    public List<WorkTask> effective(List<WorkTask> history) {
        var known=new LinkedHashMap<TaskId,WorkTask>();history.forEach(t->known.put(t.id(),t));
        var selected=new LinkedHashMap<TaskId,WorkTask>();
        for(var old:history) {
            var id=current(old.id());var actual=known.get(id);
            if(actual==null||!actual.subjectId().equals(old.subjectId())||!actual.subjectType().equals(old.subjectType())) throw new IllegalStateException("Current lineage slot missing from source history");
            selected.putIfAbsent(id,actual);
        }return List.copyOf(selected.values());
    }
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
