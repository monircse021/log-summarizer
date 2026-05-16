package com.inovace.logsummarizer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Holds all Gemini-related settings, read from application.properties
 * (any key starting with "gemini.").
 *
 * Using a typed properties class instead of scattered @Value annotations gives
 * us two benefits:
 *   1. All Gemini config lives in one place and is easy to find.
 *   2. @Validated checks the values at startup - if the API key is missing or
 *      a number is out of range, the app fails fast instead of breaking later.
 *
 * @param baseUrl          Base URL of Google's Generative Language API.
 * @param model            Model name, e.g. "gemini-2.5-flash".
 * @param apiKey           API key (set via the GEMINI_API_KEY environment variable).
 * @param timeoutMs        How long to wait for Gemini before timing out (ms).
 * @param temperature      0.0-1.0; lower means more consistent, factual output.
 * @param maxOutputTokens  Maximum size of the model's response.
 *
 * @author Monirul
 */
@Validated
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotBlank String apiKey,
        @Min(1_000) int timeoutMs,
        double temperature,
        @Min(64) int maxOutputTokens
) { }
