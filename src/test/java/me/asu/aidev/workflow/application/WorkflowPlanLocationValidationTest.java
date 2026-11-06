package me.asu.aidev.workflow.application;

import java.util.List;
import java.util.Set;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowPlanLocationValidationTest {
    private WorkflowPlanSpec spec(String goal, String location) {
        return new WorkflowPlanSpec(goal, WorkflowPlanSpec.Source.AI, List.of(new WorkflowPlanSpec.Step(
                "code", Set.of("coding"), Set.of(), "coding-work-package-executor", "Implement the goal.",
                null, null, Set.of())), location);
    }

    @Test void explicitlyNamedWorkspaceCannotSilentlyUseTemporaryLocation() {
        for (String goal : List.of("workspace: D:\\project\\sudoku\nAdd themes.",
                "Add themes.\n工作空间：D:\\project\\sudoku", "  LOCATION: /project/sudoku")) {
            assertThatThrownBy(() -> WorkflowEditorService.validateSpec(spec(goal, null), false))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Set Plan location explicitly");
        }
    }

    @Test void selectedLocationAndIntentionallyTemporaryPlansRemainSupported() {
        assertThatCode(() -> WorkflowEditorService.validateSpec(spec("workspace: D:\\project", "D:\\project"), false))
                .doesNotThrowAnyException();
        assertThatCode(() -> WorkflowEditorService.validateSpec(spec("Create a new Sudoku page.", null), false))
                .doesNotThrowAnyException();
    }

    @Test void recommendationCandidateCanWaitForTheHumanLocationChoice() {
        assertThatCode(() -> WorkflowEditorService.validateSpec(spec("workspace: D:\\project", null), true))
                .doesNotThrowAnyException();
    }
}
