package me.asu.aidev.workflow.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.config.AgentFactorySettings;
import me.asu.aidev.execution.config.FactoryTaskProfiles;
import me.asu.aidev.execution.config.ManagerProfileSelection;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.run.NativePlanOwners;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.application.TeamMembers;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkflowBusinessRoutingTest {
    @TempDir Path directory;
    WorkflowRuns runs;
    WorkflowPlanService plans;
    WorkTaskRepository tasks;
    JdbcTemplate jdbc;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("workflow.db")));
        jdbc.execute("CREATE TABLE workflow_definition(id TEXT PRIMARY KEY,actor TEXT,version INTEGER,spec_json TEXT,updated_ms INTEGER)");
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT PRIMARY KEY,definition_id TEXT,actor TEXT,version INTEGER,state TEXT,run_json TEXT,updated_ms INTEGER,request_id TEXT,request_digest TEXT)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT PRIMARY KEY,spec_json TEXT)");
        var db=new AuthorityRepository(jdbc);
        var boundary=mock(AuthorityBoundary.class);
        when(boundary.human()).thenReturn("owner");
        var store=mock(WorkflowPlanRepository.class);
        var rows=new HashMap<String,WorkflowPlan>();
        doAnswer(call->{
            WorkflowPlan plan=call.getArgument(0);
            jdbc.update("INSERT INTO flexible_workflow_plan VALUES(?,?)",plan.id(),db.encode(plan.spec()));
            rows.put(plan.id(),plan);
            return null;
        }).when(store).insert(any());
        when(store.find(anyString())).thenAnswer(call->Optional.ofNullable(rows.get(call.<String>getArgument(0))));
        when(store.transition(anyString(),anyLong(),any(),any(),anyString(),anyString(),any())).thenAnswer(call->{
            WorkflowPlan plan=rows.get(call.<String>getArgument(0));
            WorkflowPlan.State state=call.getArgument(3);
            rows.put(plan.id(),new WorkflowPlan(plan.id(),state,plan.version()+1,plan.spec(),
                    plan.createdBy(),"owner",call.getArgument(5),plan.createdAt(),call.getArgument(6)));
            return true;
        });
        tasks=mock(WorkTaskRepository.class);
        plans=spy(new WorkflowPlanService(store,null,tasks,null,null,null,null));
        doNothing().when(plans).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),anySet(),anySet());
        var locations=mock(PlanLocations.class);
        when(locations.allocate(anyString(),isNull())).thenReturn(directory.toString());
        when(locations.executionStoreCapability()).thenReturn("handoff-store:test");
        ObjectProvider<WorkflowPlanService> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(plans);
        runs=new WorkflowRuns(db,boundary,store,provider,mock(NativePlanOwners.class),locations,tasks,null,null);
        ReflectionTestUtils.setField(runs,"team",mock(TeamMembers.class));
    }

    @Test void simpleTaskDispatchesOneTaskAndCompletesWithoutAnEndNode() {
        var run=runs.simple("request-1","Create the requested deliverable",null,null,WorkflowRuns.Assignment.GENERAL,List.of(),List.of());
        assertThat(run.spec.simpleTask()).isTrue();
        assertThat(run.spec.nodes()).extracting(WorkflowRuns.Node::kind).containsExactly(WorkflowRuns.Kind.TASK);
        var plan=plans.get(run.id);
        verify(plans).createWorkflowExecution(eq(plan),eq(0),eq(1),
                argThat(instruction->!instruction.contains("workflow node")),eq(Set.of()),eq(Set.of()));
        var finished=finishedTask(run);
        runs.completed(finished,new CompletionReport(CompletionVerdict.PASS,"Actual result"));
        var replay=runs.simple("request-1","Create the requested deliverable",null,null,WorkflowRuns.Assignment.GENERAL,List.of(),List.of());
        assertThat(replay.id).isEqualTo(run.id);
        assertThat(replay.state).isEqualTo("COMPLETED");
        assertThat(replay.current).isEqualTo("task");
        assertThat(replay.events).extracting(WorkflowRuns.Event::type).containsSubsequence("DISPATCHED","RESULT","COMPLETED");
        verify(plans,times(1)).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),anySet(),anySet());
    }

    @Test void theSameBusinessWorkflowCanTakeBothRoutesWithoutDispatchingAWorkerTask() {
        var rule=new WorkflowRuns.Node("receipt","Check receipt",WorkflowRuns.Kind.RULE,"",null,
                Map.of("match","confirm","otherwise","missing"),null,null,List.of(),false,List.of(),null,
                new WorkflowRuns.FieldRule("INPUT","hasReceipt","true"));
        var question=new WorkflowRuns.Node("confirm","Confirm intake",WorkflowRuns.Kind.ASK,"Accept the request?",null,
                Map.of("accept","accepted","reject","missing"),null,null,List.of());
        var accepted=new WorkflowRuns.Node("accepted","Accepted",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());
        var missing=new WorkflowRuns.Node("missing","Missing or declined",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());
        var spec=new WorkflowRuns.Spec("Intake","Check the application",rule.key(),List.of(rule,question,accepted,missing),List.of());
        var definition=runs.save(null,0,spec);

        var waiting=runs.start("match",definition.id(),definition.version(),"{\"hasReceipt\":true}",null);
        assertThat(waiting.state).isEqualTo("WAITING_USER");
        assertThat(waiting.current).isEqualTo("confirm");
        assertThatThrownBy(()->plans.copy(waiting.id,"owner")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported role");
        var confirmed=runs.control(waiting.id,waiting.version,"answer","accept");
        assertThat(confirmed.current).isEqualTo("accepted");
        assertThat(confirmed.state).isEqualTo("COMPLETED");

        var other=runs.start("miss",definition.id(),definition.version(),"{\"hasReceipt\":false}",null);
        assertThat(other.current).isEqualTo("missing");
        assertThat(other.state).isEqualTo("COMPLETED");
        var invalid=runs.start("invalid",definition.id(),definition.version(),"{\"hasReceipt\":true} extra",null);
        assertThat(invalid.state).isEqualTo("FAILED");
        assertThat(invalid.events.getLast().content()).contains("JSON object");
        verify(plans,never()).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),anySet(),anySet());
        verifyNoInteractions(tasks);
    }

    @Test void aBusinessRuleCanReadFencedJsonFromAnAgentHandoff() {
        var task=new WorkflowRuns.Node("extract","Extract fields",WorkflowRuns.Kind.TASK,"Return a JSON object","check",
                Map.of(),null,WorkflowRuns.Assignment.GENERAL,List.of());
        var rule=new WorkflowRuns.Node("check","Check status",WorkflowRuns.Kind.RULE,"",null,
                Map.of("match","accepted","otherwise","missing"),null,null,List.of(),false,List.of(),null,
                new WorkflowRuns.FieldRule("TASK_RESULT","status","approved"));
        var accepted=new WorkflowRuns.Node("accepted","Accepted",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());
        var missing=new WorkflowRuns.Node("missing","Missing",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());
        var definition=runs.save(null,0,new WorkflowRuns.Spec("Intake","Check the application",task.key(),
                List.of(task,rule,accepted,missing),List.of()));
        var run=runs.start("agent",definition.id(),definition.version(),"Request",null);
        runs.completed(finishedTask(run),new CompletionReport(CompletionVerdict.PASS,
                "```json\r\n{\"status\":\"approved\"}\r\n```\n\nWorking directory: D:/work"));
        var result=runs.start("agent",definition.id(),definition.version(),"Request",null);
        assertThat(result.state).isEqualTo("COMPLETED");
        assertThat(result.current).isEqualTo("accepted");
        verify(plans,times(1)).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),anySet(),anySet());
    }

    @Test void workflowWaitsForManagerDecisionAndOnlyDispatchesTheCurrentSelection() {
        var profiles=mock(FactoryTaskProfiles.class);
        var fixed=new AgentFactorySettings.RuntimeProfile("precise",2,true,
                new AgentFactorySettings.Settings("openai-agents","example","default",true,"python","",""));
        when(profiles.resolve("precise")).thenReturn(fixed);
        ReflectionTestUtils.setField(runs,"runtimeProfiles",profiles);
        var check=new WorkflowRuns.Node("check","Check receipt",WorkflowRuns.Kind.TASK,"Check the receipt","end",
                Map.of(),null,null,List.of(),false,List.of(),null,null,null,List.of("quick","precise"));
        var end=new WorkflowRuns.Node("end","End",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());
        var definition=runs.save(null,0,new WorkflowRuns.Spec("Intake","Check the application",check.key(),List.of(check,end),List.of()));
        var run=runs.start("manager",definition.id(),definition.version(),"Private input",null);
        var request=runs.pendingProfile(run.id);
        assertThat(request.input().candidates()).containsExactly("quick","precise");
        assertThat(request.input().task()).contains("Check the receipt").doesNotContain("Private input");
        verify(plans,never()).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),anySet(),anySet());

        runs.finishProfile(run.id,request.version(),null,"Invalid result");
        var failed=runs.start("manager",definition.id(),definition.version(),"Private input",null);
        assertThat(failed.state).isEqualTo("FAILED");
        assertThat(failed.events.getLast().type()).isEqualTo("MANAGER_MODEL_FAILED");
        var resumed=runs.control(failed.id,failed.version,"retry",null);
        var selected=new ManagerProfileSelection.Result("result","precise",2,"Fits");
        runs.finishProfile(run.id,request.version(),selected,null);
        verifyNoInteractions(profiles);
        runs.finishProfile(run.id,resumed.version,selected,null);
        var active=runs.start("manager",definition.id(),definition.version(),"Private input",null);
        assertThat(active.state).isEqualTo("RUNNING");
        verify(profiles).bind(active.taskId,fixed);
        verify(plans).createWorkflowExecution(any(),anyInt(),anyInt(),anyString(),eq(Set.of("handoff-store:test")),eq(Set.of()));
    }

    @Test void automaticRecoveryRequiresTheFirstSettledRateLimitAndRecordsOnlyOneRetry() {
        var run=runs.simple("rate-limit","Read the available material",null,null,WorkflowRuns.Assignment.GENERAL,List.of(),List.of());
        var task=new WorkTask(new TaskId(run.taskId),TaskType.EXTERNAL_WORK,TaskState.FAILED,"flexible-plan",run.id,
                Set.of("general-work"),"general-work","Read material",null,null,TaskPriority.NORMAL,1,Instant.EPOCH,Instant.EPOCH);
        when(tasks.find(task.id())).thenReturn(Optional.of(task));
        when(tasks.retryFailed(eq(task.id()),any())).thenReturn(true);
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_id TEXT,attempt_no INTEGER,lease_token TEXT)");
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,attempt_no INTEGER,state TEXT,termination_reason TEXT,finished_at_epoch_ms BIGINT,stop_requested_at_epoch_ms BIGINT)");
        jdbc.execute("CREATE TABLE execution_operation(attempt_id TEXT)");
        jdbc.execute("CREATE TABLE work_task_control_event(task_id TEXT,action TEXT)");
        jdbc.execute("CREATE TABLE web_console_plan_archive(plan_id TEXT)");
        jdbc.update("INSERT INTO work_task VALUES(?,?,1,NULL)",run.taskId,run.id);
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt',?,1,'FAILED','MODEL_API_RATE_LIMIT',1,NULL)",run.taskId);
        jdbc.update("INSERT INTO execution_operation VALUES('attempt')");
        assertThat(runs.recoverModelRateLimit(run.id,run.taskId,"attempt")).isFalse();
        jdbc.update("DELETE FROM execution_operation WHERE attempt_id='attempt'");
        doAnswer(call->{jdbc.update("INSERT INTO work_task_control_event VALUES(?,?)",run.taskId,call.<String>getArgument(3));return null;})
                .when(tasks).recordControlEvent(anyString(),eq(task.id()),eq("attempt"),anyString(),anyString(),anyString(),anyBoolean(),any());
        assertThat(runs.recoverModelRateLimit(run.id,run.taskId,"attempt")).isTrue();
        assertThat(runs.recoverModelRateLimit(run.id,run.taskId,"attempt")).isFalse();
        verify(tasks,times(1)).retryFailed(eq(task.id()),any());
    }

    private WorkTask finishedTask(WorkflowRuns.Run run) {
        var finished=new WorkTask(new TaskId(run.taskId),TaskType.EXTERNAL_WORK,TaskState.COMPLETED,"flexible-plan",run.id,
                Set.of("general-work"),"general-work","Execute",1,null,null,TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH);
        when(tasks.find(finished.id())).thenReturn(Optional.of(finished));
        doReturn(List.of(finished)).when(plans).effectiveSteps(run.id);
        return finished;
    }
}
