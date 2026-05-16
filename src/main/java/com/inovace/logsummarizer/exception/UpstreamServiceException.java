package com.inovace.logsummarizer.exception;

/**
 * Thrown when something goes wrong with the upstream AI provider (Gemini).
 *
 * This covers cases such as:
 *   - Gemini is unreachable or times out.
 *   - Gemini returns an HTTP error.
 *   - Gemini returns a response that cannot be parsed.
 *   - The circuit breaker is open after repeated failures.
 *
 * GlobalExceptionHandler catches this and returns HTTP 503 to the client.
 *
 * @author Monirul
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
