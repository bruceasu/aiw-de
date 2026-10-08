package me.asu.aidev.autonomous.protocol.http;

import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.domain.AutonomousRun;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r8/runs")
public class AutonomousRunController {
    private final AutonomousRunService runs;
    public AutonomousRunController(AutonomousRunService runs) { this.runs = runs; }

    @GetMapping("/{runId}")
    public RunResponse find(@PathVariable String runId) {
        return toResponse(runs.find(runId).orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + runId)));
    }

    @PostMapping("/{runId}/recover")
    public RunResponse recover(@PathVariable String runId, @RequestBody RecoverRequest request) {
        return toResponse(runs.recover(runId, request.recoveredBy()));
    }

    @PostMapping("/{runId}/pause")
    public RunResponse pause(@PathVariable String runId, @RequestBody OperatorControlRequest request) {
        return toResponse(runs.pause(runId, request.controlledBy(), request.reason()));
    }

    @PostMapping("/{runId}/resume")
    public RunResponse resume(@PathVariable String runId, @RequestBody OperatorControlRequest request) {
        return toResponse(runs.resume(runId, request.controlledBy(), request.reason()));
    }

    @PostMapping("/{runId}/cancel")
    public RunResponse cancel(@PathVariable String runId, @RequestBody OperatorControlRequest request) {
        return toResponse(runs.cancel(runId, request.controlledBy(), request.reason()));
    }

    @PostMapping("/{runId}/scheduling")
    public RunResponse scheduling(@PathVariable String runId, @RequestBody SchedulingControlRequest request) {
        return toResponse(runs.updateMaxParallelism(
                runId, request.maxParallelism(), request.controlledBy(), request.reason()));
    }

    private static RunResponse toResponse(AutonomousRun run) {
        String effectiveStatus = switch (run.controlState()) {
            case PAUSED -> "PAUSED";
            case CANCELLED -> "CANCELLED";
            case ACTIVE -> run.status().name();
        };
        return new RunResponse(
                run.id(), run.status().name(), run.controlState().name(), effectiveStatus, run.terminal(),
                run.requirementTaskId().value(),
                run.terminalSourceTaskId() == null ? null : run.terminalSourceTaskId().value(), run.resultRevision(),
                run.approvalRequestId() == null ? null : run.approvalRequestId().value(),
                run.lastFailedTaskId() == null ? null : run.lastFailedTaskId().value(),
                run.lastFailureReason(), run.failureCount(), run.recoveryCount(),
                run.lastRecoveredBy(), run.lastRecoveredAt(),
                run.pauseCount(), run.resumeCount(), run.lastControlAction(), run.lastControlledBy(),
                run.lastControlReason(), run.lastControlledAt(), run.cancelledAt(),
                run.maxParallelism(), run.activeSlots(), run.maxParallelism() == 0 ? null : run.availableSlots(),
                run.overcommittedSlots(), run.startedAt(), run.statusUpdatedAt(), run.completedAt());
    }

    public record RecoverRequest(String recoveredBy) {}
    public record OperatorControlRequest(String controlledBy, String reason) {}
    public record SchedulingControlRequest(int maxParallelism, String controlledBy, String reason) {}
    public record RunResponse(
            String runId, String status, String controlState, String effectiveStatus, boolean terminal, String requirementTaskId,
            String terminalSourceTaskId, String resultRevision, String approvalRequestId,
            String lastFailedTaskId, String lastFailureReason, int failureCount, int recoveryCount,
            String lastRecoveredBy, Instant lastRecoveredAt, int pauseCount, int resumeCount,
            String lastControlAction, String lastControlledBy, String lastControlReason,
            Instant lastControlledAt, Instant cancelledAt, int maxParallelism, int activeSlots, Integer availableSlots,
            int overcommittedSlots, Instant startedAt, Instant statusUpdatedAt, Instant completedAt) {}
}
