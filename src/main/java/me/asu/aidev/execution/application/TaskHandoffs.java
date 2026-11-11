package me.asu.aidev.execution.application;

import java.util.Objects;
import me.asu.aidev.workflow.application.PlanLocations;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records Worker assertions; never opens Worker files or trusts them as execution authority. */
@Service
public class TaskHandoffs {
    public record Reference(String manifest,String sha256) {
        public Reference {
            if(manifest==null || !manifest.matches("\\.ai/handoffs/[a-f0-9]{64}/[a-f0-9]{64}/manifest\\.json")
                    || sha256==null || !sha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid handoff reference");
        }
    }
    public record Input(String location,String manifest,String sha256) {}
    private final JdbcTemplate jdbc;
    private final WorkTaskRepository tasks;
    private final PlanLocations locations;
    public TaskHandoffs(JdbcTemplate jdbc,WorkTaskRepository tasks,PlanLocations locations) {
        this.jdbc=jdbc;this.tasks=tasks;this.locations=locations;
    }
    @Transactional
    public void record(String task,String worker,String token,Reference ref) {
        if(ref==null) throw new IllegalArgumentException("Worker handoff required");
        var rows=jdbc.queryForList("""
                SELECT a.id,a.state FROM execution_attempt a JOIN work_task t ON t.id=a.task_id
                 WHERE a.task_id=? AND a.worker_id=? AND a.lease_token=?
                   AND t.lease_token=? AND t.lease_expires_at_epoch_ms>?
                """,task,worker,token,token,System.currentTimeMillis());
        if(rows.size()!=1 || !"RUNNING".equals(rows.getFirst().get("state")))
            throw new IllegalStateException("Running owned lease required for handoff");
        String attempt=String.valueOf(rows.getFirst().get("id"));
        String expected=".ai/handoffs/"+me.asu.aidev.authority.AuthorityRepository.hash(task)+"/"
                +me.asu.aidev.authority.AuthorityRepository.hash(attempt)+"/manifest.json";
        if(!expected.equals(ref.manifest())) throw new IllegalArgumentException("Handoff must identify this task and attempt");
        Input old=input(attempt);
        if(old!=null) {
            if(!Objects.equals(old.manifest(),ref.manifest()) || !Objects.equals(old.sha256(),ref.sha256()))
                throw new IllegalStateException("Handoff is immutable");
            return;
        }
        var work=tasks.find(new TaskId(task)).orElseThrow();
        jdbc.update("INSERT INTO execution_handoff(attempt_id,task_id,location,manifest_path,sha256) VALUES(?,?,?,?,?)",
                attempt,task,me.asu.aidev.execution.pricing.ModelPriceRepairDispatch.SUBJECT.equals(work.subjectType())
                        ? "worker-local:" + worker : locations.forTask(work),ref.manifest(),ref.sha256());
    }
    public Input input(String attempt) {
        if(attempt==null) return null;
        return jdbc.query("SELECT location,manifest_path,sha256 FROM execution_handoff WHERE attempt_id=?",
                (rs,row)->new Input(rs.getString(1),rs.getString(2),rs.getString(3)),attempt).stream().findFirst().orElse(null);
    }
    public void verifyReplay(String token,Reference ref) {
        var inputs=jdbc.query("""
                SELECT h.location,h.manifest_path,h.sha256 FROM execution_handoff h
                JOIN execution_attempt a ON a.id=h.attempt_id WHERE a.lease_token=?
                """,(rs,row)->new Input(rs.getString(1),rs.getString(2),rs.getString(3)),token);
        if(ref==null || inputs.size()!=1 || !inputs.getFirst().manifest().equals(ref.manifest())
                || !inputs.getFirst().sha256().equals(ref.sha256())) throw new IllegalStateException("Handoff replay conflict");
    }
}
