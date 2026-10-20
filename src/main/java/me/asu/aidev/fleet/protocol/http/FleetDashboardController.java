package me.asu.aidev.fleet.protocol.http;

import me.asu.aidev.fleet.application.FleetDashboardService;
import me.asu.aidev.fleet.domain.FleetDashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r1/fleet")
public class FleetDashboardController {
    private final FleetDashboardService dashboards;

    public FleetDashboardController(FleetDashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    public FleetDashboard dashboard(@RequestParam(required = false) Integer detailLimit) {
        return dashboards.dashboard(detailLimit);
    }
}
