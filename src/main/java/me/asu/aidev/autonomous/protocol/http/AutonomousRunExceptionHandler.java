package me.asu.aidev.autonomous.protocol.http;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AutonomousRunController.class, AutonomousRunTimelineController.class})
public class AutonomousRunExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RunError> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new RunError("bad_request", error.getMessage(), Instant.now()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<RunError> notFound(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RunError("not_found", error.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RunError> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RunError("state_conflict", error.getMessage(), Instant.now()));
    }

    public record RunError(String code, String message, Instant timestamp) {}
}
