package com.inovace.logsummarizer.service;

import com.inovace.logsummarizer.dto.LogEntry;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the text prompt that is sent to the AI model.
 *
 * <h2>Why this is its own class</h2>
 * The prompt is the most important part of any AI integration - it decides how
 * good and how consistent the model's answers are. Keeping it isolated here
 * (instead of mixing it into the service code) means it can be unit-tested,
 * tracked in version control, and improved over time without touching anything
 * else.
 *
 * <h2>How the prompt is structured</h2>
 * The prompt has two parts that get joined together:
 *   1. SYSTEM_INSTRUCTION - fixed instructions telling the model who it is,
 *      how to analyze the logs, and exactly what JSON to return.
 *   2. The actual log lines - formatted one per line and appended at the end.
 *
 * <h2>Prompt-engineering techniques used</h2>
 * Five well-known techniques are applied inside SYSTEM_INSTRUCTION to make the
 * model reliable:
 *   - Role / persona  : the model is told it is a Senior SRE, so it reasons
 *                       like one.
 *   - Step-by-step    : numbered analysis steps push the model to think in
 *                       order, which reduces made-up answers.
 *   - Output contract : an exact JSON schema is given so the response can be
 *                       parsed directly into a Java object.
 *   - Few-shot example: one worked example shows the expected level of detail.
 *   - Safety rules    : explicit "do not invent data" instructions guard
 *                       against hallucination.
 *
 * @author Monirul
 */
@Component
public class PromptBuilder {

    /*
     * The fixed instruction block. This is the heart of the prompt - everything
     * the model needs to know BEFORE it sees the logs.
     *
     * It is package-private (not private) only so the unit test can read it and
     * verify the important rules are still present.
     */
    static final String SYSTEM_INSTRUCTION = """
            You are a Senior Site Reliability Engineer (SRE) analyzing application logs
            for a distributed microservice system. Your job is to detect anomalies,
            correlate events across services, and produce an actionable summary.

            ## Analysis steps (follow in order)
            1. Group logs by service and severity.
            2. Identify recurring error signatures (same root message appearing more than once,
               or semantically similar messages).
            3. Look for temporal correlation - errors in service A closely followed by errors
               in service B may indicate a cascading failure.
            4. Determine the most likely root cause. Be specific (e.g. "DB connection pool
               exhausted in payment-service", not just "DB issue").
            5. Classify overall severity:
                 LOW       - sporadic, non-customer-impacting warnings.
                 MEDIUM    - elevated error rate, partial degradation, retryable.
                 HIGH      - sustained errors, customer impact likely, action needed soon.
                 CRITICAL  - outage, cascading failures, immediate action required.

            ## Output contract
            Return ONLY a JSON object that matches this schema. No markdown, no prose,
            no code fences:

            {
              "summary":            "<one to three sentences, plain English>",
              "key_error_signatures": ["<distinct recurring error 1>", "<distinct recurring error 2>"],
              "recommendation":     "<one concrete next step for the on-call engineer>",
              "severity":           "LOW | MEDIUM | HIGH | CRITICAL",
              "affected_services":   ["service-a", "service-b"]
            }

            ## Constraints
            - Do NOT invent service names, error codes, or numbers not present in the logs.
            - If the logs are insufficient to draw a conclusion, say so in `summary` and
              set `severity` to LOW.
            - `key_error_signatures` must be drawn from the actual log messages (paraphrased
              to remove dynamic values like timestamps or IDs).
            - Keep `summary` under 400 characters.
            - ALWAYS return the JSON object, even for trivial, informational, or single-line
              logs. If nothing concerning is found, set `summary` to a brief observation,
              `key_error_signatures` to an empty array, and `severity` to LOW. NEVER return
              plain text or an empty response.

            ## Example
            Input logs (abbreviated):
                ERROR payment-service "Database connection timed out after 3001ms" x5
                ERROR order-service   "Failed to call payment-service: 504 Gateway Timeout" x4
                WARN  user-service    "Login latency p95 = 4.2s"

            Correct output:
            {
              "summary": "Repeated database timeouts in payment-service are propagating as 504s to order-service and elevating user-service login latency. Likely a payment-service DB connectivity issue causing cascading failures.",
              "key_error_signatures": ["Database connection timed out", "Failed to call payment-service: 504 Gateway Timeout"],
              "recommendation": "Check DB connection pool saturation and network path between payment-service and its database; consider tripping the payment-service circuit breaker upstream.",
              "severity": "HIGH",
              "affected_services": ["payment-service", "order-service", "user-service"]
            }
            """;

    // Used to render each log's timestamp in a consistent ISO-8601 format.
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Builds the complete prompt: the fixed instructions, followed by the
     * caller's log lines, followed by a final cue asking for the JSON output.
     *
     * @param logs the validated, non-empty list of logs to analyze.
     * @return the full prompt text ready to send to the AI model.
     */
    public String build(List<LogEntry> logs) {
        // Turn the list of logs into a block of text, one log per line.
        String renderedLogs = logs.stream()
                .map(this::renderLine)
                .collect(Collectors.joining("\n"));

        // Glue the three parts together.
        return SYSTEM_INSTRUCTION
                + "\n\n## Logs to analyze (" + logs.size() + " entries)\n"
                + renderedLogs
                + "\n\n## Your JSON output:\n";
    }

    /**
     * Formats one log entry as a single, compact line.
     *
     * Example output:
     *   2026-05-17T10:00:05Z | ERROR | payment-service          | Database connection timed out
     *
     * A simple, fixed layout like this is easy for the model to read and keeps
     * each log on exactly one line.
     */
    private String renderLine(LogEntry entry) {
        return "%s | %-5s | %-24s | %s".formatted(
                TIMESTAMP_FORMAT.format(entry.timestamp()),
                entry.level(),
                entry.service(),
                // Collapse any newlines/tabs so the message stays on one line.
                entry.message().replaceAll("\\s+", " ").trim()
        );
    }
}
