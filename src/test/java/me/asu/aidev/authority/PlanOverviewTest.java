package me.asu.aidev.authority;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static me.asu.aidev.authority.ActionPermissionContract.Scope;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanOverviewTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    BusinessPermissionController controller;
    BusinessActionPermissions permissions;
    PermissionTargetResolver targets;
    ActionAdmissionService admission;
    WorkTaskRepository tasks;

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("overview.db")));
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT,created_by TEXT,created_at_epoch_ms INTEGER)");
        jdbc.execute("CREATE TABLE web_console_plan_archive(plan_id TEXT)");
        jdbc.execute("CREATE TABLE execution_policy(plan_id TEXT,owner_id TEXT)");
        jdbc.execute("CREATE TABLE authority_target(target_key TEXT,owner_id TEXT)");
        jdbc.execute("CREATE TABLE authority_identity(worker_id TEXT,owner_id TEXT,identity_kind TEXT,connection_profile TEXT)");
        jdbc.update("INSERT INTO authority_identity VALUES('agent','owner','AGENT','LOCAL')");
        jdbc.update("INSERT INTO flexible_workflow_plan VALUES('one','human',3),('two','human',2),('other','another',1)");
        jdbc.update("INSERT INTO authority_target VALUES('PLAN:other','owner')");
        var repo=mock(AuthorityRepository.class);when(repo.jdbc()).thenReturn(jdbc);when(repo.actor("owner")).thenReturn("human");
        var boundary=mock(AuthorityBoundary.class);permissions=mock(BusinessActionPermissions.class);
        targets=mock(PermissionTargetResolver.class);admission=mock(ActionAdmissionService.class);tasks=mock(WorkTaskRepository.class);
        doThrow(new SecurityException()).when(targets).plan("owner","other");
        doThrow(new SecurityException()).when(targets).task("owner","foreign-task");
        when(targets.task("owner","task-one")).thenReturn("one");
        var plans=mock(WorkflowPlanService.class);
        for(String id:List.of("one","two","other"))when(plans.get(id)).thenReturn(plan(id));
        when(tasks.findBySubject("flexible-plan","one")).thenReturn(List.of(task("task-one"),task("foreign-task")));
        ObjectProvider<WorkflowPlanService> plansProvider=mock(ObjectProvider.class);when(plansProvider.getObject()).thenReturn(plans);
        ObjectProvider<ActionAdmissionService> admissionProvider=mock(ObjectProvider.class);when(admissionProvider.getObject()).thenReturn(admission);
        ObjectProvider<WorkTaskRepository> taskProvider=mock(ObjectProvider.class);when(taskProvider.getObject()).thenReturn(tasks);
        controller=new BusinessPermissionController(repo,boundary,mock(AuthorityGrantService.class),permissions,
                mock(PermissionActionPreparer.class),targets,plansProvider,admissionProvider);
        ReflectionTestUtils.setField(controller,"workTasks",taskProvider);
    }

    private WorkflowPlan plan(String id) {
        var step=new WorkflowPlanSpec.Step("execute",Set.of("coding"),Set.of(),"coding-work-package-executor","work",null,null,Set.of());
        return new WorkflowPlan(id,WorkflowPlan.State.CONFIRMED,1,new WorkflowPlanSpec("Title "+id,WorkflowPlanSpec.Source.MANAGER,List.of(step)),
                "human","human","",Instant.EPOCH,Instant.EPOCH);
    }
    private WorkTask task(String id) {
        return new WorkTask(new TaskId(id),TaskType.EXTERNAL_WORK,TaskState.RUNNING,"flexible-plan","one",Set.of(),"skill","work",null,null,
                TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH);
    }
    private MockHttpServletRequest request(boolean enabled) {
        var request=new MockHttpServletRequest();request.setAttribute("authorityPrincipal",new AuthorityBoundary.Principal("owner","agent",1,enabled));return request;
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> read() {
        return (List<Map<String,Object>>)((Map<String,Object>)controller.planOverview(request(true))).get("plans");
    }
    @Test void localSeesOnlyOwnedPlansAndTasksWithoutActionGrant() {
        var result=read();assertThat(result).extracting(row->row.get("id")).containsExactly("one","two");
        assertThat((List<?>)result.getFirst().get("tasks")).hasSize(1);
        assertThat(result.getFirst().get("runningCount")).isEqualTo(1L);
    }
    @Test void remoteOnlySeesPlansInActivePermissionScope() {
        jdbc.update("UPDATE authority_identity SET connection_profile='REMOTE'");
        var permission=new BusinessActionPermissions.Permission("p","owner","agent",ActionScope.Action.A07,Scope.PLAN,"one",5,0,"ENABLED","operator",0,Long.MAX_VALUE,"r","d",1);
        when(permissions.list("owner")).thenReturn(List.of(permission));when(permissions.active(permission)).thenReturn(true);
        assertThat(read()).extracting(row->row.get("id")).containsExactly("one");
    }
    @Test void disabledAgentCannotReadOverview() {
        doThrow(new SecurityException()).when(admission).enabled(argThat(p->!p.enabled()));
        assertThatThrownBy(()->controller.planOverview(request(false))).isInstanceOf(SecurityException.class);
    }
}
