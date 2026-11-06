package me.asu.aidev.execution.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.PlanLocations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskHandoffsTest {
    private static final String TASK = "task-1", ATTEMPT = "attempt-1";
    private static final String WORKER = "worker-1", TOKEN = "lease-1";
    private static final String LOCATION = "worker-defined-location";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final PlanLocations locations = mock(PlanLocations.class);
    private TaskHandoffs handoffs;
    private TaskHandoffs.Reference reference;

    @BeforeEach void setup() throws Exception {
        handoffs = spy(new TaskHandoffs(jdbc, tasks, locations));
        reference = new TaskHandoffs.Reference(path(TASK, ATTEMPT), "a".repeat(64));
        when(jdbc.queryForList(contains("SELECT a.id,a.state"), eq(TASK), eq(WORKER),
                eq(TOKEN), eq(TOKEN), anyLong()))
                .thenReturn(List.of(Map.<String, Object>of("id", ATTEMPT, "state", "RUNNING")));
        doReturn(null).when(handoffs).input(ATTEMPT);
        var task = new WorkTask(new TaskId(TASK), TaskType.EXTERNAL_WORK, TaskState.RUNNING,
                "flexible-plan", "plan-1", Set.of("coding"), "coding-work-package-executor",
                "Implement the task", null, null, TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH);
        when(tasks.find(new TaskId(TASK))).thenReturn(Optional.of(task));
        when(locations.forTask(task)).thenReturn(LOCATION);
    }

    @Test void recordsOwnedAttemptAndOpaqueLocationWithoutReadingFiles() {
        handoffs.record(TASK, WORKER, TOKEN, reference);

        verify(jdbc).update(startsWith("INSERT INTO execution_handoff"), eq(ATTEMPT), eq(TASK),
                eq(LOCATION), eq(reference.manifest()), eq(reference.sha256()));
    }

    @Test void rejectsManifestFromAnotherProducingAttempt() throws Exception {
        var other = new TaskHandoffs.Reference(path(TASK, "another-attempt"), reference.sha256());

        assertThatThrownBy(() -> handoffs.record(TASK, WORKER, TOKEN, other))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("this task and attempt");
        verifyNoInteractions(tasks, locations);
    }

    @Test void rejectsMissingOrExpiredLeaseBeforeRecording() {
        when(jdbc.queryForList(contains("SELECT a.id,a.state"), eq(TASK), eq(WORKER),
                eq(TOKEN), eq(TOKEN), anyLong())).thenReturn(List.of());

        assertThatThrownBy(() -> handoffs.record(TASK, WORKER, TOKEN, reference))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("owned lease");
        verifyNoInteractions(tasks, locations);
    }

    @Test void refusesToReplaceAnAlreadyRecordedDigest() {
        doReturn(new TaskHandoffs.Input(LOCATION, reference.manifest(), "b".repeat(64)))
                .when(handoffs).input(ATTEMPT);

        assertThatThrownBy(() -> handoffs.record(TASK, WORKER, TOKEN, reference))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable");
        verifyNoInteractions(tasks, locations);
    }

    @Test void identicalReferenceDoesNotAllocateOrInsertAgain() {
        doReturn(new TaskHandoffs.Input(LOCATION, reference.manifest(), reference.sha256()))
                .when(handoffs).input(ATTEMPT);

        handoffs.record(TASK, WORKER, TOKEN, reference);

        verifyNoInteractions(tasks, locations);
        verify(jdbc, never()).update(startsWith("INSERT INTO execution_handoff"), any(), any(), any(), any(), any());
    }

    @Test void missingReferenceCannotBecomeACompletionHandoff() {
        assertThatThrownBy(() -> handoffs.record(TASK, WORKER, TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("handoff required");
        verifyNoInteractions(jdbc, tasks, locations);
    }

    private static String path(String task, String attempt) throws Exception {
        return ".ai/handoffs/" + sha256(task) + "/" + sha256(attempt) + "/manifest.json";
    }
    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
