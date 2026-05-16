package com.inovace.logsummarizer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovace.logsummarizer.client.GeminiClient;
import com.inovace.logsummarizer.dto.LogEntry;
import com.inovace.logsummarizer.dto.LogSummaryResponse;
import com.inovace.logsummarizer.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates the whole "summarize logs" operation.
 *
 * This is the only class that talks to both the prompt builder and the AI
 * client, which keeps the controller simple and the client focused purely on
 * HTTP. The full pipeline is:
 *
 *   logs -> build prompt -> call Gemini -> clean response -> parse JSON -> result
 *
 * @author Monirul
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogSummarizerService {

    // Both dependencies are injected by Spring through the constructor.
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;

    /*
     * A dedicated JSON parser for the AI model's output.
     *
     * FAIL_ON_UNKNOWN_PROPERTIES is turned off so that, if the model adds an
     * extra field, parsing still succeeds instead of throwing. The field names
     * themselves are mapped by the @JsonProperty annotations on
     * LogSummaryResponse.
     *
     * One shared instance is reused because ObjectMapper is thread-safe but
     * relatively expensive to create.
     */
    private static final ObjectMapper LLM_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /*
     * Some models wrap their JSON in markdown code fences (```json ... ```).
     * This pattern is used to strip those fences if they appear.
     */
    private static final Pattern CODE_FENCE = Pattern.compile(
            "(?s)^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$");

    /**
     * Analyzes a batch of logs and returns a structured summary.
     *
     * @param logs the validated, non-empty list of logs.
     * @return the parsed summary produced by the AI model.
     * @throws UpstreamServiceException if the model is unreachable or returns
     *                                  something that cannot be parsed as JSON.
     */
    public LogSummaryResponse summarize(List<LogEntry> logs) {
        log.info("Summarizing batch of {} log entries", logs.size());

        // 1. Build the prompt from the logs.
        String prompt = promptBuilder.build(logs);

        // 2. Send it to the AI model and get back the raw response text.
        String llmRawText = geminiClient.generate(prompt);

        // 3. Remove markdown code fences if the model added any.
        String cleaned = stripCodeFences(llmRawText);

        // 4. Parse the JSON text into a typed response object.
        try {
            LogSummaryResponse response = LLM_MAPPER.readValue(cleaned, LogSummaryResponse.class);
            log.debug("Summary produced: severity={}, services={}",
                    response.severity(), response.affectedServices());
            return response;
        } catch (JsonProcessingException e) {
            // The model returned something that is not valid JSON.
            log.error("AI model returned non-JSON / malformed JSON. Payload was: {}", cleaned);
            throw new UpstreamServiceException(
                    "AI provider returned a malformed response. Please retry.", e);
        }
    }

    /**
     * If the given text is wrapped in a ```json ... ``` markdown block,
     * returns just the inner content; otherwise returns the text unchanged.
     */
    private String stripCodeFences(String text) {
        Matcher matcher = CODE_FENCE.matcher(text);
        return matcher.matches() ? matcher.group(1) : text;
    }
}
