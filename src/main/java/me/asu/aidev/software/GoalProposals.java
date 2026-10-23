package me.asu.aidev.software;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.questions.QuestionContract;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.graph.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Inert selected data. Only a current human can adopt it through the existing graph draft boundary. */
@Service
public class GoalProposals {
    public record Projection(String selectedGoal,String necessaryContext,String acceptance){
        public Projection {text(selectedGoal,8000);necessaryContext=optional(necessaryContext);acceptance=optional(acceptance);}
        private static String optional(String value){String v=value==null?"":value;if(v.length()>8000||v.indexOf('\0')>=0)throw new IllegalArgumentException("Selected projection limit");return v;}
    }
    public record Proposal(String requestId,String projectionJson,String digest){public Proposal {QuestionContract.uuid(requestId);text(projectionJson,28000);me.asu.aidev.execution.guard.GuardContract.hash(digest);} }
    public record Adopt(String ownerId,String proposalRequestId,String digest,String planId,String requestId){public Adopt {text(ownerId,128);QuestionContract.uuid(proposalRequestId);QuestionContract.uuid(requestId);me.asu.aidev.execution.guard.GuardContract.hash(digest);GraphContract.id(planId);} }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ActionAdmissionService admission;
    private final ExecutionGuard guard;private final WebConsoleSecurityService security;private final GraphPlans plans;
    public GoalProposals(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,ExecutionGuard guard,WebConsoleSecurityService security,GraphPlans plans){this.repo=repo;this.boundary=boundary;this.admission=admission;this.guard=guard;this.security=security;this.plans=plans;}
    public void human(String owner,boolean write){guard.owner(owner);boolean op=!security.currentDelegationAuthority(boundary.human(),"A19_ANSWER").isEmpty();if(!op&&(write||security.currentDelegationAuthority(boundary.human(),"A18_BUDGET").isEmpty()))throw new SecurityException("Current owner role required");}
    private void machine(AuthorityBoundary.Principal principal,String boot){admission.enabled(principal);if(!boundary.processBoot(principal.worker(),boot))throw new SecurityException("Current personal process required");}
    @Transactional public Object submit(AuthorityBoundary.Principal principal,String boot,Proposal input){
        boundary.lock();machine(principal,boot);String digest=AuthorityRepository.hash(input.projectionJson());if(!digest.equals(input.digest()))throw new IllegalArgumentException("Exact raw selected projection required");repo.decode(input.projectionJson(),Projection.class);
        var old=repo.jdbc().queryForList("SELECT * FROM selected_goal_proposal WHERE owner_id=? AND request_id=?",principal.owner(),input.requestId());
        if(!old.isEmpty()){var row=old.getFirst();if(!principal.worker().equals(row.get("worker_id"))||!input.projectionJson().equals(row.get("projection_json"))||!digest.equals(row.get("digest")))throw new IllegalStateException("GOAL_PROPOSAL_CONFLICT");return repo.decode((String)row.get("receipt_json"),Map.class);}
        var receipt=Map.of("requestId",input.requestId(),"state","RECORDED","ownerId",principal.owner(),"workerId",principal.worker(),"digest",digest,"platformInstanceId",boundary.instance());
        repo.jdbc().update("INSERT INTO selected_goal_proposal(owner_id,request_id,worker_id,projection_json,digest,instance_id,generation,worker_boot,platform_boot,receipt_json) VALUES(?,?,?,?,?,?,?,?,?,?)",principal.owner(),input.requestId(),principal.worker(),input.projectionJson(),digest,boundary.instance(),principal.generation(),boot,boundary.boot(),repo.encode(receipt));return receipt;
    }
    @Transactional public Object receipt(AuthorityBoundary.Principal principal,String boot,String id){
        boundary.lock();machine(principal,boot);QuestionContract.uuid(id);var row=repo.jdbc().queryForMap("SELECT * FROM selected_goal_proposal WHERE owner_id=? AND request_id=? AND worker_id=?",principal.owner(),id,principal.worker());return repo.decode((String)row.get("receipt_json"),Map.class);
    }
    @Transactional public Object list(String owner){boundary.lock();human(owner,false);return repo.jdbc().queryForList("SELECT request_id,worker_id,projection_json,digest,instance_id,generation,adopted_plan FROM selected_goal_proposal WHERE owner_id=? ORDER BY request_id",owner);}
    @Transactional public Object adopt(Adopt input){
        boundary.lock();human(input.ownerId(),true);var row=repo.jdbc().queryForMap("SELECT * FROM selected_goal_proposal WHERE owner_id=? AND request_id=?",input.ownerId(),input.proposalRequestId());
        if(!input.digest().equals(row.get("digest"))||!input.digest().equals(AuthorityRepository.hash((String)row.get("projection_json"))))throw new IllegalStateException("GOAL_PROJECTION_CHANGED");
        String semantic=AuthorityRepository.hash(repo.encode(List.of(input.ownerId(),input.proposalRequestId(),input.digest(),input.planId())));
        if(row.get("adoption_receipt")!=null){if(!semantic.equals(row.get("adoption_digest")))throw new IllegalStateException("GOAL_ALREADY_ADOPTED");return repo.decode((String)row.get("adoption_receipt"),Map.class);}
        var projection=repo.decode((String)row.get("projection_json"),Projection.class);
        var spec=new GraphContract.Spec(GraphContract.PROTOCOL,projection.selectedGoal(),projection.necessaryContext().isBlank()?"Scope needs user clarification.":projection.necessaryContext(),projection.acceptance().isBlank()?"Acceptance needs user clarification.":projection.acceptance(),
            List.of(new GraphContract.Region("ROOT",null,List.of(),List.of(),null,null,null,List.of(),List.of())),List.of());
        var change=new GraphContract.Change(input.requestId(),input.ownerId(),input.planId(),0,"SELECTED_GOAL:"+input.proposalRequestId(),"Current human adopted this exact selected goal projection",null,spec);
        var receipt=plans.change(change);
        if(repo.jdbc().update("UPDATE selected_goal_proposal SET adopted_plan=?,adoption_digest=?,adoption_receipt=? WHERE owner_id=? AND request_id=? AND adopted_plan IS NULL",input.planId(),semantic,repo.encode(receipt),input.ownerId(),input.proposalRequestId())!=1)throw new IllegalStateException("GOAL_ADOPTION_CHANGED");
        return receipt;
    }
}
