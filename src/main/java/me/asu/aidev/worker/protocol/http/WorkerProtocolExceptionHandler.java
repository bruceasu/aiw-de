package me.asu.aidev.worker.protocol.http;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = WorkerProtocolV1Controller.class)
public class WorkerProtocolExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProtocolError> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new ProtocolError("bad_request", error.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProtocolError> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ProtocolError("state_conflict", error.getMessage(), Instant.now()));
    }

    public record ProtocolError(String code, String message, Instant timestamp) {}
}
