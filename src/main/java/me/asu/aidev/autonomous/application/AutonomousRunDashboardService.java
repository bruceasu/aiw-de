package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import me.asu.aidev.autonomous.port.AutonomousRunDashboardRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AutonomousRunDashboardService {
    private final AutonomousRunDashboardRepository repository;

    public AutonomousRunDashboardService(AutonomousRunDashboardRepository repository) {
        this.repository = repository;
    }

    public Optional<AutonomousRunDashboard> find(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is blank");
        return repository.project(runId.strip());
    }
}
