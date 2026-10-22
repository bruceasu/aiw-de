package me.asu.aidev.questions;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

/** A checkpoint is a retained input, never a successful upstream task or a new grant. */
@Component
public class QuestionClaimPolicy implements WorkerTaskClaimPolicy {
    private final ExecutionGuard guard;private final AuthorityRepository repo;private final TaskLineage lineage;private final FormalQuestions questions;
    public QuestionClaimPolicy(ExecutionGuard guard,AuthorityRepository repo,TaskLineage lineage,FormalQuestions questions) {this.guard=guard;this.repo=repo;this.lineage=lineage;this.questions=questions;}
    @Override public boolean canClaim(WorkTask task) {return valid(task,null);}
    @Override public boolean canClaim(WorkTask task,Worker worker) {return valid(task,worker);}
    private boolean valid(WorkTask task,Worker worker) {
        if(!guard.managed(task.id().value())) return true;
        var context=guard.context(task.id().value());var ancestors=lineage.ancestors(task.id()).stream().map(TaskId::value).toList();
        for(var ref:context.snapshot().references()) {
            if(!Set.of("question","checkpoint").contains(ref.kind())) continue;
            if(worker!=null&&!worker.capabilities().contains(QuestionContract.CAPABILITY)) return false;
            if(ref.kind().equals("checkpoint")) {
                if(!ancestors.contains(ref.id())) return false;
                var rows=repo.jdbc().queryForList("SELECT s.checkpoint_json,y.state FROM formal_question_slot s JOIN execution_yield y ON y.task_id=s.source_task_id WHERE s.source_task_id=?",ref.id());
                if(rows.size()!=1||!"ACKNOWLEDGED".equals(rows.getFirst().get("state"))) return false;
                String raw=String.valueOf(rows.getFirst().get("checkpoint_json"));var cp=repo.decode(raw,QuestionContract.Checkpoint.class);
                if(!ref.sha256().equals(AuthorityRepository.hash(raw))||!ref.revision().equals(cp.resultRevision())||worker!=null&&!worker.capabilities().contains(cp.storageCapability())) return false;
            } else {
                var q=questions.get(ref.id());if(!q.ownerId().equals(context.ownerId())||!q.source().planId().equals(context.planId())||!ancestors.contains(q.source().taskId())||q.state()!=QuestionContract.State.APPLIED) return false;
                var history=repo.jdbc().queryForList("SELECT answer_json FROM formal_question_answer WHERE question_id=? AND answer_revision=?",String.class,q.questionId(),Long.parseLong(ref.revision()));
                if(history.size()!=1||!ref.sha256().equals(AuthorityRepository.hash(history.getFirst()))) return false;
            }
        }return true;
    }
}
