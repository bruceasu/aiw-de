package me.asu.aidev.output.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.Base64;
import java.util.List;
import me.asu.aidev.output.application.TaskOutputService;
import me.asu.aidev.output.domain.TaskOutputBundle;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ui/api/task-outputs")
public class TaskOutputController {
    private final TaskOutputService outputs;
    private final WebConsoleSecurityService security;
    public TaskOutputController(TaskOutputService outputs, WebConsoleSecurityService security) {
        this.outputs = outputs; this.security = security;
    }

    @GetMapping("/tasks/{taskId}")
    public List<TaskOutputBundle> list(@PathVariable String taskId, HttpSession session) {
        requireRead(session);
        return outputs.list(taskId);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable String fileId, HttpSession session) {
        requireRead(session);
        var file = outputs.file(fileId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(file.file().path().replace('/', '_'), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", file.file().sha256())
                .header("X-Content-Type-Options", "nosniff")
                .body(Base64.getDecoder().decode(file.contentBase64()));
    }

    public record PublicationRequest(String reviewTaskId, String targetPath, String reason) {}

    @PostMapping("/files/{fileId}/publication-plans")
    public WorkflowPlan publication(@PathVariable String fileId, @RequestBody PublicationRequest request, HttpSession session) {
        security.requireOperator(session);
        return outputs.publication(fileId, request.reviewTaskId(), request.targetPath(), request.reason(), security.actor(session));
    }

    private void requireRead(HttpSession session) {
        if (!security.authenticated(session)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}
