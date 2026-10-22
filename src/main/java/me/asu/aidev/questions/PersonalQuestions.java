package me.asu.aidev.questions;

import java.util.*;
import me.asu.aidev.authority.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static me.asu.aidev.questions.QuestionContract.*;

/** The staged projection is inert. Existing exact action admission owns its eventual effect. */
@Service
public class PersonalQuestions {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ActionAdmissionService admission;
    private final FormalQuestions questions;private final TransactionTemplate tx;
    public PersonalQuestions(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,FormalQuestions questions,PlatformTransactionManager manager) {
        this.repo=repo;this.boundary=boundary;this.admission=admission;this.questions=questions;this.tx=new TransactionTemplate(manager);
    }
    public Object list(AuthorityBoundary.Principal principal,String plan) { return tx.execute(s->{boundary.lock();admission.readable(principal,"PLAN:"+plan);return questions.list(plan);}); }
    /** Called inside admission, including the first-execution window after inert staging commits. */
    public void validateEnvelope(AuthorityBoundary.Principal principal,ActionAdmissionService.Envelope input) {
        var rows=repo.jdbc().queryForList("SELECT * FROM formal_question_proposal WHERE owner_id=? AND request_id=?",principal.owner(),input.requestId());
        if(rows.isEmpty()) {
            if(input.action()==ActionScope.Action.A19_ANSWER) throw new IllegalArgumentException("A19 requires the original selected projection");
            return; // Existing independent A18 actions do not require a question.
        }
        var row=rows.getFirst();String raw=String.valueOf(row.get("projection_json"));String digest=AuthorityRepository.hash(raw);
        if(!principal.worker().equals(row.get("worker_id"))||!input.equals(repo.decode(String.valueOf(row.get("envelope_json")),ActionAdmissionService.Envelope.class))
                ||!digest.equals(row.get("projection_digest"))||!input.evidence().equals(List.of("question-projection:"+digest)))
            throw new IllegalStateException("ORIGINAL_QUESTION_ENVELOPE_CHANGED");
    }
    public ActionAdmissionService.Receipt submit(AuthorityBoundary.Principal principal,PersonalReply input) {
        tx.executeWithoutResult(status->{
            boundary.lock();var e=input.envelope();uuid(e.requestId());var projection=repo.decode(input.projectionJson(),Projection.class);var q=questions.get(projection.reply().questionId());
            admission.readable(principal,"PLAN:"+q.source().planId());
            if(!q.ownerId().equals(principal.owner())||!e.targetId().equals(q.source().planId())||e.taskId()!=null||e.step()!=null
                    ||!Set.of(ActionScope.Action.A19_ANSWER,ActionScope.Action.A18_BUDGET).contains(e.action())) throw new SecurityException("Exact question action required");
            String json=input.projectionJson(),digest=AuthorityRepository.hash(json);
            if(e.evidence()==null||!e.evidence().equals(List.of("question-projection:"+digest))) throw new IllegalArgumentException("Exact selected projection digest required");
            var old=repo.jdbc().queryForList("SELECT * FROM formal_question_proposal WHERE owner_id=? AND request_id=?",principal.owner(),e.requestId());
            if(!old.isEmpty()) {
                var r=old.getFirst();if(!json.equals(r.get("projection_json"))||!principal.worker().equals(r.get("worker_id"))||!repo.encode(e).equals(r.get("envelope_json"))) throw new IllegalStateException("QUESTION_PROJECTION_CONFLICT");
            } else repo.jdbc().update("INSERT INTO formal_question_proposal VALUES(?,?,?,?,?,?,?,?,?,?)",principal.owner(),principal.worker(),e.requestId(),json,digest,repo.encode(e),boundary.instance(),principal.generation(),
                    repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,principal.worker()),boundary.boot());
        });
        // Do not join admission to an outer transaction: rejected receipts need their own commit.
        return admission.apply(principal,input.envelope());
    }
    private Projection proposal(String request) {
        var p=boundary.principal();var row=repo.jdbc().queryForMap("SELECT * FROM formal_question_proposal WHERE owner_id=? AND request_id=?",p.owner(),request);
        if(!p.worker().equals(row.get("worker_id"))) throw new SecurityException("Question projection belongs to another personal identity");
        if(!Objects.equals(row.get("instance_id"),boundary.instance())||!Objects.equals(row.get("platform_boot"),boundary.boot())||((Number)row.get("worker_generation")).longValue()!=p.generation()
                ||!Objects.equals(row.get("worker_boot"),repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,p.worker()))) throw new SecurityException("Question projection process identity changed");
        String json=String.valueOf(row.get("projection_json"));if(!AuthorityRepository.hash(json).equals(row.get("projection_digest"))) throw new IllegalStateException("Question projection corrupted");
        return repo.decode(json,Projection.class);
    }
    public Question applyAnswer(String request,String plan) {
        var p=proposal(request);var q=questions.get(p.reply().questionId());
        if(q.kind()!=Kind.PROFESSIONAL||!q.source().planId().equals(plan)) throw new SecurityException("A19 only records professional answers");
        return questions.answer(p.reply(),boundary.principal().owner(),"personal:"+boundary.principal().worker(),"SECRETARY",true);
    }
    /** Invoked before increment, inside A18 admission. No proposal means an existing non-question A18 request. */
    public Question validateBudget(String request,ActionScope rule) {
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM formal_question_proposal WHERE owner_id=? AND request_id=?",Long.class,boundary.principal().owner(),request)==0) return null;
        var projection=proposal(request);var q=questions.get(projection.reply().questionId());var a=q.authorization();
        if(q.kind()!=Kind.AUTHORIZATION||a==null||!q.ownerId().equals(boundary.principal().owner())||!q.source().planId().equals(rule.targetId())
                ||!a.requestId().equals(request)||q.requestVersion()!=projection.reply().requestVersion()||q.state()!=State.OPEN||!projection.reply().rawAnswer().equals("APPROVE")
                ||!a.dimension().name().equals(rule.parameters().get("dimension"))||!Objects.equals(a.loopId(),rule.parameters().get("loopId"))
                ||!Long.toString(a.amount()).equals(rule.parameters().get("amount"))||!Long.toString(a.cumulativeCeiling()).equals(rule.parameters().get("cumulativeCeiling"))) throw new SecurityException("Budget question differs from the exact A18 scope");
        return q;
    }
    public void budgetApplied(String request,Question q) {
        if(q==null) return;var p=proposal(request);var a=new Answer(p.reply().rawAnswer(),"personal:"+boundary.principal().worker(),"SECRETARY",p.reply().messageId(),1,true,null);
        if(repo.jdbc().update("UPDATE formal_question SET state='ANSWERED',answer_revision=1,answer_json=?,updated_ms=? WHERE id=? AND state='OPEN' AND answer_revision=0",repo.encode(a),System.currentTimeMillis(),q.questionId())!=1) throw new IllegalStateException("Budget question changed");
        repo.jdbc().update("INSERT INTO formal_question_answer VALUES(?,?,?)",q.questionId(),1,repo.encode(a));questions.applied(q.questionId(),1,null);
    }
}
