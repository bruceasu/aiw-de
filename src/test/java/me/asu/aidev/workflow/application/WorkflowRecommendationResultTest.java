package me.asu.aidev.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowRecommendationResultTest {
    private static final String KEY = "ef0d77f8-bcc4-428f-9873-bfe88f99f1a0";
    private static final String ID = "WR-" + KEY;
    private static final String GOAL = "保留原始目标：\"示例\"\r\n第二行和所有细节。";
    private final ObjectMapper json = new ObjectMapper();
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final ExecutionAttemptRepository attempts = mock(ExecutionAttemptRepository.class);
    private final WorkflowEditorService editor = new WorkflowEditorService(null, null, null, null, null, null);
    private final WorkflowRecommendationService service = new WorkflowRecommendationService(
            null, null, null, tasks, attempts, editor);

    @BeforeEach void frozenRequest() throws Exception {
        ObjectNode request = json.createObjectNode().put("requestKey", KEY).put("inputVersion", 7)
                .put("goal", GOAL).put("actor", "owner");
        when(tasks.find(new TaskId(ID))).thenReturn(Optional.of(new WorkTask(new TaskId(ID),
                TaskType.EXTERNAL_WORK, TaskState.COMPLETED, "workflow-recommendation", ID,
                Set.of("implementation-planning"), "implementation-planner",
                "AIW_RECOMMENDATION_V1 " + json.writeValueAsString(request) + "\nGenerate steps.",
                null, null, TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH)));
    }

    private ObjectNode output() throws Exception {
        return (ObjectNode) json.readTree("""
                {"requestKey":"%s","inputVersion":7,"reason":"One concrete implementation step.",
                 "plan":{"steps":[{"key":"code","requiredSkills":["coding"],"preferredSkills":[],
                 "skillRef":"coding-work-package-executor","instruction":"Implement the original request.",
                 "repositoryRef":null,"baseRevision":null,"dependsOn":[],"model":null,"effort":null}]}}
                """.formatted(KEY));
    }

    private WorkflowRecommendationService.Result read(ObjectNode output) throws Exception {
        when(attempts.findCompletionReport(new TaskId(ID))).thenReturn(Optional.of(
                new CompletionReport(CompletionVerdict.PASS, json.writeValueAsString(output))));
        return service.get(ID, "owner");
    }

    @Test void existingReportWithRewrittenMetadataRecoversWithoutAnotherModelCall() throws Exception {
        ObjectNode output = output();
        ((ObjectNode) output.path("plan")).put("goal", "A shorter model summary").put("source", "MANAGER");

        var result = read(output);

        assertThat(result.state()).isEqualTo("READY_TO_APPLY");
        assertThat(result.plan().goal()).isEqualTo(GOAL);
        assertThat(result.plan().source()).isEqualTo(WorkflowPlanSpec.Source.AI);
        assertThat(result.plan().steps()).singleElement().satisfies(step -> {
            assertThat(step.key()).isEqualTo("code");
            assertThat(step.instruction()).isEqualTo("Implement the original request.");
        });
        assertThat(result.reason()).isEqualTo("One concrete implementation step.");
        // Only stored task/report reads are available; there is no task creator or model client.
        verify(attempts).findCompletionReport(new TaskId(ID));
        verifyNoMoreInteractions(attempts);
    }

    @Test void modelCanOmitPlatformMetadata() throws Exception {
        var result = read(output());
        assertThat(result.state()).isEqualTo("READY_TO_APPLY");
        assertThat(result.plan().goal()).isEqualTo(GOAL);
        assertThat(result.plan().source()).isEqualTo(WorkflowPlanSpec.Source.AI);
        assertThat(result.plan().location()).isNull();
    }

    @Test void anotherRequestOrInputVersionIsStillRejected() throws Exception {
        ObjectNode output = output();
        output.put("requestKey", "another-request");
        assertThat(read(output).state()).isEqualTo("INVALID");
        output.put("requestKey", KEY).put("inputVersion", 8);
        assertThat(read(output).state()).isEqualTo("INVALID");
    }

    @Test void missingReasonOrNonObjectPlanIsStillRejected() throws Exception {
        ObjectNode output = output();
        output.put("reason", " ");
        assertThat(read(output).state()).isEqualTo("INVALID");
        output = output();
        output.put("plan", "not an object");
        assertThat(read(output).state()).isEqualTo("INVALID");
    }

    @Test void modelCannotChooseExecutionLocationOrRepository() throws Exception {
        ObjectNode output = output();
        ((ObjectNode) output.path("plan")).put("location", "D:/unapproved");
        assertThat(read(output).state()).isEqualTo("INVALID");
        output = output();
        ((ObjectNode) output.path("plan").path("steps").get(0)).put("repositoryRef", "unapproved");
        assertThat(read(output).state()).isEqualTo("INVALID");
        output = output();
        ((ObjectNode) output.path("plan").path("steps").get(0)).put("baseRevision", "a".repeat(40));
        assertThat(read(output).state()).isEqualTo("INVALID");
    }

    @Test void unsupportedRolesAndBrokenDependenciesAreStillRejected() throws Exception {
        ObjectNode output = output();
        ((ObjectNode) output.path("plan").path("steps").get(0)).put("skillRef", "untrusted-skill");
        assertThat(read(output).state()).isEqualTo("INVALID");
        output = output();
        ((ObjectNode) output.path("plan").path("steps").get(0)).putArray("dependsOn").add("missing-step");
        assertThat(read(output).state()).isEqualTo("INVALID");
    }

    @Test void anotherOperatorCannotReadOrRebindTheResult() {
        assertThatThrownBy(() -> service.get(ID, "another-operator")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(attempts);
    }
}
