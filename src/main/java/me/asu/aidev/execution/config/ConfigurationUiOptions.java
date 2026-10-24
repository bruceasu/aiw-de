package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.springframework.stereotype.Component;
import static me.asu.aidev.execution.config.ConfigurationContract.*;

/** Read-only form choices. Personal content is never a generic option payload. */
@Component
public class ConfigurationUiOptions {
    private final AuthorityRepository repo;
    private final ExecutionConfigurations configs;
    private final AgentFactory factory;
    private final WorkflowPlanService plans;

    public ConfigurationUiOptions(AuthorityRepository repo,ExecutionConfigurations configs,AgentFactory factory,WorkflowPlanService plans) {
        this.repo=repo;this.configs=configs;this.factory=factory;this.plans=plans;
    }
    public record RepositoryChoice(String ref,List<String> baselines) {}
    public record PlanChoice(String id,String title,long policyRevision,boolean paused,boolean revoked,long expiresAt,
                             String url,List<RepositoryChoice> repositories) {}
    public record TaskChoice(String id,String title,String planId,String repositoryRef,String baseRevision,String state) {}
    public record AssetChoice(String kind,String ref,String version,String digest,String repositoryRef,String title) {}
    public record SavedChoice(String repositoryRef,String methodName,PreparedConfigRef reference) {}
    public record DraftChoice(Intent intent) {}

    public Map<String,Object> snapshot() {
        String owner=configs.currentOwner();
        String actor=repo.actor(owner);
        Map<String,WorkflowPlan> ownedPlans=new HashMap<>();
        plans.list().stream().filter(p->actor.equals(p.createdBy())).forEach(p->ownedPlans.put(p.id(),p));
        var tasks=repo.jdbc().queryForList("""
                SELECT t.id,t.instruction,t.repository_ref,t.base_revision,t.state,c.plan_id
                FROM work_task t JOIN execution_context c ON c.task_id=t.id
                JOIN execution_policy p ON p.plan_id=c.plan_id WHERE p.owner_id=? ORDER BY t.id
                """,owner).stream().map(row->new TaskChoice((String)row.get("id"),title((String)row.get("instruction")),
                    (String)row.get("plan_id"),(String)row.get("repository_ref"),(String)row.get("base_revision"),(String)row.get("state"))).toList();
        var choices=new ArrayList<PlanChoice>();
        for(var row:repo.jdbc().queryForList("SELECT * FROM execution_policy WHERE owner_id=? ORDER BY plan_id",owner)) {
            var policy=repo.decode((String)row.get("policy_json"),GuardContract.Policy.class);
            var plan=ownedPlans.get(policy.planId());
            var repositories=new ArrayList<RepositoryChoice>();
            for(String ref:policy.rules().stream().map(GuardContract.Rule::repositoryRef).distinct().sorted().toList()) {
                Set<String> bases=new LinkedHashSet<>();
                if(plan!=null) plan.spec().steps().stream().filter(s->ref.equals(s.repositoryRef()))
                        .map(s->s.baseRevision()).filter(ConfigurationUiOptions::baseline).forEach(bases::add);
                tasks.stream().filter(t->t.planId().equals(policy.planId()) && ref.equals(t.repositoryRef()))
                        .map(TaskChoice::baseRevision).filter(ConfigurationUiOptions::baseline).forEach(bases::add);
                repositories.add(new RepositoryChoice(ref,List.copyOf(bases)));
            }
            choices.add(new PlanChoice(policy.planId(),plan==null?policy.planId():title(plan.spec().goal()),policy.revision(),
                    AuthorityRepository.flag(row.get("paused")),AuthorityRepository.flag(row.get("revoked")),policy.expiresAt(),
                    plan==null?"/ui/software-development":"/ui/plans/"+java.net.URLEncoder.encode(plan.id(),java.nio.charset.StandardCharsets.UTF_8),List.copyOf(repositories)));
        }
        var assets=new ArrayList<AssetChoice>();
        configs.catalogue().assets().stream().filter(a->a.kind().equals("skill"))
                .forEach(a->assets.add(new AssetChoice(a.kind(),a.ref(),a.version(),a.digest(),null,a.ref())));
        for(String raw:repo.jdbc().queryForList("SELECT asset_json FROM execution_asset WHERE owner_id=? ORDER BY kind,ref,version",String.class,owner)) {
            var asset=repo.decode(raw,Asset.class);
            assets.add(new AssetChoice(asset.kind(),asset.ref(),asset.version(),asset.digest(),asset.repositoryRef(),title(asset.content())));
        }
        var saved=new ArrayList<SavedChoice>();
        for(var row:repo.jdbc().queryForList("SELECT content_json,digest FROM execution_configuration WHERE owner_id=? ORDER BY id,revision",owner)) {
            var candidate=repo.decode((String)row.get("content_json"),Content.class).candidate();
            var ref=new PreparedConfigRef(candidate.id(),candidate.revision(),(String)row.get("digest"),PROTOCOL,
                    candidate.method().resultKind(),candidate.skills(),candidate.knowledge(),candidate.toolRequirements());
            saved.add(new SavedChoice(candidate.repositoryRef(),candidate.method().name(),ref));
        }
        var drafts=repo.jdbc().queryForList("SELECT intent_json FROM execution_preparation WHERE owner_id=? ORDER BY intent_id,intent_version",String.class,owner)
                .stream().map(raw->new DraftChoice(repo.decode(raw,Intent.class))).toList();
        return Map.of("plans",choices,"models",factory.supportedProfiles(),"assets",assets,"saved",saved,"drafts",drafts,
                "upstream",tasks.stream().filter(t->t.state().equals("COMPLETED")).toList());
    }
    private static boolean baseline(String value) { return value!=null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}"); }
    private static String title(String value) {
        if(value==null || value.isBlank()) return "未命名";
        String first=value.strip().split("\\R",2)[0].replaceFirst("^#+\\s*","");
        return first.length()>100?first.substring(0,100)+"…":first;
    }
}
