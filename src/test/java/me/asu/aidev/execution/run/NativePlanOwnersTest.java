package me.asu.aidev.execution.run;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativePlanOwnersTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;AuthorityBoundary boundary;NativePlanOwners owners;WorkTaskRepository tasks;
    WorkflowPlanRepository plans;WorkflowPlan plan;
    NativePlanOwners.Bind input=new NativePlanOwners.Bind("bind-request","plan",3);

    @BeforeEach void setup() {
        var datasource=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("owners.db"));jdbc=new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE authority_lock(id INTEGER,version INTEGER)");jdbc.update("INSERT INTO authority_lock VALUES(1,0)");
        jdbc.execute("CREATE TABLE authority_owner(id TEXT PRIMARY KEY,actor TEXT)");jdbc.update("INSERT INTO authority_owner VALUES('owner','human')");
        jdbc.execute("CREATE TABLE authority_audit(id TEXT,target TEXT,grant_id TEXT,request_id TEXT,actor TEXT,event TEXT,detail TEXT,created_ms BIGINT)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT PRIMARY KEY,spec_json TEXT)");jdbc.update("INSERT INTO flexible_workflow_plan VALUES('plan','fixed-spec')");
        jdbc.execute("CREATE TABLE execution_run_session(attempt_id TEXT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V29__native_plan_run_owners.sql")).execute(datasource);
        var repo=new AuthorityRepository(jdbc);boundary=new AuthorityBoundary(repo);boundary.human("human");
        plans=mock(WorkflowPlanRepository.class);tasks=mock(WorkTaskRepository.class);
        var spec=new WorkflowPlanSpec("Original goal",WorkflowPlanSpec.Source.MANAGER,List.of(new WorkflowPlanSpec.Step("work",Set.of("coding"),Set.of(),"coding-work-package-executor","Implement it",null,null,Set.of())));
        plan=new WorkflowPlan("plan",WorkflowPlan.State.CONFIRMED,3,spec,"human","human","confirmed",Instant.now(),Instant.now());
        when(plans.find("plan")).thenReturn(Optional.of(plan));when(tasks.findBySubject("flexible-plan","plan")).thenReturn(List.of());
        owners=new NativePlanOwners(repo,boundary,mock(AuthorityGrantService.class),plans,tasks,mock(ExecutionGuard.class));
    }
    WorkTask task(TaskState state,String id) {
        return new WorkTask(new TaskId(id),TaskType.IMPLEMENTATION,state,"flexible-plan","plan",Set.of(),null,"Original instruction",1,null,null,TaskPriority.NORMAL,0,Instant.now(),Instant.now());
    }
    @Test void explicitCreatorBindingIsIdempotentAndNeverRebindsHistory() {
        assertThat(owners.bind(input)).isEqualTo(owners.bind(input));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_run_owner",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_version FROM plan_run_owner",Long.class)).isEqualTo(4);
        assertThatThrownBy(()->owners.bind(new NativePlanOwners.Bind("bind-request","plan",4))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->owners.bind(new NativePlanOwners.Bind("different-request","plan",3))).isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyCreatorAndCurrentVersionCanBind() {
        assertThatThrownBy(()->owners.bind(new NativePlanOwners.Bind("request","plan",2))).isInstanceOf(IllegalStateException.class);
        boundary.human("another-human");jdbc.update("INSERT INTO authority_owner VALUES('other','another-human')");
        assertThatThrownBy(()->owners.bind(input)).isInstanceOf(SecurityException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_run_owner",Long.class)).isZero();
    }
    @Test void activeTaskPreventsAttachingControlToAnExistingRun() {
        when(tasks.findBySubject("flexible-plan","plan")).thenReturn(List.of(task(TaskState.RUNNING,"plan~work~work-1")));
        assertThatThrownBy(()->owners.bind(input)).isInstanceOf(IllegalStateException.class);
    }
    @Test void fixedScopeAndActualPlanSlotAreRequiredForNativeTaskIdentity() {
        owners.bind(input);var task=task(TaskState.READY,"plan~work~work-1");when(tasks.find(task.id())).thenReturn(Optional.of(task));
        assertThat(owners.task(task.id().value(),true).ownerId()).isEqualTo("owner");
        jdbc.update("UPDATE flexible_workflow_plan SET spec_json='changed-scope'");
        assertThatThrownBy(()->owners.task(task.id().value(),true)).isInstanceOf(SecurityException.class);
        var unrelated=task(TaskState.READY,"not-a-real-slot");when(tasks.find(unrelated.id())).thenReturn(Optional.of(unrelated));
        assertThatThrownBy(()->owners.task(unrelated.id().value(),true)).isInstanceOf(SecurityException.class);
    }
}
