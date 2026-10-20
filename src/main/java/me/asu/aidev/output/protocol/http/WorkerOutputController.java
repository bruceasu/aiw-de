package me.asu.aidev.output.protocol.http;

import java.util.List;
import me.asu.aidev.output.application.TaskOutputService;
import me.asu.aidev.output.domain.TaskOutputBundle;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected by the existing WorkerProtocolTokenFilter; each operation also checks the task lease. */
@RestController
@RequestMapping("/api/r1/worker-protocol/v1/tasks/{taskId}/outputs")
public class WorkerOutputController {
    private final TaskOutputService outputs;
    public WorkerOutputController(TaskOutputService outputs) { this.outputs = outputs; }

    @PostMapping
    public TaskOutputBundle register(@PathVariable String taskId, @RequestBody TaskOutputService.Registration request) {
        return outputs.register(taskId, request);
    }

    public record ReadRequest(String workerId, String leaseToken, String attemptId, String bundleId) {}

    @PostMapping("/read")
    public List<JdbcTaskOutputRepository.StoredFile> read(@PathVariable String taskId, @RequestBody ReadRequest request) {
        return outputs.read(taskId, request.workerId(), request.leaseToken(), request.attemptId(), request.bundleId());
    }
}
