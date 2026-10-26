package me.asu.aidev.workflow.application;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowGitDeliveryServiceTest {
    @TempDir Path temp;
    Path repo;
    JdbcTemplate db;
    WorkflowGitDeliveryService service;
    WorkflowPlan plan;
    String base, result;
    AuthorityBoundary authority;
    WorkflowPlanRepository plans;
    WorkflowPlanService workflow;
    ExecutionAttemptRepository attempts;
    boolean failMerge;

    @BeforeEach void setup() throws Exception {
        repo=Files.createDirectory(temp.resolve("repo"));
        git("init","-b","master");git("config","user.name","Test");git("config","user.email","test@example.invalid");
        Files.writeString(repo.resolve("page.htm"),"initial\n");
        Files.writeString(repo.resolve("notes.txt"),"notes\n");
        git("add",".");git("commit","-m","baseline");base=git("rev-parse","HEAD");
        git("checkout","-b","task");Files.writeString(repo.resolve("page.htm"),"undo added\n");
        git("commit","-am","implement undo");result=git("rev-parse","HEAD");git("checkout","master");
        var source=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("test.db"));
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE flexible_workflow_plan(id TEXT PRIMARY KEY)");
        db.update("INSERT INTO flexible_workflow_plan VALUES('FP-delivery')");
        for (String sql:Files.readString(Path.of("src/main/resources/db/migration/V21__plan_git_delivery.sql")).split(";"))
            if (!sql.isBlank()) db.execute(sql);
        plans=mock(WorkflowPlanRepository.class);
        workflow=mock(WorkflowPlanService.class);
        var factory=mock(FactoryPlanLifecycle.class);
        var tasks=mock(WorkTaskRepository.class);
        attempts=mock(ExecutionAttemptRepository.class);
        var spaces=mock(ManagedWorkspaceService.class);
        authority=mock(AuthorityBoundary.class);when(authority.human()).thenReturn("owner");
        var spec=new WorkflowPlanSpec("Undo",WorkflowPlanSpec.Source.MANAGER,List.of(new WorkflowPlanSpec.Step(
                "code",Set.of("coding"),Set.of(),"coding-work-package-executor","Implement undo","repo",base,Set.of())));
        plan=new WorkflowPlan("FP-delivery",WorkflowPlan.State.COMPLETED,3,spec,"owner","owner","",Instant.EPOCH,Instant.EPOCH);
        when(plans.find(plan.id())).thenAnswer(call -> Optional.of(plan));
        var taskId=new TaskId("FP-delivery~code~work-1");
        var task=new WorkTask(taskId,TaskType.EXTERNAL_WORK,TaskState.COMPLETED,"flexible-plan",plan.id(),Set.of("coding"),
                "coding-work-package-executor","Implement undo","repo",base,TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH);
        when(tasks.find(taskId)).thenReturn(Optional.of(task));
        when(workflow.acceptedInputs(plan,0)).thenReturn(Set.of(taskId));
        when(attempts.findCompletedInput(taskId)).thenReturn(Optional.of(new TaskInput(taskId,TaskType.EXTERNAL_WORK,"repo",base,result,
                null,List.of("page.htm"),CompletionVerdict.PASS,"done")));
        when(spaces.findTarget("repo")).thenReturn(Optional.of(new ManagedWorkspaceService.Target("repo","w","w",repo.toString(),repo.toString(),"r",".",null,"LOCAL")));
        service=new WorkflowGitDeliveryService(plans,provider(workflow),provider(factory),tasks,attempts,spaces,db,authority,
                new DataSourceTransactionManager(source)) {
            @Override Git git(Path path,String... args) {
                if (failMerge && List.of(args).contains("merge")) throw new IllegalStateException("injected merge failure");
                try { return command(path,args); } catch (Exception error) { throw new IllegalStateException(error); }
            }
        };
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);return provider;
    }
    private WorkflowGitDeliveryService.Git command(Path path,String... args) throws Exception {
        var command=new ArrayList<>(List.of("git","-c","core.hooksPath=NUL","-c","commit.gpgsign=false","-C",path.toString()));
        command.addAll(List.of(args));
        var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.environment().put("GIT_CONFIG_NOSYSTEM","1");builder.environment().put("GIT_CONFIG_GLOBAL","NUL");
        var process=builder.start();process.getOutputStream().close();
        var output=process.getInputStream().readAllBytes();
        return new WorkflowGitDeliveryService.Git(process.waitFor(),new String(output,java.nio.charset.StandardCharsets.UTF_8));
    }
    private String git(String... args) throws Exception {
        var out=command(repo,args);assertThat(out.code()).as(String.join(" ",args)).isZero();return out.text().strip();
    }
    private WorkflowGitDeliveryService.MergeRequest request() {
        var p=service.preview(plan.id(),"repo");
        return new WorkflowGitDeliveryService.MergeRequest(p.planVersion(),"repo",p.repositoryPath(),p.resultRevision(),p.targetBranch(),p.beforeRevision(),UUID.randomUUID().toString());
    }
    @Test void fastForwardPreservesUnrelatedDirtyFilesAndIsIdempotent() throws Exception {
        Files.writeString(repo.resolve("notes.txt"),"user notes\n");
        var input=request();assertThat(service.merge(plan.id(),input,"owner").state()).isEqualTo("DELIVERED");
        assertThat(git("rev-parse","HEAD")).isEqualTo(result);
        assertThat(Files.readString(repo.resolve("notes.txt"))).isEqualTo("user notes\n");
        assertThat(service.merge(plan.id(),input,"owner").state()).isEqualTo("DELIVERED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_git_delivery",Integer.class)).isEqualTo(1);
    }
    @Test void divergenceCreatesLocalMergeCommitWithoutAnotherApproval() throws Exception {
        Files.writeString(repo.resolve("notes.txt"),"new upstream\n");git("commit","-am","upstream");String before=git("rev-parse","HEAD");
        assertThat(service.merge(plan.id(),request(),"owner").state()).isEqualTo("DELIVERED");
        assertThat(git("show","-s","--format=%P","HEAD")).isEqualTo(before+" "+result);
        assertThat(Files.readString(repo.resolve("notes.txt"))).isEqualTo("new upstream\n");
    }
    @Test void conflictStopsBeforeChangingOriginalWorktree() throws Exception {
        Files.writeString(repo.resolve("page.htm"),"different upstream\n");git("commit","-am","conflict");String before=git("rev-parse","HEAD");
        assertThat(service.merge(plan.id(),request(),"owner").state()).isEqualTo("DELIVERY_BLOCKED");
        assertThat(git("rev-parse","HEAD")).isEqualTo(before);assertThat(git("status","--porcelain")).isEmpty();
    }
    @Test void queueMergesOnlyCapturedNewCompletion() throws Exception {
        service.capture(plan);service.enqueue(plan.id());service.deliverReady();
        assertThat(service.view(plan.id()).state()).isEqualTo("DELIVERED");assertThat(git("rev-parse","HEAD")).isEqualTo(result);
    }
    @Test void readingHistoricalCompletionDoesNotQueueOrMerge() throws Exception {
        assertThat(service.view(plan.id()).state()).isEqualTo("AWAITING_MERGE");service.deliverReady();
        assertThat(git("rev-parse","HEAD")).isEqualTo(base);assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_git_delivery",Integer.class)).isZero();
    }
    @Test void changedCapturedBranchBlocksAutomaticDelivery() throws Exception {
        service.capture(plan);service.enqueue(plan.id());git("checkout","-b","another");service.deliverReady();
        assertThat(service.view(plan.id()).state()).isEqualTo("DELIVERY_BLOCKED");assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }
    @Test void missingCaptureBlocksRatherThanGuessingTarget() throws Exception {
        service.enqueue(plan.id());service.deliverReady();assertThat(service.view(plan.id()).state()).isEqualTo("DELIVERY_BLOCKED");
        assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }
    @Test void unknownOutcomeNeverRetries() throws Exception {
        failMerge=true;assertThatThrownBy(() -> service.merge(plan.id(),request(),"owner")).isInstanceOf(IllegalStateException.class);
        failMerge=false;service.deliverReady();assertThat(service.view(plan.id()).state()).isEqualTo("DELIVERY_UNKNOWN");
        assertThatThrownBy(() -> service.merge(plan.id(),request(),"owner")).isInstanceOf(IllegalStateException.class);
        assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }
    @Test void overlapAndStagedChangesAreBlocked() throws Exception {
        Files.writeString(repo.resolve("page.htm"),"user edit\n");assertThat(service.preview(plan.id(),"repo").blockedReason()).contains("overlap");
        git("checkout","--","page.htm");Files.writeString(repo.resolve("notes.txt"),"staged\n");git("add","notes.txt");
        assertThat(service.preview(plan.id(),"repo").blockedReason()).contains("staged");assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }
    @Test void changedSnapshotAndDelegatedRequestsCannotWrite() throws Exception {
        var input=request();git("checkout","-b","another");
        assertThatThrownBy(() -> service.merge(plan.id(),input,"owner")).isInstanceOf(IllegalStateException.class);
        when(authority.delegated()).thenReturn(true);
        assertThatThrownBy(() -> service.merge(plan.id(),request(),"owner")).isInstanceOf(SecurityException.class);
        assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }

    @Test void ignoredLocalFileIsNeverOverwrittenByDelivery() throws Exception {
        git("checkout","task");Files.writeString(repo.resolve("new.txt"),"task result\n");git("add","new.txt");git("commit","-m","add new file");
        result=git("rev-parse","HEAD");git("checkout","master");
        Files.writeString(repo.resolve(".git/info/exclude"),"new.txt\n");Files.writeString(repo.resolve("new.txt"),"private user data\n");
        var taskId=new TaskId("FP-delivery~code~work-1");
        when(attempts.findCompletedInput(taskId)).thenReturn(Optional.of(new TaskInput(taskId,TaskType.EXTERNAL_WORK,"repo",base,result,null,
                List.of("page.htm","new.txt"),CompletionVerdict.PASS,"done")));
        assertThat(service.preview(plan.id(),"repo").blockedReason()).contains("Untracked");
        assertThatThrownBy(() -> service.merge(plan.id(),request(),"owner")).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(repo.resolve("new.txt"))).isEqualTo("private user data\n");
        assertThat(git("rev-parse","HEAD")).isEqualTo(base);
    }
}
