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

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("workflow.db")));
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

    private WorkTask finishedTask(WorkflowRuns.Run run) {
        var finished=new WorkTask(new TaskId(run.taskId),TaskType.EXTERNAL_WORK,TaskState.COMPLETED,"flexible-plan",run.id,
                Set.of("general-work"),"general-work","Execute",1,null,null,TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH);
        when(tasks.find(finished.id())).thenReturn(Optional.of(finished));
        doReturn(List.of(finished)).when(plans).effectiveSteps(run.id);
        return finished;
    }
}
