package me.asu.aidev.execution.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkspaceBaselineServiceTest {
    final WorkTaskRepository tasks=mock(WorkTaskRepository.class);
    final ExecutionAttemptRepository attempts=mock(ExecutionAttemptRepository.class);
    final TaskExecutionService service=new TaskExecutionService(tasks,mock(WorkerRepository.class),attempts,null,null,null,List.of(),List.of(),List.of(),null);
    final TaskId id=new TaskId("plan~code~work-1"); final WorkerId worker=new WorkerId("worker");final LeaseToken lease=new LeaseToken("lease");
    final String revision="a".repeat(40);
    WorkTask task(String baseline,Set<String> capabilities) { return new WorkTask(id,TaskType.EXTERNAL_WORK,TaskState.RUNNING,"flexible-plan","plan",capabilities,
            "coding-work-package-executor","Implement","repo",baseline,TaskPriority.NORMAL,2,Instant.EPOCH,Instant.EPOCH); }
    @BeforeEach void setup() {
        when(tasks.lock(id)).thenReturn(true);when(tasks.find(id)).thenReturn(Optional.of(task(null,Set.of("coding"))));
        when(tasks.attemptNo(id)).thenReturn(1);when(attempts.findAttemptId(lease)).thenReturn(Optional.of(new ExecutionAttemptId("attempt")));
    }
    @Test void pinsAndReturnsServerGeneratedWorkspaceWithAudit() {
        when(tasks.pinBaselineOwned(eq(id),eq(worker),eq(lease),eq(revision),any())).thenReturn(true);
        when(tasks.find(id)).thenReturn(Optional.of(task(null,Set.of("coding"))),Optional.of(task(revision,Set.of("coding"))));
        var assignment=service.pinWorkspaceBaseline(id,worker,lease,revision);
        assertThat(assignment.baseRevision()).isEqualTo(revision);assertThat(assignment.branchName()).startsWith("aidev/");
        verify(tasks).recordControlEvent(anyString(),eq(id),eq("attempt"),eq("PIN_BASELINE"),eq("worker:worker"),contains(revision),eq(false),any());
    }
    @Test void guardedScopeCannotBeChanged() {
        when(tasks.find(id)).thenReturn(Optional.of(task(null,Set.of("coding","execution-guard-v1"))));
        assertThatThrownBy(() -> service.pinWorkspaceBaseline(id,worker,lease,revision)).isInstanceOf(IllegalStateException.class);
        verify(tasks,never()).pinBaselineOwned(any(),any(),any(),anyString(),any());
    }
    @Test void ownershipConflictDoesNotCreateAuditOrWorkspace() {
        assertThatThrownBy(() -> service.pinWorkspaceBaseline(id,worker,lease,revision)).isInstanceOf(IllegalStateException.class);
        verify(tasks,never()).recordControlEvent(anyString(),any(),anyString(),anyString(),anyString(),anyString(),anyBoolean(),any());
    }
    @Test void mutableRevisionIsRejected() {
        assertThatThrownBy(() -> service.pinWorkspaceBaseline(id,worker,lease,"HEAD")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tasks);
    }
}
