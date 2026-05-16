package com.inovace.logsummarizer.exception;

import com.inovace.logsummarizer.dto.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * One central place to handle every error the API can produce.
 *
 * Handling exceptions here keeps the controller free of try/catch blocks and
 * guarantees that every error - no matter where it comes from - is returned in
 * the same {@link ApiError} shape with a sensible HTTP status code.
 *
 * Each method below maps a specific kind of exception to an HTTP response.
 *
 * @author Monirul
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation failures on the request body (e.g. a blank field or
     * an empty logs list). Returns 400 with the list of field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "Validation failed", fieldErrors));
    }

    /**
     * Handles validation failures on path or query parameters. Returns 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    /**
     * Handles a request body that is not valid JSON (e.g. broken syntax or a
     * field with the wrong type). Returns 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request",
                        "Malformed JSON request: " + rootMessage(ex)));
    }

    /**
     * Handles the case where the circuit breaker is open - meaning Gemini has
     * been failing repeatedly and we are deliberately failing fast. Returns 503.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker is open: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Service Unavailable",
                        "AI provider is temporarily unavailable. Please retry shortly."));
    }

    /**
     * Handles any problem with the upstream AI provider - unreachable, HTTP
     * error, or an unparseable response. Returns 503.
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiError> handleUpstream(UpstreamServiceException ex) {
        log.warn("Upstream (AI provider) failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Service Unavailable", ex.getMessage()));
    }

    /**
     * Catch-all for anything not handled above. Returns 500.
     *
     * The real exception is logged for debugging, but only a generic message
     * is sent to the client - internal details and stack traces are never
     * leaked over the API.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please check the server logs."));
    }

    /** Walks down the cause chain to find the most specific error message. */
    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
