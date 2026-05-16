package com.inovace.logsummarizer.controller;

import com.inovace.logsummarizer.dto.ApiError;
import com.inovace.logsummarizer.dto.LogSummaryResponse;
import com.inovace.logsummarizer.dto.SummarizeLogsRequest;
import com.inovace.logsummarizer.service.LogSummarizerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the log-summarizer service.
 *
 * The controller is kept deliberately thin: its only job is to receive the
 * HTTP request, let Spring validate it, and hand the work to the service
 * layer. All the real logic lives in LogSummarizerService.
 *
 * The endpoint is exposed at two paths:
 *   - POST /summarize-logs         - the path defined in the challenge spec.
 *   - POST /api/v1/summarize-logs  - a versioned alias, so a future /api/v2
 *                                    could be added without breaking clients.
 *
 * The Swagger annotations (@Operation, @ApiResponses) describe the endpoint
 * on the auto-generated API documentation page.
 *
 * @author Monirul
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Log Summarizer",
     description = "AI-powered endpoint for analyzing batches of application logs.")
public class LogSummaryController {

    // Injected automatically by Spring via the constructor (@RequiredArgsConstructor).
    private final LogSummarizerService summarizerService;

    /**
     * Accepts a batch of logs and returns an AI-generated summary.
     *
     * The @Valid annotation triggers request validation. If the body is
     * invalid, Spring throws an exception before this method runs, and
     * GlobalExceptionHandler turns it into a clean 400 response.
     *
     * @param request the batch of logs to analyze (1-500 entries).
     * @return the structured summary produced by the AI model.
     */
    @Operation(
            summary = "Summarize a batch of logs",
            description = "Accepts up to 500 log entries and returns an AI-generated, "
                        + "structured root-cause summary including severity, affected "
                        + "services, recurring error signatures and a recommended next action."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary produced successfully.",
                    content = @Content(schema = @Schema(implementation = LogSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "The AI provider is unavailable.",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(
            value = {"/summarize-logs", "/api/v1/summarize-logs"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LogSummaryResponse summarize(@Valid @RequestBody SummarizeLogsRequest request) {
        return summarizerService.summarize(request.logs());
    }
}
