package me.asu.aidev.workflow.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.execution.domain.TaskInput;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Local delivery is part of the approved work; remote pushes and destructive recovery are excluded. */
@Service
public class WorkflowGitDeliveryService {
    public record Item(String repositoryRef, String taskId, String baseRevision, String resultRevision, List<String> files,
                       String state, String message) {}
    public record View(String state, List<Item> items) {}
    public record Preview(long planVersion, String repositoryRef, String repositoryPath, String resultRevision, String targetBranch,
                          String beforeRevision, List<String> files, boolean alreadyMerged, String blockedReason) {}
    public record MergeRequest(long planVersion, String repositoryRef, String repositoryPath, String resultRevision, String targetBranch,
                               String beforeRevision, String requestId) {}
    record Git(int code, String text) {}
    private final WorkflowPlanRepository plans;
    private final ObjectProvider<WorkflowPlanService> workflow;
    private final ObjectProvider<FactoryPlanLifecycle> factory;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final ManagedWorkspaceService workspaces;
    private final JdbcTemplate jdbc;
    private final AuthorityBoundary authority;
    private final TransactionTemplate transaction;

    public WorkflowGitDeliveryService(WorkflowPlanRepository plans, ObjectProvider<WorkflowPlanService> workflow,
            ObjectProvider<FactoryPlanLifecycle> factory, WorkTaskRepository tasks, ExecutionAttemptRepository attempts,
            ManagedWorkspaceService workspaces, JdbcTemplate jdbc, AuthorityBoundary authority,
            PlatformTransactionManager transactions) {
        this.plans=plans; this.workflow=workflow; this.factory=factory; this.tasks=tasks; this.attempts=attempts;
        this.workspaces=workspaces; this.jdbc=jdbc; this.authority=authority;
        this.transaction=new TransactionTemplate(transactions);
    }

    private WorkflowPlan plan(String id) {
        return plans.find(id).orElseThrow(() -> new WorkflowPlanService.PlanNotFound(id));
    }

    public View view(String id) {
        var plan=plan(id);
        if (plan.state()!=WorkflowPlan.State.COMPLETED) return new View("NOT_REQUIRED",List.of());
        var selected=new LinkedHashMap<String,TaskInput>();
        if (factory.getObject().view(id)!=null) {
            for (var binding:factory.getObject().currentTasks(id)) {
                if ("coding-work-package-executor".equals(binding.task().skillRef()))
                    attempts.findCompletedInput(binding.task().id()).ifPresent(input -> select(selected,input));
            }
        } else {
            var accepted=workflow.getObject().acceptedInputs(plan,plan.spec().steps().size()-1);
            for (var step:plan.spec().steps()) {
                if (!"coding-work-package-executor".equals(step.skillRef())) continue;
                for (var taskId:accepted) {
                    var task=tasks.find(taskId).orElseThrow();
                    if (task.skillRef().equals(step.skillRef()) && task.id().value().startsWith(id+"~"+step.key()+"~work-"))
                        attempts.findCompletedInput(taskId).ifPresent(input -> select(selected,input));
                }
            }
        }
        var items=new ArrayList<Item>();
        for (var input:selected.values()) {
            var receipt=jdbc.queryForList("SELECT state,message FROM plan_git_delivery WHERE plan_id=? AND repository_ref=? AND result_revision=?",
                    id,input.repositoryRef(),input.resultRevision());
            items.add(new Item(input.repositoryRef(),input.taskId().value(),input.baseRevision(),input.resultRevision(),input.changedFiles(),
                    receipt.isEmpty()?"AWAITING_MERGE":String.valueOf(receipt.getFirst().get("state")),
                    receipt.isEmpty()?"Execution passed; local delivery is pending.":String.valueOf(receipt.getFirst().get("message"))));
        }
        String state=items.isEmpty()?"NOT_REQUIRED":items.stream().allMatch(item -> item.state().equals("DELIVERED"))?"DELIVERED"
                :items.stream().anyMatch(item -> item.state().equals("UNKNOWN"))?"DELIVERY_UNKNOWN"
                :items.stream().anyMatch(item -> item.state().equals("MERGING"))?"MERGING"
                :items.stream().anyMatch(item -> item.state().equals("BLOCKED"))?"DELIVERY_BLOCKED":"AWAITING_MERGE";
        return new View(state,List.copyOf(items));
    }

    private void select(Map<String,TaskInput> selected,TaskInput input) {
        if (input.completionVerdict()==me.asu.aidev.execution.domain.CompletionVerdict.PASS
                && input.repositoryRef()!=null && input.resultRevision()!=null && !input.resultRevision().equals(input.baseRevision())
                && !input.changedFiles().isEmpty()) selected.put(input.repositoryRef(),input);
    }

    public boolean pending(String id) {
        return !Set.of("NOT_REQUIRED","DELIVERED").contains(view(id).state());
    }

    private Item candidate(String id,String repository) {
        return view(id).items().stream().filter(item -> item.repositoryRef().equals(repository)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No accepted Git result for this repository"));
    }

    private Path repository(String ref) {
        var registered=workspaces.findTarget(ref).orElseThrow(() -> new IllegalStateException("Registered local repository required"));
        try {
            Path path=Path.of(registered.repositoryPath()).toRealPath();
            if (!Files.isDirectory(path) || !Path.of(required(path,"rev-parse","--show-toplevel").strip()).toRealPath().equals(path))
                throw new IllegalStateException("Exact registered repository root required");
            return path;
        } catch (java.io.IOException e) { throw new IllegalStateException("Registered repository is not available on this machine"); }
    }

    public Preview preview(String id,String ref) {
        var plan=plan(id); var item=candidate(id,ref);
        if (!item.resultRevision().matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalStateException("Immutable Git result required");
        Path path=repository(ref);
        String branch=required(path,"symbolic-ref","--quiet","--short","HEAD").strip();
        String head=required(path,"rev-parse","HEAD").strip();
        String result=required(path,"rev-parse","--verify",item.resultRevision()+"^{commit}").strip();
        if (!result.equals(item.resultRevision())) throw new IllegalStateException("Recorded Git result is unavailable");
        boolean merged=git(path,"merge-base","--is-ancestor",result,head).code()==0;
        List<String> changed=merged?List.of():split(required(path,"diff","--no-ext-diff","--no-textconv","--name-only","-z",head,result,"--"));
        String blocked=merged?null:filterBlock(path,head,result,changed);
        if (!Set.of("AWAITING_MERGE","READY","BLOCKED","DELIVERED").contains(item.state())) blocked="An earlier merge has an unresolved outcome. Inspect it before proceeding.";
        else if (!merged && (item.baseRevision()==null || git(path,"merge-base","--is-ancestor",item.baseRevision(),head).code()!=0))
            blocked="The target branch no longer contains the original task baseline. Review it manually.";
        else if (blocked==null && !merged && git(path,"diff","--cached","--quiet","--").code()!=0) blocked="The original worktree has staged changes. Preserve them before merging.";
        else if (blocked==null && !merged && split(requiredWithoutFilters(path,"diff","--no-ext-diff","--no-textconv","--name-only","-z","HEAD","--")).stream().anyMatch(changed::contains))
            blocked="Local changes overlap the reviewed result. Preserve them before merging.";
        else if (blocked==null && !merged && !changed.isEmpty()) {
            var args=new ArrayList<String>(List.of("ls-files","--others","-z","--"));args.addAll(changed);
            if (!required(path,args.toArray(String[]::new)).isEmpty()) blocked="Untracked files overlap the reviewed result. Preserve them before merging.";
        }
        if (!merged && git(path,"config","--get-regexp","^merge\\..*\\.driver$").code()!=1)
            blocked="Custom merge drivers require a manual merge.";
        return new Preview(plan.version(),ref,path.toString(),result,branch,head,changed,merged,blocked);
    }

    public View merge(String id,MergeRequest input,String actor) { return merge(id,input,actor,false); }

    private View merge(String id,MergeRequest input,String actor,boolean automatic) {
        if (input.requestId()==null || !input.requestId().matches("[A-Za-z0-9-]{16,64}"))
            throw new IllegalArgumentException("Merge request identity required");
        var plan=plan(id);
        boolean owner=automatic || actor!=null && (actor.equals(plan.createdBy()) || jdbc.queryForObject("""
                SELECT COUNT(*) FROM authority_managed_plan m JOIN authority_owner o ON o.id=m.owner_id
                 WHERE m.plan_id=? AND o.actor=?
                """,Long.class,id,actor)>0 || jdbc.queryForObject("""
                SELECT COUNT(*) FROM execution_policy p JOIN authority_owner o ON o.id=p.owner_id
                 WHERE p.plan_id=? AND o.actor=?
                """,Long.class,id,actor)>0);
        if (!automatic && (authority.delegated() || authority.human()==null || !Objects.equals(actor,authority.human()) || !owner))
            throw new SecurityException("The authenticated Plan owner is required for manual delivery");
        var existing=jdbc.queryForList("SELECT request_id,state,target_branch,before_revision FROM plan_git_delivery WHERE plan_id=? AND repository_ref=? AND result_revision=?",
                id,input.repositoryRef(),input.resultRevision());
        if (!existing.isEmpty() && "DELIVERED".equals(existing.getFirst().get("state"))) return view(id);
        var preview=preview(id,input.repositoryRef());
        if (input.planVersion()!=preview.planVersion() || !Objects.equals(input.resultRevision(),preview.resultRevision())
                || !Objects.equals(input.repositoryPath(),preview.repositoryPath())
                || !Objects.equals(input.targetBranch(),preview.targetBranch()) || !Objects.equals(input.beforeRevision(),preview.beforeRevision()))
            throw new IllegalStateException("The merge target changed. Review a new preview before merging.");
        if (preview.blockedReason()!=null) throw new IllegalStateException(preview.blockedReason());
        Path path=repository(input.repositoryRef());
        // Commit the durable intent before touching Git. A failed/unknown external write is never rolled back in SQL.
        transaction.executeWithoutResult(status -> {
            authority.lock();
            if (plan(id).version()!=input.planVersion()) throw new IllegalStateException("Plan version changed");
            int reserved=jdbc.update("""
                    UPDATE plan_git_delivery SET request_id=?,state='MERGING',actor=?,repository_path=?,target_branch=?,before_revision=?,message=?,updated_ms=?
                     WHERE plan_id=? AND repository_ref=? AND result_revision=? AND state IN ('READY','BLOCKED')
                    """,input.requestId(),actor,input.repositoryPath(),input.targetBranch(),input.beforeRevision(),
                    "Local delivery is starting",Instant.now().toEpochMilli(),id,input.repositoryRef(),input.resultRevision());
            if (reserved==0 && jdbc.update("""
                    INSERT INTO plan_git_delivery
                    (plan_id,repository_ref,repository_path,result_revision,request_id,state,actor,target_branch,before_revision,after_revision,message,created_ms,updated_ms)
                    VALUES(?,?,?,?,?,'MERGING',?,?,?,NULL,?,?,?)
                    ON CONFLICT(plan_id,repository_ref,result_revision) DO NOTHING
                    """,id,input.repositoryRef(),input.repositoryPath(),input.resultRevision(),input.requestId(),actor,input.targetBranch(),input.beforeRevision(),
                    "Local delivery started; merge result is not yet recorded",Instant.now().toEpochMilli(),Instant.now().toEpochMilli())!=1)
                throw new IllegalStateException("An earlier merge request already exists; reconcile its outcome instead of retrying");
        });
        try {
            if (!required(path,"symbolic-ref","--quiet","--short","HEAD").strip().equals(input.targetBranch())
                    || !required(path,"rev-parse","HEAD").strip().equals(input.beforeRevision()))
                throw new IllegalStateException("Merge target changed before delivery");
            String dirty=digest(requiredWithoutFilters(path,"diff","--no-ext-diff","--no-textconv","--binary","HEAD","--"));
            if (!preview.alreadyMerged()) {
                boolean fastForward=git(path,"merge-base","--is-ancestor",input.beforeRevision(),input.resultRevision()).code()==0;
                if (!fastForward) {
                    var check=gitWithoutFilters(path,"merge-tree","--write-tree",input.beforeRevision(),input.resultRevision());
                    if (check.code()!=0) {
                        jdbc.update("UPDATE plan_git_delivery SET state='BLOCKED',message=?,updated_ms=? WHERE request_id=? AND state='MERGING'",
                                "Merge conflicts or unsupported preflight require attention; the original worktree was not changed",Instant.now().toEpochMilli(),input.requestId());
                        return view(id);
                    }
                    required(path,"var","GIT_AUTHOR_IDENT");
                }
                requiredWithoutFilters(path,"-c","branch."+input.targetBranch()+".mergeOptions=","merge",fastForward?"--ff-only":"--no-ff",
                        "--strategy=ort","--no-verify-signatures","--no-edit","--no-autostash","--no-overwrite-ignore",input.resultRevision());
            }
            String after=required(path,"rev-parse","HEAD").strip();
            if (!required(path,"symbolic-ref","--quiet","--short","HEAD").strip().equals(input.targetBranch())
                    || git(path,"merge-base","--is-ancestor",input.resultRevision(),after).code()!=0
                    || !dirty.equals(digest(requiredWithoutFilters(path,"diff","--no-ext-diff","--no-textconv","--binary","HEAD","--"))))
                throw new IllegalStateException("Merge outcome needs manual reconciliation");
            jdbc.update("UPDATE plan_git_delivery SET state='DELIVERED',after_revision=?,message=?,updated_ms=? WHERE request_id=? AND state='MERGING'",
                    after,"Reviewed result merged locally; no remote push",Instant.now().toEpochMilli(),input.requestId());
        } catch (RuntimeException error) {
            jdbc.update("UPDATE plan_git_delivery SET state='UNKNOWN',message=?,updated_ms=? WHERE request_id=? AND state='MERGING'",
                    "Merge outcome requires manual inspection; no automatic retry",Instant.now().toEpochMilli(),input.requestId());
            throw error;
        }
        return view(id);
    }

    /** Pin the original target while the Plan is confirmed, before task execution can outlive a branch change. */
    public void capture(WorkflowPlan plan) {
        for (var ref:plan.spec().steps().stream().filter(step -> "coding-work-package-executor".equals(step.skillRef()))
                .map(step -> step.repositoryRef()).filter(Objects::nonNull).distinct().toList()) {
            try {
                Path path=repository(ref);
                String branch=required(path,"symbolic-ref","--quiet","--short","HEAD").strip();
                String head=required(path,"rev-parse","HEAD").strip();
                jdbc.update("INSERT INTO plan_git_delivery_target VALUES(?,?,?,?,?) ON CONFLICT(plan_id,repository_ref) DO NOTHING",
                        plan.id(),ref,path.toString(),branch,head);
            } catch (RuntimeException unavailable) {
                // Execution can still proceed remotely. Delivery must then wait for an explicit, resolvable local target.
            }
        }
    }

    /** Only newly completed work is enqueued. Historical results are never merged by scanning old Plans. */
    public void enqueue(String id) {
        for (var item:view(id).items()) {
            var targets=jdbc.queryForList("SELECT * FROM plan_git_delivery_target WHERE plan_id=? AND repository_ref=?",id,item.repositoryRef());
            var target=targets.isEmpty()?Map.<String,Object>of():targets.getFirst();
            jdbc.update("""
                    INSERT INTO plan_git_delivery VALUES(?,?,?,?,?,?, 'workflow-delivery',?,?,NULL,?,?,?)
                    ON CONFLICT(plan_id,repository_ref,result_revision) DO NOTHING
                    """,id,item.repositoryRef(),target.getOrDefault("repository_path",""),item.resultRevision(),UUID.randomUUID().toString(),
                    targets.isEmpty()?"BLOCKED":"READY",target.getOrDefault("target_branch",""),target.getOrDefault("captured_revision",""),
                    targets.isEmpty()?"The original delivery target was not captured; select a local target before merging":"Local merge queued",Instant.now().toEpochMilli(),Instant.now().toEpochMilli());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aidev.delivery.poll-ms:5000}")
    public void deliverReady() {
        for (var row:jdbc.queryForList("SELECT * FROM plan_git_delivery WHERE state='READY' ORDER BY created_ms LIMIT 5")) {
            String id=String.valueOf(row.get("plan_id")),ref=String.valueOf(row.get("repository_ref"));
            try {
                var current=preview(id,ref);
                if (current.blockedReason()!=null || !current.repositoryPath().equals(row.get("repository_path"))
                        || !current.targetBranch().equals(row.get("target_branch"))
                        || !current.resultRevision().equals(row.get("result_revision"))) {
                    block(id,ref,Objects.toString(current.blockedReason(),"The captured delivery target changed; inspect the original branch"));
                    continue;
                }
                merge(id,new MergeRequest(current.planVersion(),ref,current.repositoryPath(),current.resultRevision(),current.targetBranch(),
                        current.beforeRevision(),String.valueOf(row.get("request_id"))),"workflow-delivery",true);
            } catch (RuntimeException error) { block(id,ref,"Local delivery needs attention; no automatic retry"); }
        }
    }

    private void block(String id,String ref,String message) {
        jdbc.update("UPDATE plan_git_delivery SET state='BLOCKED',message=?,updated_ms=? WHERE plan_id=? AND repository_ref=? AND state='READY'",
                message,Instant.now().toEpochMilli(),id,ref);
    }

    private String filterBlock(Path path,String head,String result,List<String> files) {
        if (files.isEmpty()) return null;
        List<String> settings;
        try { settings=filterSettings(path); }
        catch (RuntimeException error) {
            return "File filter attributes could not be checked. Check Git support and repository attributes before merging.";
        }
        String input=String.join("\u0000",files)+"\u0000";
        for (String source:List.of("",head,result)) {
            Git attributes;
            try {
                var args=new ArrayList<String>(List.of("check-attr","-z","--stdin"));
                if (!source.isEmpty()) args.add("--source="+source);
                args.add("filter");
                attributes=gitWithInput(path,input,args.toArray(String[]::new));
            } catch (RuntimeException error) {
                return "File filter attributes could not be checked. Check Git support and repository attributes before merging.";
            }
            String[] fields=attributes.text().split("\u0000",-1);
            if (attributes.code()!=0 || fields.length!=files.size()*3+1 || !fields[fields.length-1].isEmpty())
                return "File filter attributes could not be checked. Check Git support and repository attributes before merging.";
            for (int i=0;i<files.size();i++) {
                int offset=i*3;
                if (!fields[offset].equals(files.get(i)) || !fields[offset+1].equals("filter"))
                    return "File filter attributes could not be checked. Check Git support and repository attributes before merging.";
                String value=fields[offset+2];
                // check-attr renders both -filter and filter=unset as "unset" (likewise unspecified).
                boolean ambiguous=settings.stream().anyMatch(key -> key.startsWith("filter."+value+"."));
                if (!Set.of("unspecified","unset").contains(value) || ambiguous)
                    return "A changed file uses a Git filter and requires a manual merge: "+files.get(i);
            }
        }
        return null;
    }

    private List<String> filterSettings(Path path) {
        var config=git(path,"config","--null","--name-only","--get-regexp","^filter\\..*\\.(process|smudge|clean|required)$");
        if (config.code()==1 && config.text().isEmpty()) return List.of();
        var keys=split(config.text());
        if (config.code()!=0 || keys.isEmpty() || !config.text().endsWith("\u0000")
                || keys.stream().anyMatch(key -> !key.matches("filter\\.[^=\\r\\n\\u0000]+\\.(process|smudge|clean|required)")))
            throw new IllegalStateException("Git filter configuration could not be checked safely; no merge was started.");
        return keys;
    }

    private Git gitWithoutFilters(Path path,String... args) {
        // Both worktree diffs and merge's index refresh can run unrelated clean filters.
        // Changed paths were checked above; disable drivers only in each child process.
        var command=new ArrayList<String>();
        for (String key:filterSettings(path)) {
            command.add("-c");command.add(key+"="+(key.endsWith(".required")?"false":""));
        }
        command.addAll(List.of(args));
        return git(path,command.toArray(String[]::new));
    }

    private String requiredWithoutFilters(Path path,String... args) {
        var result=gitWithoutFilters(path,args);
        if (result.code()!=0) throw new IllegalStateException("Local Git operation failed; inspect the original worktree before retrying");
        return result.text();
    }

    private static List<String> split(String text) { return Arrays.stream(text.split("\u0000")).filter(s -> !s.isEmpty()).toList(); }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String required(Path path,String... args) {
        var result=git(path,args);
        if (result.code()!=0) throw new IllegalStateException("Local Git operation failed; inspect the original worktree before retrying");
        return result.text();
    }
    Git git(Path path,String... args) {
        return gitWithInput(path,"",args);
    }
    Git gitWithInput(Path path,String input,String... args) {
        var command=new ArrayList<String>(List.of("git","--no-replace-objects","-c","core.fsmonitor=false","-c","gc.auto=0","-c","maintenance.auto=false",
                "-c","commit.gpgsign=false","-c","core.hooksPath="+(System.getProperty("os.name").startsWith("Windows")?"NUL":"/dev/null"),"-C",path.toString()));
        command.addAll(List.of(args));
        Process process=null;
        try {
            var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().keySet().removeIf(key -> key.startsWith("GIT_") || key.equals("LD_PRELOAD"));
            builder.environment().put("GIT_TERMINAL_PROMPT","0");
            process=builder.start();
            var output=new ByteArrayOutputStream();
            var child=process;
            var writer=Thread.ofVirtual().start(() -> {
                try (var stdin=child.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
                catch (java.io.IOException ignored) { child.destroyForcibly(); }
            });
            var reader=Thread.ofVirtual().start(() -> {
                try {
                    byte[] buffer=new byte[8192];int count;
                    while ((count=child.getInputStream().read(buffer))!=-1) {
                        if (output.size()+count>2*1024*1024) {child.destroyForcibly();break;}
                        output.write(buffer,0,count);
                    }
                } catch (java.io.IOException ignored) { child.destroyForcibly(); }
            });
            if (!process.waitFor(15,TimeUnit.SECONDS)) throw new IllegalStateException("Local Git operation timed out; its outcome must be inspected");
            reader.join(2000);
            writer.join(2000);
            if (reader.isAlive() || writer.isAlive()) throw new IllegalStateException("Local Git output is incomplete");
            String decoded=StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString();
            return new Git(process.exitValue(),decoded);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalStateException("Git returned a path or output that is not valid UTF-8; inspect the repository before merging.");
        } catch (java.io.IOException e) { throw new IllegalStateException("Local Git executable or repository is unavailable"); }
        catch (InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Local Git operation interrupted");}
        finally {if (process!=null && process.isAlive()) process.destroyForcibly();}
    }
}
