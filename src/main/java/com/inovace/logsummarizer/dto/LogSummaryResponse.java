package com.inovace.logsummarizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The response returned to the caller after a batch of logs is analyzed.
 *
 * This is also the exact shape the AI model is asked to produce. Keeping the
 * AI output strongly typed (instead of returning raw text) means clients get
 * predictable, machine-readable data they can filter, sort or alert on.
 *
 * The JSON field names use snake_case (key_error_signatures, affected_services)
 * to match the response format given in the challenge specification. The
 * @JsonProperty annotations map those JSON names to the Java fields.
 *
 * @JsonInclude(NON_NULL) keeps the JSON tidy by dropping any null fields.
 *
 * @author Monirul
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AI-generated summary of the supplied logs.")
public record LogSummaryResponse(

        @Schema(example = "A potential database connectivity issue in 'payment-service' is causing "
                        + "cascading failures, indicated by repeated timeout errors.",
                description = "Short, human-readable root-cause summary.")
        @JsonProperty("summary")
        String summary,

        @Schema(description = "The recurring error messages the model identified.")
        @JsonProperty("key_error_signatures")
        List<String> keyErrorSignatures,

        @Schema(example = "Investigate database load and network latency for the payment-service.",
                description = "A concrete next step for the on-call engineer.")
        @JsonProperty("recommendation")
        String recommendation,

        @Schema(example = "HIGH",
                description = "Severity level: LOW, MEDIUM, HIGH or CRITICAL.")
        @JsonProperty("severity")
        String severity,

        @Schema(description = "The services the model believes are affected.")
        @JsonProperty("affected_services")
        List<String> affectedServices
) { }
