package me.asu.aidev.autonomous.protocol.http;

import me.asu.aidev.autonomous.application.AutonomousRunDashboardService;
import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r8/runs")
public class AutonomousRunDashboardController {
    private final AutonomousRunDashboardService dashboards;

    public AutonomousRunDashboardController(AutonomousRunDashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/{runId}/dashboard")
    public AutonomousRunDashboard dashboard(@PathVariable String runId) {
        return dashboards.find(runId)
                .orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + runId));
    }
}
