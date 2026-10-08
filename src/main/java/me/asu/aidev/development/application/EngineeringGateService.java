package me.asu.aidev.development.application;

import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.port.EngineeringGateRepository;
import me.asu.aidev.work.domain.TaskId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineeringGateService {
    private final EngineeringGateRepository gates;

    public EngineeringGateService(EngineeringGateRepository gates) {
        this.gates = gates;
    }

    @Transactional(readOnly = true)
    public Optional<EngineeringGate> find(TaskId sourceTaskId) {
        return gates.findBySourceTask(sourceTaskId);
    }

    @Transactional(readOnly = true)
    public Optional<EngineeringGate> findLatestPassed(String subjectType, String subjectId) {
        if (subjectType == null || subjectType.isBlank()) throw new IllegalArgumentException("subjectType is blank");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is blank");
        return gates.findLatestPassed(subjectType.strip(), subjectId.strip());
    }
}
