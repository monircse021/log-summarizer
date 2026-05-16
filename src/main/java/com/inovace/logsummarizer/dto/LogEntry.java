package com.inovace.logsummarizer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Represents one log line sent to the API.
 *
 * This is a Java record - a compact, immutable data holder. Records are a good
 * fit here because a log entry never changes once received.
 *
 * The validation annotations (@NotNull, @NotBlank, @Size) are checked
 * automatically when the request reaches the controller, so we never have to
 * write manual null/blank checks.
 *
 * @author Monirul
 */
@Schema(description = "A single application log record.")
public record LogEntry(

        @Schema(example = "2026-05-17T10:00:05Z", description = "When the log event happened (ISO-8601).")
        @NotNull(message = "timestamp is required")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime timestamp,

        @Schema(example = "ERROR", description = "Log level: DEBUG, INFO, WARN, ERROR or FATAL.")
        @NotBlank(message = "level is required")
        @Size(max = 16)
        String level,

        @Schema(example = "payment-service", description = "Name of the service that produced the log.")
        @NotBlank(message = "service is required")
        @Size(max = 128)
        String service,

        @Schema(example = "Database connection timed out after 3001ms",
                description = "The log message text.")
        @NotBlank(message = "message is required")
        @Size(max = 4_000, message = "message must be 4000 characters or fewer")
        String message
) { }
