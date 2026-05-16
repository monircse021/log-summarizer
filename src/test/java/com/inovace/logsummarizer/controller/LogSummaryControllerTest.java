package com.inovace.logsummarizer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovace.logsummarizer.dto.LogEntry;
import com.inovace.logsummarizer.dto.LogSummaryResponse;
import com.inovace.logsummarizer.dto.SummarizeLogsRequest;
import com.inovace.logsummarizer.service.LogSummarizerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link LogSummaryController}.
 *
 * These tests start the full Spring context and send real HTTP requests using
 * MockMvc. The actual Gemini call is replaced with a mock service, so the
 * tests run fast and offline (no API key needed).
 *
 * Covered cases:
 *   - a valid request returns 200 with a summary;
 *   - an empty logs list returns 400;
 *   - a missing required field returns 400.
 *
 * @author Monirul
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "gemini.api-key=test-key")
class LogSummaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Replaces the real LogSummarizerService with a mock for these tests, so
     * no real call to Gemini is made.
     */
    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        LogSummarizerService mockSummarizer() {
            LogSummarizerService mock = mock(LogSummarizerService.class);
            when(mock.summarize(anyList())).thenReturn(new LogSummaryResponse(
                    "DB issue in payment-service is cascading.",
                    List.of("Database connection timed out"),
                    "Inspect DB connection pool saturation.",
                    "HIGH",
                    List.of("payment-service")
            ));
            return mock;
        }
    }

    @Test
    void validRequestReturns200WithSummary() throws Exception {
        SummarizeLogsRequest body = new SummarizeLogsRequest(List.of(
                new LogEntry(OffsetDateTime.parse("2026-05-17T10:00:05Z"),
                        "ERROR", "payment-service",
                        "Database connection timed out after 3001ms")
        ));

        // Uses the bare "/summarize-logs" path defined in the challenge spec.
        mockMvc.perform(post("/summarize-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.affected_services[0]").value("payment-service"));
    }

    @Test
    void emptyLogsReturns400() throws Exception {
        // An empty logs array should fail the @NotEmpty validation.
        mockMvc.perform(post("/api/v1/summarize-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logs\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingLevelReturns400() throws Exception {
        // The "level" field is required - leaving it out should fail validation.
        String invalidBody = """
                {"logs":[{"timestamp":"2026-05-17T10:00:05Z","service":"x","message":"y"}]}
                """;
        mockMvc.perform(post("/api/v1/summarize-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }
}
