package me.asu.aidev.authority;

import java.util.*;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.autonomous.application.GitRevisionResolver;
import me.asu.aidev.autonomous.application.WorkspaceRegistry;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.guard.GuardContract;
import org.springframework.stereotype.Service;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** A visible human confirmation creates a policy; previews only resolve registered repositories. */
@Service
public class SecretaryExecutionSetup {
    public record Selection(String repositoryRef,String pathPrefix,String baseRevision,long expectedPolicyRevision,
                            boolean writeAllowed,boolean gitAllowed) {}
    public record Preview(String planId,String repositoryRef,String pathPrefix,boolean writeAllowed,boolean gitAllowed) {}
    private final AuthorityRepository repo;
    private final AuthorityGrantService grants;
    private final WorkflowPlanService plans;
    private final WorkspaceRegistry workspaces;
    private final ManagedWorkspaceService managed;
    private final GitRevisionResolver revisions;
    private final ExecutionGuard guard;
    public SecretaryExecutionSetup(AuthorityRepository repo,AuthorityGrantService grants,WorkflowPlanService plans,
            WorkspaceRegistry workspaces,ManagedWorkspaceService managed,GitRevisionResolver revisions,ExecutionGuard guard) {
        this.repo=repo;this.grants=grants;this.plans=plans;this.workspaces=workspaces;this.managed=managed;this.revisions=revisions;this.guard=guard;
    }
    public List<Map<String,String>> repositories() {
        var refs=new TreeSet<String>();
        workspaces.getWorkspaces().values().forEach(workspace->refs.addAll(workspace.getRepositories().keySet()));
        managed.targets().forEach(target->refs.add(target.repositoryRef()));
        var result=new ArrayList<Map<String,String>>();
        for(String ref:refs) {
            try { var location=workspaces.location(ref);result.add(Map.of("ref",ref,"path",location.repositoryPath().toString(),"workspace",location.workspaceName())); }
            catch(IllegalArgumentException ignored) { /* Ambiguous definitions must be repaired in Workspace settings. */ }
        }
        return result;
    }
    private void owned(String plan,String actor) {
        grants.human(actor,"A05");
        if(!Objects.equals(plans.get(plan).createdBy(),actor))throw new SecurityException("Current Plan creator required");
    }
    public Map<String,Object> state(String plan) {
        var rows=repo.jdbc().queryForList("SELECT revision,paused,revoked,expires_ms,policy_json FROM execution_policy WHERE plan_id=?",plan);
        if(rows.isEmpty())return Map.of("revision",0);
        var row=rows.getFirst();var policy=repo.decode(String.valueOf(row.get("policy_json")),Policy.class);
        return Map.of("revision",row.get("revision"),"paused",AuthorityRepository.flag(row.get("paused")),
                "revoked",AuthorityRepository.flag(row.get("revoked")),"expiresAt",row.get("expires_ms"),"rules",policy.rules());
    }
    public Map<String,Object> preview(Preview input,String actor) {
        owned(input.planId(),actor);text(input.repositoryRef(),120);path(input.pathPrefix());
        if(!guard.machineConnectionConfigured())throw new IllegalStateException("平台尚未配置专业 Worker 连接令牌。请先运行 scripts/configure-local-worker-token.ps1 并重启平台，再保存文件执行范围。");
        var location=workspaces.location(input.repositoryRef());
        String commit;
        try { commit=revisions.resolve(input.repositoryRef(),"HEAD"); }
        catch(IllegalArgumentException|IllegalStateException error) {
            throw new IllegalStateException("无法读取此仓库的 Git 基线。请确认项目已登记、Git 可用且已有提交；向导不会初始化仓库或提交现有文件。",error);
        }
        checkSource(location.repositoryPath().toString(),commit,input.pathPrefix());
        long revision=((Number)state(input.planId()).get("revision")).longValue();
        return Map.of("repositoryRef",input.repositoryRef(),"repositoryPath",location.repositoryPath().toString(),
                "pathPrefix",input.pathPrefix(),"baseRevision",commit,"expectedPolicyRevision",revision,
                "writeAllowed",input.writeAllowed(),"gitAllowed",input.gitAllowed());
    }
    private void checkSource(String directory,String commit,String prefix) {
        String object=Set.of("", ".").contains(prefix)?commit+"^{tree}":commit+":"+prefix;
        if(git(directory,List.of("cat-file","-e",object))!=0)
            throw new IllegalStateException("所选文件或目录不在此 Git 基线中，请核对相对路径，并先将需要处理的文件保存为提交。");
        if(git(directory,List.of("diff","--quiet",commit,"--",prefix.isEmpty()?".":prefix))!=0)
            throw new IllegalStateException("所选文件存在尚未提交的修改。请先保存为 Git 提交，再准备执行范围，避免专业任务遗漏本地修改。");
    }
    private int git(String directory,List<String> arguments) {
        var argv=new ArrayList<>(List.of("git","-C",directory));argv.addAll(arguments);
        Process process=null;
        try {
            process=new ProcessBuilder(argv).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(15,TimeUnit.SECONDS))throw new IllegalStateException("仓库文件检查超时，请核对 Git 状态。");
            return process.exitValue();
        } catch(java.io.IOException error) { throw new IllegalStateException("无法运行 Git 检查，请核对平台的 Git 安装。",error); }
        catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException("仓库文件检查已中断。",error);}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    public void confirm(String owner,String plan,long expires,Selection input,String actor) {
        owned(plan,actor);path(input.pathPrefix());
        if(input.baseRevision()==null || !input.baseRevision().matches("([0-9a-f]{40}|[0-9a-f]{64})"))
            throw new IllegalArgumentException("请先准备并核对执行基线。");
        if(!revisions.resolve(input.repositoryRef(),input.baseRevision()).equals(input.baseRevision()))
            throw new IllegalArgumentException("仓库基线已无法核对，请重新准备执行范围。");
        var rows=repo.jdbc().queryForList("SELECT paused,revoked,policy_json FROM execution_policy WHERE plan_id=?",plan);
        if(!rows.isEmpty() && (AuthorityRepository.flag(rows.getFirst().get("paused"))||AuthorityRepository.flag(rows.getFirst().get("revoked"))))
            throw new IllegalStateException("执行范围已暂停或撤销，请先在执行控制中核对；向导不会解除暂停。");
        var rules=new ArrayList<Rule>();
        for(Tool tool:List.of(Tool.READ,Tool.SEARCH))rules.add(new Rule(tool,input.repositoryRef(),input.pathPrefix(),input.baseRevision()));
        if(input.writeAllowed())rules.add(new Rule(Tool.WRITE,input.repositoryRef(),input.pathPrefix(),input.baseRevision()));
        if(input.gitAllowed())rules.add(new Rule(Tool.GIT,input.repositoryRef(),".",input.baseRevision()));
        for(Tool tool:List.of(Tool.MODEL,Tool.QUESTION))rules.add(new Rule(tool,input.repositoryRef(),".",input.baseRevision()));
        var budget=rows.isEmpty()?null:repo.decode(String.valueOf(rows.getFirst().get("policy_json")),Policy.class).budget();
        var policy=new Policy(GuardContract.PROTOCOL,owner,plan,input.expectedPolicyRevision()+1,expires,rules,List.of(),budget);
        if(!guard.preparationBudget(policy))throw new IllegalStateException("平台启用了预算约束，请先在高级执行设置中确认预算和模型定价；已有用量不会重置。");
        guard.confirm(policy);
    }
}
