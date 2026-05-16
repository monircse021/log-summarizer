package com.inovace.logsummarizer.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Builds the WebClient used to talk to the Gemini API.
 *
 * WebClient is Spring's modern HTTP client (the replacement for RestTemplate).
 * Creating it here as a single shared @Bean - rather than building a new one
 * inside GeminiClient - means timeouts and settings are configured once, in
 * one place, and the client can be reused efficiently.
 *
 * Connect, read and write timeouts are all wired up so a slow or hung Gemini
 * call can never block one of our threads indefinitely.
 *
 * @author Monirul
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class WebClientConfig {

    /**
     * Creates the Gemini WebClient.
     *
     * The bean is given the explicit name "geminiWebClient" so it can be
     * injected without ambiguity even if another WebClient is added later.
     */
    @Bean("geminiWebClient")
    public WebClient geminiWebClient(GeminiProperties properties) {

        // Configure the underlying Netty HTTP client with timeouts.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.timeoutMs())
                .responseTimeout(Duration.ofMillis(properties.timeoutMs()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.timeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.timeoutMs(), TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Allow up to 2 MB responses - large enough for Gemini, but still bounded.
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
