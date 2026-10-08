package me.asu.aidev.autonomous.protocol.http;

import me.asu.aidev.autonomous.application.AutonomousRunTimelineService;
import me.asu.aidev.autonomous.domain.AutonomousRun;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r8/runs")
public class AutonomousRunTimelineController {
    private final AutonomousRunTimelineService timelines;

    public AutonomousRunTimelineController(AutonomousRunTimelineService timelines) {
        this.timelines = timelines;
    }

    @GetMapping("/{runId}/timeline")
    public TimelineResponse timeline(@PathVariable String runId,
                                     @RequestParam(required = false) Integer limit) {
        var result = timelines.get(runId, limit);
        AutonomousRun run = result.run();
        String effectiveStatus = switch (run.controlState()) {
            case PAUSED -> "PAUSED";
            case CANCELLED -> "CANCELLED";
            case ACTIVE -> run.status().name();
        };
        return new TimelineResponse(
                run.id(), run.status().name(), run.controlState().name(), effectiveStatus,
                result.totalEntries(), result.returnedEntries(), result.truncated(), result.entries());
    }

    public record TimelineResponse(
            String runId,
            String status,
            String controlState,
            String effectiveStatus,
            int totalEntries,
            int returnedEntries,
            boolean truncated,
            List<AutonomousRunTimelineEntry> entries) {}
}
