package me.asu.aidev.workflow.application;

import java.util.HashSet;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.workflow.domain.WorkflowReviewPolicy;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A reviewer must be independent of every execution covered by this review boundary. */
@Component
public class WorkflowReviewClaimPolicy implements WorkerTaskClaimPolicy {
    private final JdbcTemplate jdbc;private final WorkflowPlanRepository plans;
    public WorkflowReviewClaimPolicy(JdbcTemplate jdbc,WorkflowPlanRepository plans) {this.jdbc=jdbc;this.plans=plans;}
    private boolean review(WorkTask task) {return "flexible-plan".equals(task.subjectType())&&"workflow-stage-reviewer".equals(task.skillRef());}
    @Override public boolean canClaim(WorkTask task) {return !review(task);}
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        if(!review(task))return true;
        var plan=plans.find(task.subjectId());if(plan.isEmpty()||worker==null)return false;
        var position=WorkflowTaskPosition.parse(plan.get(),task.id());
        if(position.isEmpty()||!position.get().review())return false;
        var scope=new HashSet<>(WorkflowReviewPolicy.scope(plan.get().spec(),position.get().step()));
        for(var source:jdbc.queryForList("SELECT DISTINCT a.task_id FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE t.subject_type='flexible-plan' AND t.subject_id=? AND a.worker_id=?",String.class,plan.get().id(),worker.id().value())) {
            var work=WorkflowTaskPosition.parse(plan.get(),new TaskId(source));
            if(work.isPresent()&&!work.get().review()&&scope.contains(work.get().step()))return false;
        }
        return true;
    }
}
