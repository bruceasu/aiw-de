package me.asu.aidev.development.protocol.http;

import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r1/engineering-gates")
public class EngineeringGateController {
    private final EngineeringGateService gates;

    public EngineeringGateController(EngineeringGateService gates) {
        this.gates = gates;
    }

    @GetMapping("/{sourceTaskId}")
    public ResponseEntity<EngineeringGateResponse> bySourceTask(@PathVariable String sourceTaskId) {
        return gates.find(new TaskId(sourceTaskId))
                .map(EngineeringGateController::response)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/latest-passed")
    public ResponseEntity<EngineeringGateResponse> latestPassed(
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        return gates.findLatestPassed(subjectType, subjectId)
                .map(EngineeringGateController::response)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static EngineeringGateResponse response(EngineeringGate gate) {
        return new EngineeringGateResponse(
                gate.sourceTaskId().value(),
                gate.sourceTaskType().name(),
                gate.subjectType(),
                gate.subjectId(),
                gate.status().name(),
                gate.repositoryRef(),
                gate.baseRevision(),
                gate.resultRevision(),
                gate.publicationRef(),
                gate.changedFiles(),
                evidence(gate.reviewTaskId().value(), gate.reviewReport()),
                evidence(gate.verificationTaskId().value(), gate.verificationReport()),
                gate.closedAt());
    }

    private static EvidenceResponse evidence(String taskId, CompletionReport report) {
        return new EvidenceResponse(taskId, report.verdict().name(), report.summary());
    }

    public record EvidenceResponse(String taskId, String verdict, String summary) {}

    public record EngineeringGateResponse(
            String sourceTaskId,
            String sourceTaskType,
            String subjectType,
            String subjectId,
            String status,
            String repositoryRef,
            String baseRevision,
            String resultRevision,
            String publicationRef,
            List<String> changedFiles,
            EvidenceResponse review,
            EvidenceResponse verification,
            Instant closedAt) {}
}
