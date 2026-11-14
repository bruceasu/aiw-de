package me.asu.aidev.authority;

import java.nio.file.Path;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import me.asu.aidev.execution.run.NativePlanOwners;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.WorkflowRuns;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No service or model is started. Execution still requires separate test authorization. */
class WorkflowDelegationTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    AuthorityRepository repo;
    AuthorityBoundary boundary;
    AuthorityBoundary.Principal agent=new AuthorityBoundary.Principal("owner","secretary",1,true);

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("delegation.db")));
        repo=new AuthorityRepository(jdbc);boundary=mock(AuthorityBoundary.class);
        when(boundary.optionalPrincipal()).thenReturn(agent);
    }
    @Test void wrongProcessCannotReadOrCreateDelegatedWork() {
        var admission=mock(ActionAdmissionService.class);var workflows=mock(WorkflowRuns.class);
        var controller=new WorkflowDelegationController(repo,boundary,admission,mock(BusinessActionPermissions.class),workflows);
        var request=mock(HttpServletRequest.class);
        when(request.getAttribute("authorityPrincipal")).thenReturn(agent);
        when(request.getHeader("X-AIW-Agent-Boot")).thenReturn("old-boot");
        when(boundary.processBoot("secretary","old-boot")).thenReturn(false);
        assertThatThrownBy(()->controller.read(request,UUID.randomUUID().toString())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(workflows);
    }
    @Test void ownerBindingRequiresTheConsumedPermissionForThisExactPlan() {
        jdbc.execute("CREATE TABLE authority_permission_child(grant_id TEXT,permission_id TEXT,request_id TEXT,source_plan TEXT)");
        jdbc.execute("CREATE TABLE authority_action_permission(id TEXT,owner_id TEXT,worker_id TEXT,action TEXT)");
        jdbc.execute("CREATE TABLE authority_permission_use(permission_id TEXT,request_id TEXT,grant_id TEXT)");
        jdbc.execute("CREATE TABLE plan_run_owner(plan_id TEXT,owner_id TEXT,request_id TEXT,request_digest TEXT,source_digest TEXT,source_version INTEGER,actor TEXT,created_ms INTEGER)");
        jdbc.execute("CREATE TABLE authority_owner(id TEXT,actor TEXT)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT,spec_json TEXT)");
        jdbc.execute("CREATE TABLE authority_audit(id TEXT,target TEXT,grant_id TEXT,request_id TEXT,actor TEXT,event TEXT,detail TEXT,created_ms INTEGER)");
        jdbc.update("INSERT INTO authority_owner VALUES('owner','admin')");
        jdbc.update("INSERT INTO flexible_workflow_plan VALUES('plan','{}')");
        jdbc.update("INSERT INTO authority_action_permission VALUES('permission','owner','secretary','A05')");
        jdbc.update("INSERT INTO authority_permission_child VALUES('grant','permission','request','other-plan')");
        jdbc.update("INSERT INTO authority_permission_use VALUES('permission','request','other-grant')");
        var plans=mock(WorkflowPlanRepository.class);var tasks=mock(WorkTaskRepository.class);
        var owners=new NativePlanOwners(repo,boundary,null,plans,tasks,null);
        var input=new NativePlanOwners.Bind("binding","plan",0);
        assertThatThrownBy(()->owners.bindConfirmedAgent(input,agent,"request")).isInstanceOf(SecurityException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_run_owner",Integer.class)).isZero();
        jdbc.update("UPDATE authority_permission_child SET source_plan='plan'");
        assertThatThrownBy(()->owners.bindConfirmedAgent(input,agent,"request")).isInstanceOf(SecurityException.class);
        jdbc.update("UPDATE authority_permission_use SET grant_id='grant'");
        var plan=mock(WorkflowPlan.class);when(plan.id()).thenReturn("plan");when(plan.createdBy()).thenReturn("admin");
        when(plan.state()).thenReturn(WorkflowPlan.State.DRAFT);when(plan.version()).thenReturn(0L);
        when(plans.find("plan")).thenReturn(Optional.of(plan));
        owners.bindConfirmedAgent(input,agent,"request");
        assertThat(jdbc.queryForObject("SELECT owner_id FROM plan_run_owner",String.class)).isEqualTo("owner");
        verify(boundary,never()).human(anyString());
    }
    @Test void oneAgentCannotReadAnotherAgentsRequestWithTheSameUuid() {
        jdbc.execute("CREATE TABLE authority_owner(id TEXT,actor TEXT)");
        jdbc.execute("CREATE TABLE workflow_execution(actor TEXT,request_id TEXT,run_json TEXT)");
        jdbc.update("INSERT INTO authority_owner VALUES('owner','admin')");
        String request=UUID.randomUUID().toString();
        var run=new WorkflowRuns.Run();run.id="plan";run.actor="admin";run.state="WAITING_MEMBER";
        String key=UUID.nameUUIDFromBytes(("secretary:secretary:"+request).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        jdbc.update("INSERT INTO workflow_execution VALUES(?,?,?)","admin",key,repo.encode(run));
        var workflows=new WorkflowRuns(repo,boundary,null,null,null,null,null,null,null);
        assertThat(workflows.delegation(agent,request).get("executionId")).isEqualTo("plan");
        var other=new AuthorityBoundary.Principal("owner","other-secretary",1,true);
        when(boundary.optionalPrincipal()).thenReturn(other);
        assertThatThrownBy(()->workflows.delegation(other,request)).isInstanceOf(NoSuchElementException.class);
    }
}
