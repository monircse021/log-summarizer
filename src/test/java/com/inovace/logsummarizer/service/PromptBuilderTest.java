package com.inovace.logsummarizer.service;

import com.inovace.logsummarizer.dto.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PromptBuilder}.
 *
 * These tests do not start Spring - they just create the builder directly,
 * which makes them very fast. The goal is to confirm that the generated prompt
 * keeps the parts we depend on: the SRE persona, the JSON contract, the log
 * count, and proper single-line formatting of each log.
 *
 * @author Monirul
 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void promptContainsRolePersonaAndJsonContract() {
        String prompt = builder.build(List.of(sampleLog()));

        // The key instructions must still be present in the prompt.
        assertThat(prompt)
                .contains("Senior Site Reliability Engineer")
                .contains("Return ONLY a JSON object")
                .contains("\"severity\"")
                .contains("LOW | MEDIUM | HIGH | CRITICAL");
    }

    @Test
    void promptRendersEachLogAsSingleLine() {
        // A log message that contains newlines and tabs.
        LogEntry multiline = new LogEntry(
                OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                "ERROR",
                "svc",
                "line one\nline two\tline three"
        );
        String prompt = builder.build(List.of(multiline));

        // After rendering, that message must sit on exactly one line.
        String renderedLine = prompt.lines()
                .filter(line -> line.contains("svc"))
                .findFirst()
                .orElseThrow();
        assertThat(renderedLine)
                .doesNotContain("\n")
                .contains("line one line two line three");
    }

    @Test
    void promptIncludesLogCount() {
        String prompt = builder.build(List.of(sampleLog(), sampleLog(), sampleLog()));
        assertThat(prompt).contains("(3 entries)");
    }

    /** A small, reusable log entry for the tests above. */
    private LogEntry sampleLog() {
        return new LogEntry(
                OffsetDateTime.parse("2026-05-17T10:00:05Z"),
                "ERROR",
                "payment-service",
                "Database connection timed out after 3001ms"
        );
    }
}
