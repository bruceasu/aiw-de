package me.asu.aidev.autonomous.port;

import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import java.util.Optional;

public interface AutonomousRunDashboardRepository {
    Optional<AutonomousRunDashboard> project(String runId);
}
