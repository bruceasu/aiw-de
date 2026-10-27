package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.workflow.jdbc.JdbcRecommendationRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowWorkerProvisioningTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    WorkTaskRepository tasks=mock(WorkTaskRepository.class);
    JdbcRecommendationRepository workers=mock(JdbcRecommendationRepository.class);
    AgentFactorySettings settings=mock(AgentFactorySettings.class);
    ManagedWorkerProvisioner provisioner=mock(ManagedWorkerProvisioner.class);
    WorkflowWorkerProvisioning service=new WorkflowWorkerProvisioning(db,tasks,workers,settings,provisioner);
    @BeforeEach void setup() {
        when(settings.current()).thenReturn(new AgentFactorySettings.Settings("codex-app-server","model","medium",true,"python","", ""));
        when(db.queryForList(anyString(),eq(String.class),anyString())).thenReturn(List.of("task"));
        when(provisioner.requestNative(anyString(),anyString(),anyString())).thenReturn(new ManagedWorkerProvisioner.Result(true,"Starting"));
        when(tasks.find(new TaskId("task"))).thenReturn(Optional.of(new WorkTask(new TaskId("task"),TaskType.EXTERNAL_WORK,TaskState.READY,
                "flexible-plan","plan",Set.of("coding"),"coding-work-package-executor","Implement","repo",null,TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH)));
    }
    private Worker online(String repo) {
        return new Worker(new WorkerId("online"),"codex-app-server:coder",Set.of("coding","repo:"+repo),1,1,WorkerStatus.ACTIVE,
                Instant.now(),Instant.now(),Instant.now());
    }
    @Test void missingRoleRequestsMatchingNativeWorker() {
        service.supply();verify(provisioner).requestNative("repo","codex-app-server","coder");
    }
    @Test void compatibleBusyWorkerIsReusedInsteadOfDuplicated() {
        when(workers.online(any())).thenReturn(List.of(online("repo")));
        service.supply();verifyNoInteractions(provisioner);
    }
    @Test void differentRepositoryDoesNotSatisfyTheTask() {
        when(workers.online(any())).thenReturn(List.of(online("other")));
        service.supply();verify(provisioner).requestNative("repo","codex-app-server","coder");
    }
    @Test void disabledAutomaticWorkersDoNotStartProcesses() {
        when(settings.current()).thenReturn(new AgentFactorySettings.Settings("codex-app-server","model","medium",false,"python","", ""));
        service.supply();verifyNoInteractions(db,provisioner);
    }
    @Test void blockedPreparationIsVisibleInOriginalPlan() {
        when(provisioner.requestNative("repo","codex-app-server","coder"))
                .thenReturn(new ManagedWorkerProvisioner.Result(false,"Capacity unavailable"));
        service.supply();
        assertThat(service.pending("plan")).containsExactly(new WorkflowWorkerProvisioning.Preparation("task","plan","BLOCKED","Capacity unavailable"));
        assertThat(service.pending("other")).isEmpty();
    }
    @Test void scanAdvancesPastBlockedTasksAndWraps() {
        when(db.queryForList(anyString(),eq(String.class),eq("task"))).thenReturn(List.of());
        service.supply();service.supply();service.supply();
        verify(db,times(2)).queryForList(anyString(),eq(String.class),eq(""));
        verify(db).queryForList(anyString(),eq(String.class),eq("task"));
    }
}
