package com.inovace.logsummarizer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovace.logsummarizer.config.GeminiProperties;
import com.inovace.logsummarizer.exception.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Handles all communication with the Google Gemini API.
 *
 * <h2>Responsibility</h2>
 * This class is intentionally narrow: it only knows how to send a prompt to
 * Gemini and return the text that comes back. It does not build prompts and it
 * does not parse the final business response - that belongs to the service
 * layer. Because of this clean separation, switching to a different AI provider
 * (OpenAI, Anthropic, AWS Bedrock) would only require changing this one class.
 *
 * <h2>Resilience</h2>
 * External APIs fail sometimes, so the call is protected by two safety nets:
 *   - @Retry          : automatically retries a few times on transient errors.
 *   - @CircuitBreaker : if Gemini keeps failing, it "trips" and fails fast for
 *                       a while instead of hammering a broken service.
 * Both are configured in application.properties under the name "gemini".
 *
 * @author Monirul
 */
@Slf4j
@Component
public class GeminiClient {

    /** Shared name linking this client to its resilience settings in application.properties. */
    public static final String RESILIENCE_NAME = "gemini";

    private final WebClient webClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * The constructor is written out explicitly (rather than using Lombok) so
     * the @Qualifier can clearly point at the "geminiWebClient" bean defined
     * in WebClientConfig.
     */
    public GeminiClient(@Qualifier("geminiWebClient") WebClient webClient,
                        GeminiProperties properties,
                        ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends the prompt to Gemini and returns the raw text it produced.
     *
     * The caller (the service layer) is responsible for parsing that text.
     *
     * @param prompt the fully built prompt.
     * @return the raw text content from Gemini's response.
     * @throws UpstreamServiceException if the call fails or the response is empty.
     */
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "fallback")
    @Retry(name = RESILIENCE_NAME)
    public String generate(String prompt) {

        // Gemini endpoint format: /v1beta/models/{model}:generateContent
        String path = "/v1beta/models/%s:generateContent".formatted(properties.model());

        Map<String, Object> requestBody = buildRequestBody(prompt);

        try {
            // Make the HTTP POST call and wait (block) for the response.
            String rawResponse = webClient.post()
                    .uri(uri -> uri.path(path).queryParam("key", properties.apiKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.timeoutMs()));

            // Pull the actual text out of Gemini's nested response structure.
            return extractText(rawResponse);

        } catch (WebClientResponseException ex) {
            // Gemini replied with an HTTP error (e.g. 400, 404, 500).
            log.warn("Gemini returned HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new UpstreamServiceException(
                    "Gemini call failed: HTTP " + ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            // Network error, timeout, or anything else unexpected.
            log.error("Unexpected error calling Gemini", ex);
            throw new UpstreamServiceException("Gemini call failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fallback method invoked by Resilience4j when the circuit breaker is open
     * or all retries have been used up.
     *
     * Its signature must match generate(String) plus a trailing Throwable.
     * Here it simply converts the failure into a clear UpstreamServiceException,
     * which GlobalExceptionHandler turns into a 503 response.
     */
    @SuppressWarnings("unused")
    private String fallback(String prompt, Throwable cause) {
        log.warn("Gemini fallback triggered (circuit open or retries exhausted): {}", cause.toString());
        throw new UpstreamServiceException(
                "AI provider is currently unavailable. Please retry shortly.", cause);
    }

    /* ----------------------------- helpers ----------------------------- */

    /**
     * Builds the JSON request body that Gemini expects.
     *
     * The "responseMimeType: application/json" line asks Gemini to reply with
     * pure JSON, which makes the response easier and safer to parse.
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", properties.temperature(),
                        "maxOutputTokens", properties.maxOutputTokens(),
                        "responseMimeType", "application/json"
                )
        );
    }

    /**
     * Digs the generated text out of Gemini's response.
     *
     * Gemini wraps the answer fairly deep:
     *   candidates[0].content.parts[0].text
     *
     * @throws UpstreamServiceException if that field is missing or empty.
     */
    private String extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0)
                                    .path("content").path("parts").path(0)
                                    .path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new UpstreamServiceException(
                        "Gemini response did not contain any text. Body: " + rawResponse);
            }
            return textNode.asText();

        } catch (UpstreamServiceException e) {
            throw e;  // already the right type - just re-throw.
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse Gemini response", e);
        }
    }
}
