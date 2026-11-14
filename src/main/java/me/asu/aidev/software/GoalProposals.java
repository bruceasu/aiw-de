package me.asu.aidev.software;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.goal.domain.GoalData.Content;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.questions.QuestionContract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit secretary input enters the ordinary wish pool; it never starts work. */
@Service
public class GoalProposals {
    public record Projection(String selectedGoal,String necessaryContext,String acceptance) {
        public Projection {
            if(selectedGoal==null||selectedGoal.isBlank())throw new IllegalArgumentException("Selected goal required");
            selectedGoal=bounded(selectedGoal);necessaryContext=bounded(necessaryContext);acceptance=bounded(acceptance);
        }
        private static String bounded(String value) {
            String text=Objects.toString(value,"");
            if(text.length()>8000||text.indexOf(0)>=0)throw new IllegalArgumentException("Selected projection limit");
            return text;
        }
    }
    public record Proposal(String requestId,String projectionJson,String digest) {
        public Proposal {
            QuestionContract.uuid(requestId);
            if(projectionJson==null||projectionJson.length()>28000)throw new IllegalArgumentException("Selected envelope limit");
            me.asu.aidev.execution.guard.GuardContract.hash(digest);
        }
    }
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ActionAdmissionService admission;
    private final GoalRepository wishes;
    public GoalProposals(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,GoalRepository wishes) {
        this.repo=repo;this.boundary=boundary;this.admission=admission;this.wishes=wishes;
    }
    private void machine(AuthorityBoundary.Principal principal,String boot) {
        admission.enabled(principal);
        if(!boundary.processBoot(principal.worker(),boot))throw new SecurityException("Current personal process required");
    }
    @Transactional public Object submit(AuthorityBoundary.Principal principal,String boot,Proposal input) {
        boundary.lock();machine(principal,boot);
        String digest=AuthorityRepository.hash(input.projectionJson());
        if(!digest.equals(input.digest()))throw new IllegalArgumentException("Exact raw selected projection required");
        var projection=repo.decode(input.projectionJson(),Projection.class);
        var old=repo.jdbc().queryForList("SELECT * FROM selected_goal_proposal WHERE owner_id=? AND request_id=?",principal.owner(),input.requestId());
        if(!old.isEmpty()) {
            var row=old.getFirst();
            if(!principal.worker().equals(row.get("worker_id"))||!input.projectionJson().equals(row.get("projection_json"))||!digest.equals(row.get("digest")))
                throw new IllegalStateException("GOAL_PROPOSAL_CONFLICT");
            return repo.decode((String)row.get("receipt_json"),Map.class);
        }
        String body=projection.selectedGoal()+"\n\nContext:\n"+projection.necessaryContext()+"\n\nAcceptance:\n"+projection.acceptance();
        if(body.length()>16000)throw new IllegalArgumentException("The selected requirement exceeds the wish pool's 16000-character limit. Shorten it before submitting.");
        String actor=repo.jdbc().queryForObject("SELECT actor FROM authority_owner WHERE id=?",String.class,principal.owner());
        var draft=wishes.create(actor);
        String title=projection.selectedGoal().strip().lines().findFirst().orElse("Wish");
        wishes.save(draft,title.substring(0,Math.min(160,title.length())),
            new Content(body,"Confirm scope and acceptance before generating a workflow","openai",List.of(),List.of()),false,false);
        var receipt=Map.of("requestId",input.requestId(),"state","RECORDED","ownerId",principal.owner(),"agentId",principal.worker(),
            "digest",digest,"platformInstanceId",boundary.instance(),"goalId",draft.id(),"path","/ui/goals?goal="+draft.id());
        repo.jdbc().update("INSERT INTO selected_goal_proposal(owner_id,request_id,worker_id,projection_json,digest,instance_id,generation,worker_boot,platform_boot,receipt_json) VALUES(?,?,?,?,?,?,?,?,?,?)",
            principal.owner(),input.requestId(),principal.worker(),input.projectionJson(),digest,boundary.instance(),principal.generation(),boot,boundary.boot(),repo.encode(receipt));
        return receipt;
    }
    @Transactional(readOnly=true) public Object receipt(AuthorityBoundary.Principal principal,String boot,String id) {
        machine(principal,boot);QuestionContract.uuid(id);
        var row=repo.jdbc().queryForMap("SELECT receipt_json FROM selected_goal_proposal WHERE owner_id=? AND request_id=? AND worker_id=?",principal.owner(),id,principal.worker());
        return repo.decode((String)row.get("receipt_json"),Map.class);
    }
}
