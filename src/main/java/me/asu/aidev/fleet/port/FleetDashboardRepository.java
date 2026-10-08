package me.asu.aidev.fleet.port;

import me.asu.aidev.fleet.domain.FleetDashboard;

public interface FleetDashboardRepository {
    FleetDashboard project(int detailLimit);
}
