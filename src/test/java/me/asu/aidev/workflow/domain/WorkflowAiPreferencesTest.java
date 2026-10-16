package me.asu.aidev.workflow.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.workflow.application.WorkflowAiPreferences;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowAiPreferencesTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void oldJsonAndBlankFieldsKeepWorkerChoice() throws Exception {
        var step = json.readValue("""
                {"key":"code","requiredSkills":["coding"],"skillRef":"executor",
                 "instruction":"Implement","dependsOn":[]}
                """, WorkflowPlanSpec.Step.class);
        assertThat(step.model()).isNull();
        assertThat(step.effort()).isNull();
        var blank = new WorkflowPlanSpec.Step("code", Set.of("coding"), Set.of(), "executor", "Implement",
                null, null, Set.of(), " ", "\t");
        assertThat(blank.model()).isNull();
        assertThat(blank.effort()).isNull();
    }

    @Test
    void preferencesSurviveJsonAndApplyToWorkRepairAndRepeatedReviewOnly() throws Exception {
        var spec = new WorkflowPlanSpec("Implement", WorkflowPlanSpec.Source.MANAGER, List.of(
                new WorkflowPlanSpec.Step("code", Set.of("coding"), Set.of(), "executor", "Implement",
                        null, null, Set.of(), " custom-model ", " high ")));
        var restored = json.readValue(json.writeValueAsString(spec), WorkflowPlanSpec.class);
        assertThat(restored).isEqualTo(spec);
        var repository = mock(WorkflowPlanRepository.class);
        var plan = new WorkflowPlan("FP-ai", WorkflowPlan.State.CONFIRMED, 1, restored,
                "operator", "operator", "", Instant.EPOCH, Instant.EPOCH);
        when(repository.find(plan.id())).thenReturn(Optional.of(plan));
        var preferences = new WorkflowAiPreferences(repository);
        for (String suffix : List.of("work-1", "work-4", "review-1", "review-4-resume-2")) {
            assertThat(preferences.forTask(task("FP-ai~code~" + suffix, "flexible-plan")))
                    .isEqualTo(new WorkflowAiPreferences.Preferences("custom-model", "high"));
        }
        assertThat(preferences.forTask(task("RA-decision", "workflow-repair-authorization"))).isNull();
        assertThat(preferences.forTask(task("FP-ai~other~work-1", "flexible-plan"))).isNull();
    }

    private WorkTask task(String id, String subject) {
        return new WorkTask(new TaskId(id), TaskType.EXTERNAL_WORK, TaskState.READY, subject,
                "FP-ai", Set.of("coding"), "executor", "Implement", null, null,
                TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
