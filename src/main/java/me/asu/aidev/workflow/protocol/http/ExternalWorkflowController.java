package me.asu.aidev.workflow.protocol.http;

import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Generic, bounded DAG submission for independently implemented workflows. */
@RestController
@RequestMapping("/api/workflows")
@ConditionalOnProperty(prefix = "aidev.external-workflow", name = "enabled", havingValue = "true")
public class ExternalWorkflowController {
    private final WorkTaskService taskService;
    private final WorkTaskRepository tasks;
    private final String submissionToken;
    private final String requestToken;

    public ExternalWorkflowController(WorkTaskService taskService, WorkTaskRepository tasks,
                                      @Value("${AIW_EXTERNAL_WORKFLOW_TOKEN:}") String submissionToken,
                                      @Value("${AIW_EXTERNAL_WORKFLOW_REQUEST_TOKEN:}") String requestToken) {
        this.taskService = taskService;
        this.tasks = tasks;
        this.submissionToken = submissionToken;
        this.requestToken = requestToken;
        if (!requestToken.isBlank() && requestToken.equals(submissionToken)) {
            throw new IllegalArgumentException("workflow request and submission tokens must differ");
        }
    }

    /** Accept an opaque workflow request; its intake Worker proposes the fixed steps. */
    @PostMapping("/{workflowId}/requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartResponse request(@PathVariable String workflowId,
                                 @RequestHeader("X-Workflow-Token") String token,
                                 @RequestBody IntakeRequest request) {
        authorizeRequest(token);
        validateWorkflowId(workflowId);
        if (request.capability() == null || !request.capability().matches("[a-z][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("invalid intake capability");
        }
        if (request.instruction() == null || request.instruction().length() > 16_384) {
            throw new IllegalArgumentException("invalid intake instruction");
        }
        String runId = "WF-" + UUID.randomUUID().toString().replace("-", "");
        taskService.create(new CreateWorkTaskCommand(
                new TaskId(runId + "~intake"), TaskType.EXTERNAL_WORK, workflowId, runId,
                Set.of(request.capability()), request.capability(), request.instruction(),
                null, null, TaskPriority.NORMAL, Set.of()));
        return new StartResponse(workflowId, runId);
    }

    /** Materialize the reviewed intake Worker's plan under the existing run. */
    @PostMapping("/{workflowId}/runs/{runId}/steps")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public StartResponse addSteps(@PathVariable String workflowId, @PathVariable String runId,
                                  @RequestHeader("X-Workflow-Token") String token,
                                  @RequestBody StartRequest request) {
        authorize(token);
        validateWorkflowId(workflowId);
        WorkTask intake = tasks.find(new TaskId(runId + "~intake"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!intake.subjectType().equals(workflowId) || !intake.subjectId().equals(runId)
                || intake.type() != TaskType.EXTERNAL_WORK) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (intake.state() != TaskState.RUNNING && intake.state() != TaskState.COMPLETED) {
            throw new IllegalArgumentException("intake task must be running or completed");
        }
        createSteps(workflowId, runId, request, true);
        return new StartResponse(workflowId, runId);
    }

    @PostMapping("/{workflowId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public StartResponse start(@PathVariable String workflowId,
                               @RequestHeader("X-Workflow-Token") String token,
                               @RequestBody StartRequest request) {
        authorize(token);
        validateWorkflowId(workflowId);
        String runId = "WF-" + UUID.randomUUID().toString().replace("-", "");
        createSteps(workflowId, runId, request, false);
        return new StartResponse(workflowId, runId);
    }

    private void createSteps(String workflowId, String runId, StartRequest request, boolean afterIntake) {
        if (request == null || request.steps() == null || request.steps().isEmpty() || request.steps().size() > 32) {
            throw new IllegalArgumentException("steps must contain 1 to 32 items");
        }
        Set<String> seen = new HashSet<>();
        for (Step step : request.steps()) {
            if (step == null || step.key() == null || !step.key().matches("[a-z][a-z0-9._-]{0,31}")) {
                throw new IllegalArgumentException("invalid step key");
            }
            if (!seen.add(step.key())) throw new IllegalArgumentException("duplicate step key: " + step.key());
            if (afterIntake && step.key().equals("intake")) {
                throw new IllegalArgumentException("intake is a reserved step key");
            }
            if (step.capability() == null || !step.capability().matches("[a-z][a-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException("invalid step capability");
            }
            if (step.instruction() == null || step.instruction().length() > 16_384) {
                throw new IllegalArgumentException("invalid step instruction");
            }
            if (step.dependsOn() != null && step.dependsOn().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("dependency key is null");
            }
            Set<String> predecessors = step.dependsOn() == null ? Set.of() : Set.copyOf(step.dependsOn());
            if (!seen.containsAll(predecessors) || predecessors.contains(step.key())) {
                throw new IllegalArgumentException("steps must be ordered after their dependencies");
            }
            Set<TaskId> dependencies = predecessors.stream()
                    .map(key -> new TaskId(runId + "~" + key)).collect(java.util.stream.Collectors.toSet());
            if (afterIntake && dependencies.isEmpty()) dependencies.add(new TaskId(runId + "~intake"));
            var command = new CreateWorkTaskCommand(
                    new TaskId(runId + "~" + step.key()), TaskType.EXTERNAL_WORK,
                    workflowId, runId, Set.of(step.capability()), step.capability(), step.instruction(),
                    step.repositoryRef(), step.baseRevision(), TaskPriority.NORMAL, dependencies);
            if (afterIntake) taskService.createIfAbsent(command);
            else taskService.create(command);
        }
    }

    @GetMapping("/{workflowId}/runs/{runId}")
    public StatusResponse status(@PathVariable String workflowId, @PathVariable String runId,
                                 @RequestHeader("X-Workflow-Token") String token) {
        authorize(token);
        validateWorkflowId(workflowId);
        List<StepStatus> steps = tasks.findBySubject(workflowId, runId).stream()
                .filter(task -> task.type() == TaskType.EXTERNAL_WORK)
                .map(task -> new StepStatus(task.id().value(), task.state().name()))
                .toList();
        if (steps.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String state = steps.stream().anyMatch(step -> step.state().equals(TaskState.FAILED.name())) ? "FAILED"
                : steps.stream().allMatch(step -> step.state().equals(TaskState.COMPLETED.name())) ? "COMPLETED"
                : "RUNNING";
        return new StatusResponse(workflowId, runId, state, steps);
    }

    private void authorize(String supplied) {
        authorizeToken(submissionToken, supplied);
    }

    private void authorizeRequest(String supplied) {
        authorizeToken(requestToken, supplied);
    }

    private static void authorizeToken(String expected, String supplied) {
        if (expected.isBlank() || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static void validateWorkflowId(String value) {
        if (!value.matches("[a-z][a-z0-9-]{1,40}@v[0-9]{1,4}")) {
            throw new IllegalArgumentException("invalid workflow id");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }

    public record StartRequest(List<Step> steps) {}
    public record IntakeRequest(String capability, String instruction) {}
    public record Step(String key, String capability, String instruction,
                       String repositoryRef, String baseRevision, List<String> dependsOn) {}
    public record StartResponse(String workflowId, String runId) {}
    public record StepStatus(String taskId, String state) {}
    public record StatusResponse(String workflowId, String runId, String state, List<StepStatus> steps) {}
}
