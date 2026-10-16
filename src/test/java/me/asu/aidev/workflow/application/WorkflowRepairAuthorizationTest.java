package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import me.asu.aidev.worker.port.WorkerRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository.Authorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRepairAuthorizationTest {
    private final WorkflowPlanService plans = mock(WorkflowPlanService.class);
    private final JdbcWorkflowRepairAuthorizationRepository repository = mock(JdbcWorkflowRepairAuthorizationRepository.class);
    private final WorkTaskService taskService = mock(WorkTaskService.class);
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final Map<String, Authorization> grants = new LinkedHashMap<>();
    private final WorkflowRepairAuthorizationService service = new WorkflowRepairAuthorizationService(plans, repository, taskService, tasks, workers);
    private static final String PLAN = "FP-authorization", REVIEW = PLAN + "~code~review-3";
    private static final String SCOPE = "Reject extra arguments and empty names; verify on Node.js 22 and npm 10.";

    @BeforeEach
    void setup() {
        var spec = new WorkflowPlanSpec("Greeting", WorkflowPlanSpec.Source.MANAGER, List.of(
                new WorkflowPlanSpec.Step("code", Set.of("coding"), Set.of(), "coding-work-package-executor", "Implement", "test/root", null, Set.of())));
        when(plans.get(PLAN)).thenReturn(new WorkflowPlan(PLAN, WorkflowPlan.State.CONFIRMED, 7, spec, "human", "human", "", Instant.EPOCH, Instant.EPOCH));
        when(plans.reserveRepair(eq(PLAN), eq(REVIEW), eq(7L), eq(5L), eq("human"), anyString()))
                .thenReturn(new WorkflowPlanService.ReviewRecovery(8, REVIEW, 5, true, null));
        when(repository.list(anyString())).thenAnswer(call -> List.copyOf(grants.values()));
        when(repository.find(anyString())).thenAnswer(call -> Optional.ofNullable(grants.get(call.getArgument(0))));
        when(repository.decision(anyString())).thenAnswer(call -> grants.values().stream()
                .filter(grant -> call.getArgument(0).equals(grant.decisionTaskId())).findFirst());
        doAnswer(call -> { Authorization grant = call.getArgument(0); grants.put(grant.id(), grant); return null; }).when(repository).insert(any());
        when(repository.planUnchanged(any())).thenReturn(true);
        when(repository.completedWorker(anyString())).thenAnswer(call -> Optional.of(call.getArgument(0).equals(REVIEW) ? "reviewer" : "coder"));
        when(repository.finish(anyString(), anyString(), nullable(String.class), any())).thenAnswer(call -> {
            Authorization grant = grants.get(call.getArgument(0));
            if (!grant.status().equals("PENDING")) return false;
            grants.put(grant.id(), new Authorization(grant.id(), grant.planId(), grant.planVersion(), grant.sourceReviewId(),
                    grant.sourceTaskVersion(), grant.scope(), grant.reason(), grant.grantedBy(), grant.delegateWorkerId(),
                    grant.decisionTaskId(), call.getArgument(2), call.getArgument(1), grant.createdAt(), grant.expiresAt()));
            return true;
        });
        when(plans.continueAuthorizedRepair(eq(PLAN), eq(REVIEW), eq(8L), eq(5L), anyString(), anyString(), eq(SCOPE)))
                .thenReturn(Optional.of(new TaskId(PLAN + "~code~work-4")));
        when(workers.find(new WorkerId("decider"))).thenReturn(Optional.of(worker("decider")));
    }

    private Worker worker(String id) {
        return new Worker(new WorkerId(id), "codex-app-server:recovery-decider", Set.of(WorkflowRepairAuthorizationService.CAPABILITY),
                1, 0, WorkerStatus.ACTIVE, Instant.now(), Instant.now(), Instant.now());
    }

    private TaskCompletionContext decision(Authorization grant) {
        var task = new WorkTask(new TaskId(grant.decisionTaskId()), TaskType.EXTERNAL_WORK, TaskState.COMPLETED,
                WorkflowRepairAuthorizationService.SUBJECT, grant.id(), Set.of(WorkflowRepairAuthorizationService.CAPABILITY),
                "workflow-recovery-decider", "Decide", null, null, TaskPriority.NORMAL, 1, Instant.EPOCH, Instant.EPOCH);
        return new TaskCompletionContext(task, null, new CompletionReport(CompletionVerdict.PASS, "Use the fixed grant."));
    }

    @Test
    void humanApprovalUsesOnlyOneRoundAndPreservesTheExactScope() {
        var grant = service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Fix findings", null, true, "human");
        assertThat(grant.status()).isEqualTo("CONSUMED");
        assertThat(grant.repairTaskId()).endsWith("~work-4");
        verify(plans).continueAuthorizedRepair(PLAN, REVIEW, 8, 5, "human", "Fix findings", SCOPE);
        verifyNoInteractions(taskService);
    }

    @Test
    void onlyTheNamedIndependentDecisionWorkerCanConsumeTheExistingGrantOnce() {
        var grant = service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Check scope", "decider", true, "human");
        verify(plans, never()).continueAuthorizedRepair(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        var policy = new WorkflowRepairDecisionPolicy(repository);
        assertThat(policy.canClaim(decision(grant).completedTask(), worker("other"))).isFalse();
        assertThat(policy.canClaim(decision(grant).completedTask(), worker("decider"))).isTrue();
        when(repository.completedWorker(grant.decisionTaskId())).thenReturn(Optional.of("decider"));
        assertThat(service.decide(decision(grant))).containsExactly(new TaskId(PLAN + "~code~work-4"));
        assertThat(service.decide(decision(grant))).isEmpty();
        verify(plans).continueAuthorizedRepair(PLAN, REVIEW, 8, 5, "worker:decider", "Check scope", SCOPE);
    }

    @Test
    void aChangedPlanCannotStartRepair() {
        var grant = service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Check scope", "decider", true, "human");
        when(repository.completedWorker(grant.decisionTaskId())).thenReturn(Optional.of("decider"));
        when(repository.planUnchanged(any())).thenReturn(false);
        assertThat(service.decide(decision(grant))).isEmpty();
        assertThat(grants.get(grant.id()).status()).isEqualTo("STALE");
        verify(plans, never()).continueAuthorizedRepair(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void revokedExpiredAndDeclinedGrantsDoNotCreateRepairTasks() {
        var grant = service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Check scope", "decider", true, "human");
        when(repository.completedWorker(grant.decisionTaskId())).thenReturn(Optional.of("decider"));
        service.revoke(PLAN, grant.id(), "human");
        assertThat(service.decide(decision(grant))).isEmpty();
        assertThat(grants.get(grant.id()).status()).isEqualTo("REVOKED");

        grants.put(grant.id(), new Authorization(grant.id(), grant.planId(), grant.planVersion(), grant.sourceReviewId(),
                grant.sourceTaskVersion(), grant.scope(), grant.reason(), grant.grantedBy(), grant.delegateWorkerId(),
                grant.decisionTaskId(), null, "PENDING", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(1)));
        assertThat(service.decide(decision(grant))).isEmpty();
        assertThat(grants.get(grant.id()).status()).isEqualTo("EXPIRED");

        grants.put(grant.id(), grant);
        var context = decision(grant);
        assertThat(service.decide(new TaskCompletionContext(context.completedTask(), null,
                new CompletionReport(CompletionVerdict.REPAIR_REQUIRED, "Scope is insufficient.")))).isEmpty();
        assertThat(grants.get(grant.id()).status()).isEqualTo("DENIED");
        verify(plans, never()).continueAuthorizedRepair(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void anExistingPendingGrantPreventsAnotherAuthorization() {
        service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Check scope", "decider", true, "human");
        assertThatThrownBy(() -> service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Duplicate", null, true, "human"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(grants).hasSize(1);
    }

    @Test
    void confirmationAndIndependenceAreRequired() {
        assertThatThrownBy(() -> service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Fix", null, false, "human"))
                .isInstanceOf(IllegalArgumentException.class);
        when(repository.completedWorker(REVIEW)).thenReturn(Optional.of("decider"));
        assertThatThrownBy(() -> service.authorize(PLAN, REVIEW, 7, 5, SCOPE, "Fix", "decider", true, "human"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(plans, never()).reserveRepair(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
    }
}
