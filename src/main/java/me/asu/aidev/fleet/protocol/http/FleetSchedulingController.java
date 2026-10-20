package me.asu.aidev.fleet.protocol.http;

import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r1/fleet/scheduling")
public class FleetSchedulingController {
    private final FleetAdmissionBudgetService service;

    public FleetSchedulingController(FleetAdmissionBudgetService service) {
        this.service = service;
    }

    @GetMapping
    public FleetSchedulingResponse current() {
        return response(service.snapshot());
    }

    @PostMapping
    public FleetSchedulingResponse update(@RequestBody FleetSchedulingRequest request) {
        return response(service.updateMaxParallelism(
                request.maxParallelism(), request.controlledBy(), request.reason()));
    }

    @GetMapping("/events")
    public List<FleetSchedulingEventResponse> events(@RequestParam(defaultValue = "100") int limit) {
        return service.recentEvents(limit).stream().map(FleetSchedulingController::eventResponse).toList();
    }

    private static FleetSchedulingResponse response(FleetAdmissionBudget budget) {
        return new FleetSchedulingResponse(
                budget.maxParallelism(), budget.activeLeases(), budget.availableSlots(),
                budget.overcommittedSlots(), budget.saturated(), budget.updatedAt());
    }

    private static FleetSchedulingEventResponse eventResponse(FleetAdmissionBudgetEvent event) {
        return new FleetSchedulingEventResponse(
                event.sequenceNo(), event.type().name(), event.actor(), event.previousMaxParallelism(),
                event.maxParallelism(), event.activeLeases(), event.reason(), event.occurredAt());
    }

    public record FleetSchedulingRequest(int maxParallelism, String controlledBy, String reason) {}

    public record FleetSchedulingResponse(
            int maxParallelism,
            int activeLeases,
            Integer availableSlots,
            int overcommittedSlots,
            boolean saturated,
            Instant updatedAt) {}

    public record FleetSchedulingEventResponse(
            long sequenceNo,
            String eventType,
            String controlledBy,
            int previousMaxParallelism,
            int maxParallelism,
            int activeLeases,
            String reason,
            Instant occurredAt) {}
}
