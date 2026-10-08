package me.asu.aidev.fleet.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Applies the deployment Fleet ceiling only while the singleton authority row is still uninitialized. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FleetAdmissionBudgetInitializer implements ApplicationRunner {
    private final FleetAdmissionBudgetService service;

    public FleetAdmissionBudgetInitializer(FleetAdmissionBudgetService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.configureFromDeployment();
    }
}
