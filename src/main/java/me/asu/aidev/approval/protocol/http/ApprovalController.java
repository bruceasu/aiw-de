package me.asu.aidev.approval.protocol.http;

import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.application.DecideApprovalCommand;
import me.asu.aidev.approval.application.RequestApprovalCommand;
import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalDecision;
import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r7/approvals")
public class ApprovalController {
    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping
    public ApprovalResponse request(@RequestBody ApprovalRequestBody body) {
        ApprovalChannel channel = body.channel() == null || body.channel().isBlank()
                ? ApprovalChannel.API
                : ApprovalChannel.valueOf(body.channel().strip().toUpperCase());
        return response(approvals.request(new RequestApprovalCommand(
                new TaskId(body.sourceTaskId()), body.requestedBy(), channel, body.channelRef())));
    }

    @PostMapping("/{approvalRequestId}/decision")
    public ApprovalResponse decide(@PathVariable String approvalRequestId, @RequestBody DecisionBody body) {
        ApprovalDecision decision = ApprovalDecision.valueOf(body.decision().strip().toUpperCase());
        return response(approvals.decide(new DecideApprovalCommand(
                new ApprovalRequestId(approvalRequestId), decision, body.decidedBy(), body.note())));
    }

    @GetMapping("/{approvalRequestId}")
    public ResponseEntity<ApprovalResponse> find(@PathVariable String approvalRequestId) {
        return approvals.find(new ApprovalRequestId(approvalRequestId))
                .map(ApprovalController::response)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-source/{sourceTaskId}")
    public ResponseEntity<ApprovalResponse> findBySource(@PathVariable String sourceTaskId) {
        return approvals.findBySourceTask(new TaskId(sourceTaskId))
                .map(ApprovalController::response)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorResponse> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    private static ApprovalResponse response(ApprovalRequest request) {
        return new ApprovalResponse(
                request.id().value(), request.sourceTaskId().value(), request.subjectType(), request.subjectId(),
                request.resultRevision(), request.engineeringGateClosedAt(), request.status().name(),
                request.requestedBy(), request.channel().name(), request.channelRef(), request.requestedAt(),
                request.decidedBy(), request.decisionNote(), request.decidedAt());
    }

    public record ApprovalRequestBody(String sourceTaskId, String requestedBy, String channel, String channelRef) {}
    public record DecisionBody(String decision, String decidedBy, String note) {}
    public record ErrorResponse(String message) {}
    public record ApprovalResponse(
            String approvalRequestId,
            String sourceTaskId,
            String subjectType,
            String subjectId,
            String resultRevision,
            Instant engineeringGateClosedAt,
            String status,
            String requestedBy,
            String channel,
            String channelRef,
            Instant requestedAt,
            String decidedBy,
            String decisionNote,
            Instant decidedAt) {}
}
