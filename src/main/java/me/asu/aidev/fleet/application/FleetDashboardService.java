package me.asu.aidev.fleet.application;

import me.asu.aidev.fleet.domain.FleetDashboard;
import me.asu.aidev.fleet.port.FleetDashboardRepository;
import org.springframework.stereotype.Service;

@Service
public class FleetDashboardService {
    public static final int DEFAULT_DETAIL_LIMIT = 100;
    public static final int MAX_DETAIL_LIMIT = 500;

    private final FleetDashboardRepository repository;

    public FleetDashboardService(FleetDashboardRepository repository) {
        this.repository = repository;
    }

    public FleetDashboard dashboard(Integer detailLimit) {
        int limit = detailLimit == null ? DEFAULT_DETAIL_LIMIT : detailLimit;
        if (limit < 1 || limit > MAX_DETAIL_LIMIT) {
            throw new IllegalArgumentException("detailLimit must be between 1 and " + MAX_DETAIL_LIMIT);
        }
        return repository.project(limit);
    }
}
