package me.asu.aidev.approval.application;

import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.approval.port.ApprovalLifecycleListener;
import me.asu.aidev.approval.port.ApprovalNotificationPort;
import me.asu.aidev.approval.port.ApprovalRequestRepository;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.work.domain.TaskId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ApprovalService {
    private static final Logger LOG = Logger.getLogger(ApprovalService.class.getName());
    private final ApprovalRequestRepository approvals;
    private final EngineeringGateService engineeringGates;
    private final List<ApprovalNotificationPort> notificationPorts;
    private final List<ApprovalLifecycleListener> lifecycleListeners;
    private final Clock clock;

    @Autowired
    public ApprovalService(ApprovalRequestRepository approvals,
                           EngineeringGateService engineeringGates,
                           List<ApprovalNotificationPort> notificationPorts,
                           List<ApprovalLifecycleListener> lifecycleListeners) {
        this(approvals, engineeringGates, notificationPorts, lifecycleListeners, Clock.systemUTC());
    }

    ApprovalService(ApprovalRequestRepository approvals,
                    EngineeringGateService engineeringGates,
                    List<ApprovalNotificationPort> notificationPorts,
                    List<ApprovalLifecycleListener> lifecycleListeners,
                    Clock clock) {
        this.approvals = Objects.requireNonNull(approvals);
        this.engineeringGates = Objects.requireNonNull(engineeringGates);
        this.notificationPorts = List.copyOf(notificationPorts);
        this.lifecycleListeners = List.copyOf(lifecycleListeners);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public ApprovalRequest request(RequestApprovalCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.sourceTaskId(), "sourceTaskId");
        if (command.requestedBy() == null || command.requestedBy().isBlank()) {
            throw new IllegalArgumentException("requestedBy is blank");
        }
        Objects.requireNonNull(command.channel(), "channel");

        var gate = engineeringGates.find(command.sourceTaskId())
                .orElseThrow(() -> new NoSuchElementException("engineering gate not found: " + command.sourceTaskId().value()));
        if (gate.status() != EngineeringGateStatus.PASSED) {
            throw new IllegalStateException("human approval requires PASSED engineering gate");
        }
        Optional<ApprovalRequest> existing = approvals.findBySourceTask(command.sourceTaskId());
        if (existing.isPresent()) return existing.get();

        Instant now = clock.instant();
        ApprovalRequest request = new ApprovalRequest(
                new ApprovalRequestId("APR-" + UUID.randomUUID()),
                gate.sourceTaskId(),
                gate.subjectType(),
                gate.subjectId(),
                gate.resultRevision(),
                gate.closedAt(),
                ApprovalStatus.PENDING,
                command.requestedBy().strip(),
                command.channel(),
                command.channelRef(),
                now,
                null,
                null,
                null);

        boolean inserted = approvals.insertIfAbsent(request);
        ApprovalRequest authoritative = inserted
                ? request
                : approvals.findBySourceTask(command.sourceTaskId()).orElseThrow();
        if (inserted) {
            for (ApprovalLifecycleListener listener : lifecycleListeners) listener.onRequested(authoritative);
            notifyAfterCommit(authoritative);
        }
        return authoritative;
    }

    @Transactional
    public ApprovalRequest decide(DecideApprovalCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.approvalRequestId(), "approvalRequestId");
        Objects.requireNonNull(command.decision(), "decision");
        if (command.decidedBy() == null || command.decidedBy().isBlank()) {
            throw new IllegalArgumentException("decidedBy is blank");
        }

        ApprovalRequest current = approvals.find(command.approvalRequestId())
                .orElseThrow(() -> new NoSuchElementException("approval request not found: " + command.approvalRequestId().value()));
        ApprovalStatus target = command.decision().terminalStatus();
        String actor = command.decidedBy().strip();
        String note = normalize(command.note());

        if (current.status() != ApprovalStatus.PENDING) {
            if (current.status() == target && Objects.equals(current.decidedBy(), actor)
                    && Objects.equals(current.decisionNote(), note)) {
                return current;
            }
            throw new IllegalStateException("approval request is already decided: " + current.status());
        }

        if (!approvals.decide(command.approvalRequestId(), ApprovalStatus.PENDING, target, actor, note, clock.instant())) {
            ApprovalRequest raced = approvals.find(command.approvalRequestId()).orElseThrow();
            if (raced.status() == target && Objects.equals(raced.decidedBy(), actor)
                    && Objects.equals(raced.decisionNote(), note)) {
                return raced;
            }
            throw new IllegalStateException("approval request decision conflict");
        }
        ApprovalRequest decided = approvals.find(command.approvalRequestId()).orElseThrow();
        for (ApprovalLifecycleListener listener : lifecycleListeners) listener.onDecided(decided);
        return decided;
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalRequest> find(ApprovalRequestId id) {
        return approvals.find(id);
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalRequest> findBySourceTask(TaskId sourceTaskId) {
        return approvals.findBySourceTask(sourceTaskId);
    }

    private void notifyAfterCommit(ApprovalRequest request) {
        if (notificationPorts.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyPorts(request);
                }
            });
        } else {
            notifyPorts(request);
        }
    }

    private void notifyPorts(ApprovalRequest request) {
        for (ApprovalNotificationPort port : notificationPorts) {
            if (!port.supports(request)) continue;
            try {
                port.approvalRequested(request);
            } catch (RuntimeException e) {
                // The approval authority is already committed. Transport retry/reconciliation belongs to the adapter layer.
                LOG.log(Level.WARNING, "approval notification adapter failed for " + request.id().value(), e);
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }
}
