package me.asu.aidev.fleet.protocol.http;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {FleetDashboardController.class, FleetSchedulingController.class})
public class FleetDashboardExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FleetError> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(new FleetError("bad_request", error.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<FleetError> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new FleetError("state_conflict", error.getMessage(), Instant.now()));
    }

    public record FleetError(String code, String message, Instant timestamp) {}
}
