package com.inovace.logsummarizer;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * This service takes a batch of application logs over a REST API and uses
 * a generative AI model (Google Gemini) to produce a structured root-cause
 * summary - severity, affected services, recurring errors and a recommended
 * next step.
 *
 * High-level flow:
 *   HTTP request  ->  Controller  ->  Service  ->  GeminiClient  ->  Gemini API
 *
 * The @OpenAPIDefinition below provides the metadata shown on the Swagger UI
 * page (available at /swagger-ui.html once the app is running).
 *
 * @author Monirul
 */
@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "Log Summarizer API",
                version = "1.0.0",
                description = "Analyzes a batch of application logs and returns an "
                            + "AI-generated, human-readable root-cause summary.",
                contact = @Contact(name = "Inovace Technologies", email = "engineering@inovace.com"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local development")
        }
)
public class LogSummarizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogSummarizerApplication.class, args);
    }
}
