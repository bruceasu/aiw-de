package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceAssignmentTest {
    @Test
    void assignmentIsDeterministicAndAttemptScoped() {
        var task = new WorkTask(new TaskId("WI/unsafe:name"), TaskType.IMPLEMENTATION, TaskState.READY,
                "WORK_ITEM", "WI-1", Set.of("coding", "repo:core"), "coding", "do it",
                "core", "0123456789012345678901234567890123456789",
                TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH);
        var a1 = WorkspaceAssignment.forTask(task, 1);
        var a1Again = WorkspaceAssignment.forTask(task, 1);
        var a2 = WorkspaceAssignment.forTask(task, 2);
        assertThat(a1).isEqualTo(a1Again);
        assertThat(a1.workspaceKey()).isNotEqualTo(a2.workspaceKey());
        assertThat(a1.branchName()).doesNotContain(":").startsWith("aidev/");
        assertThat(a2.branchName()).endsWith("/a2");
    }
}
