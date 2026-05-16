# Log Summarizer

AI-powered microservice that analyzes a batch of application logs and returns a structured root-cause summary using Google Gemini.

Built with **Spring Boot 3.3** + **Java 21** for the Inovace Technologies challenge.

---

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Gemini API key — get one free at [aistudio.google.com/apikey](https://aistudio.google.com/apikey)

### Run Locally

```bash
export GEMINI_API_KEY="your-key-here"
mvn clean package -DskipTests
java -jar target/log-summarizer-1.0.0.jar
```

### Run with Docker

```bash
echo "GEMINI_API_KEY=your-key-here" > .env
docker compose up --build
```

App runs at **http://localhost:8080**.

---

## 🔗 Important Links (After Starting the App)

### API Documentation

| Link | Purpose |
|---|---|
| 🎨 **[Swagger UI](http://localhost:8080/swagger-ui.html)** | Interactive API documentation — try endpoints from your browser |
| 📄 **[OpenAPI JSON](http://localhost:8080/v3/api-docs)** | Raw OpenAPI 3 specification (for client SDK generation) |
| 📄 **[OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml)** | Same spec in YAML format |

### Monitoring & Health (Spring Boot Actuator)

| Link | Purpose |
|---|---|
| ❤️ **[Health Check](http://localhost:8080/actuator/health)** | Overall app health (UP / DOWN) |
| 🟢 **[Liveness Probe](http://localhost:8080/actuator/health/liveness)** | App alive? (Kubernetes liveness) |
| 🔵 **[Readiness Probe](http://localhost:8080/actuator/health/readiness)** | App ready for traffic? (Kubernetes readiness) |
| 📊 **[Metrics](http://localhost:8080/actuator/metrics)** | JVM, HTTP, system metrics list |
| 📈 **[Prometheus](http://localhost:8080/actuator/prometheus)** | Prometheus-format metrics for scraping |
| ⚡ **[Circuit Breakers](http://localhost:8080/actuator/circuitbreakers)** | Live Resilience4j circuit breaker state |
| ℹ️ **[App Info](http://localhost:8080/actuator/info)** | Application metadata |
| 🗂️ **[All Endpoints](http://localhost:8080/actuator)** | List of all available actuator endpoints |

### Main API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | [`/summarize-logs`](http://localhost:8080/summarize-logs) | Analyze a batch of logs (1–500 entries) |

> The same endpoint is also available at `/api/v1/summarize-logs` as a versioned alias.

> 💡 **Tip:** Open [Swagger UI](http://localhost:8080/swagger-ui.html) first — it's the easiest way to explore and test all endpoints.

---

## Try It Out

### Option 1: Swagger UI (easiest)

1. Open <http://localhost:8080/swagger-ui.html>
2. Expand `POST /summarize-logs`
3. Click **Try it out**
4. Paste the sample request (below)
5. Click **Execute**

### Option 2: curl

```bash
curl -X POST http://localhost:8080/summarize-logs \
  -H "Content-Type: application/json" \
  --data @sample-request.json
```

### Sample Request

```json
{
  "logs": [
    {
      "timestamp": "2026-05-17T10:00:05Z",
      "level": "ERROR",
      "service": "payment-service",
      "message": "Database connection timed out after 3001ms"
    },
    {
      "timestamp": "2026-05-17T10:00:08Z",
      "level": "ERROR",
      "service": "order-service",
      "message": "Failed to call payment-service: 504 Gateway Timeout"
    }
  ]
}
```

### Sample Response

```json
{
  "summary": "Database timeouts in payment-service are cascading as 504 errors to order-service.",
  "key_error_signatures": [
    "Database connection timed out",
    "Failed to call payment-service: 504 Gateway Timeout"
  ],
  "recommendation": "Investigate payment-service DB connection pool and network latency.",
  "severity": "HIGH",
  "affected_services": ["payment-service", "order-service"]
}
```

**Response codes:** `200` success · `400` validation error · `503` AI provider unavailable

---

## Architecture

```
Client → Controller → Service → GeminiClient → Gemini API
                         ↓
                    PromptBuilder
```

| Component | Responsibility |
|---|---|
| `LogSummaryController` | REST endpoint, validation, OpenAPI |
| `LogSummarizerService` | Orchestrates prompt → call → parse |
| `PromptBuilder` | Builds the engineered LLM prompt |
| `GeminiClient` | HTTP call with Retry + Circuit Breaker |
| `GlobalExceptionHandler` | Unified error responses |

Swap to a different LLM (OpenAI, Claude) by replacing only `GeminiClient`.

---

## Design Decisions

| Choice | Reason |
|---|---|
| **Java 21 Records** | Immutable DTOs, less boilerplate |
| **WebClient** (not RestTemplate) | RestTemplate is deprecated |
| **Resilience4j** | Retry + Circuit Breaker for LLM call stability |
| **Bean Validation** | Declarative request validation |
| **springdoc-openapi** | Auto-generated Swagger UI |
| **JSON-mode prompt** | LLM returns structured JSON, not free text |
| **Temperature 0.2** | Deterministic output for SRE summaries |

---

## The Prompt

The full engineered prompt lives in [`PromptBuilder.java`](src/main/java/com/inovace/logsummarizer/service/PromptBuilder.java). Five prompt-engineering techniques are applied:

1. **Role assignment** — model adopts an SRE persona
2. **Chain-of-thought** — numbered analysis steps reduce hallucination
3. **Output contract** — strict JSON schema for safe parsing
4. **Few-shot example** — calibrates expected detail level
5. **Safety rails** — explicit anti-hallucination instructions

---

## Resilience

The Gemini call is wrapped in two patterns (configured in `application.properties`):

- **Retry** — 3 attempts with exponential backoff (500ms → 1s → 2s)
- **Circuit Breaker** — opens at 50% failure rate over 20 calls, recovers after 30s

Inspect live state at <http://localhost:8080/actuator/circuitbreakers>.

---

## Testing

```bash
mvn test
```

- `PromptBuilderTest` — verifies prompt structure (unit)
- `LogSummaryControllerTest` — full HTTP flow with mocked LLM (integration)

---

## Configuration

All settings are in `src/main/resources/application.properties`. Override via env vars:

| Variable | Default |
|---|---|
| `GEMINI_API_KEY` | _(required)_ |
| `SERVER_PORT` | `8080` |
| `GEMINI_MODEL` | `gemini-2.5-flash` |

---

## Project Structure

```
log-summarizer/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── sample-request.json
└── src/
    ├── main/
    │   ├── java/com/inovace/logsummarizer/
    │   │   ├── controller/   # REST endpoint
    │   │   ├── service/      # Orchestration + prompt
    │   │   ├── client/       # Gemini HTTP client
    │   │   ├── config/       # Beans + properties
    │   │   ├── dto/          # Request/response records
    │   │   └── exception/    # Global error handling
    │   └── resources/application.properties
    └── test/
```

---

## License

Submitted to Inovace Technologies for evaluation.
