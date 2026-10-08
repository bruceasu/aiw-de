package me.asu.aidev.autonomous.protocol.http;

import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.work.domain.TaskPriority;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/r8/requirements")
public class AutonomousRequirementController {
    private final AutonomousRequirementService service;
    public AutonomousRequirementController(AutonomousRequirementService service) { this.service = service; }

    @PostMapping
    public StartResponse start(@RequestBody StartRequest request) {
        TaskPriority priority = parsePriority(request.priority());
        var run = service.start(new StartAutonomousRequirementCommand(
                request.requestId(), request.requirement(), request.repositoryRef(), request.baseRevision(), request.technicalCapabilities(), priority,
                request.maxParallelism()));
        return new StartResponse(run.runId(), run.requirementTaskId().value());
    }

    private static TaskPriority parsePriority(String value) {
        if (value == null || value.isBlank()) return TaskPriority.NORMAL;
        return switch (value.strip().toUpperCase()) {
            case "LOW" -> TaskPriority.LOW;
            case "NORMAL" -> TaskPriority.NORMAL;
            case "HIGH" -> TaskPriority.HIGH;
            case "CRITICAL" -> TaskPriority.CRITICAL;
            default -> new TaskPriority(Integer.parseInt(value.strip()));
        };
    }

    public record StartRequest(String requestId, String requirement, String repositoryRef, String baseRevision,
                               Set<String> technicalCapabilities, String priority, Integer maxParallelism) {}
    public record StartResponse(String runId, String requirementTaskId) {}
}
