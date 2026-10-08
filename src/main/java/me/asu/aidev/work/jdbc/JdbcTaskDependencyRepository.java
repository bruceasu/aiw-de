package me.asu.aidev.work.jdbc;

import me.asu.aidev.work.domain.TaskDependency;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.TaskDependencyRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskDependencyRepository implements TaskDependencyRepository {
    private final JdbcTemplate jdbc;

    public JdbcTaskDependencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TaskDependency dependency) {
        jdbc.update("INSERT INTO work_task_dependency(task_id,depends_on_task_id) VALUES(?,?)",
                dependency.taskId().value(), dependency.dependsOnTaskId().value());
    }

    @Override
    public boolean insertIfAbsent(TaskDependency dependency) {
        return jdbc.update("""
                INSERT INTO work_task_dependency(task_id,depends_on_task_id) VALUES(?,?)
                ON CONFLICT(task_id,depends_on_task_id) DO NOTHING
                """, dependency.taskId().value(), dependency.dependsOnTaskId().value()) == 1;
    }

    @Override
    public List<TaskId> findDependencies(TaskId taskId) {
        return jdbc.query("SELECT depends_on_task_id FROM work_task_dependency WHERE task_id=? ORDER BY depends_on_task_id",
                (rs, rowNum) -> new TaskId(rs.getString(1)), taskId.value());
    }

    @Override
    public List<TaskId> findDependents(TaskId dependsOnTaskId) {
        return jdbc.query("SELECT task_id FROM work_task_dependency WHERE depends_on_task_id=? ORDER BY task_id",
                (rs, rowNum) -> new TaskId(rs.getString(1)), dependsOnTaskId.value());
    }
}
