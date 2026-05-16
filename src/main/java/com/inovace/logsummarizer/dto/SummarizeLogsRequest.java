package com.inovace.logsummarizer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The request body for POST /summarize-logs.
 *
 * The list of logs is wrapped inside an object (instead of accepting a plain
 * JSON array) on purpose - it leaves room to add more fields later, such as a
 * request ID or a preferred output language, without breaking existing clients.
 *
 * @Valid on the list tells the validator to also check every LogEntry inside it.
 *
 * @author Monirul
 */
@Schema(description = "Batch of logs to be analyzed.")
public record SummarizeLogsRequest(

        @Schema(description = "The log entries to analyze. Must contain between 1 and 500 items.")
        @NotEmpty(message = "logs must contain at least one entry")
        @Size(max = 500, message = "a batch is limited to 500 log entries")
        @Valid
        List<LogEntry> logs
) { }
