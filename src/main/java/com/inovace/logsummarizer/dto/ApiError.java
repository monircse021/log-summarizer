package com.inovace.logsummarizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A single, consistent shape for every error the API returns.
 *
 * Using one error format everywhere makes life easier for client developers -
 * they only have to handle one structure no matter what went wrong. This is
 * built and returned by GlobalExceptionHandler.
 *
 * The two static factory methods (of) are just convenience helpers so callers
 * don't have to repeat the timestamp every time.
 *
 * @author Monirul
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response.")
public record ApiError(

        @Schema(example = "2026-05-17T10:00:05Z") OffsetDateTime timestamp,
        @Schema(example = "400")                  int status,
        @Schema(example = "Bad Request")          String error,
        @Schema(example = "logs[0].level: must not be blank") String message,
        @Schema(description = "Field-level errors. Only present for validation failures.")
        List<String> details
) {
    /** Build an error without field-level details. */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(OffsetDateTime.now(), status, error, message, null);
    }

    /** Build an error that includes a list of field-level validation messages. */
    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(OffsetDateTime.now(), status, error, message, details);
    }
}
