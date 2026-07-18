package com.tradingcapture.allocation.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Shared setup for the M2M-client tests: a per-test WireMock server plus deterministic Resilience4j
 * registries (tiny windows/waits, no sleeps) and a plain {@link RestClient} (no OAuth2 interceptor —
 * token acquisition is verified separately) with a configurable read timeout. This exercises the
 * real HTTP adapter, exception translation, retry, and circuit breaker against real sockets.
 */
abstract class M2MClientTestSupport {

    protected WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
        MDC.clear();
    }

    protected String baseUrl() {
        return wireMock.baseUrl();
    }

    /** A RestClient pointing at WireMock with the given read timeout and correlation propagation. */
    protected RestClient restClient(long readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout((int) readTimeoutMillis);
        return RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(factory)
                .requestInterceptor(new CorrelationPropagationInterceptor())
                .build();
    }

    protected RestClient restClient() {
        // Generous read timeout so WireMock latency under full-suite container load never turns a
        // non-timeout test into a spurious retry (the dedicated timeout test sets its own short one).
        return restClient(10_000);
    }

    /** Executor with 3 attempts (1 + 2 retries), 5-call breaker window opening at 50% failures. */
    protected RemoteCallExecutor executor() {
        return executor(3, 5);
    }

    protected RemoteCallExecutor executor(int maxAttempts, int windowSize) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(new RetryableDependencyPredicate())
                .build();
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(windowSize)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(new RetryableDependencyPredicate())
                .build();
        return new RemoteCallExecutor(
                RetryRegistry.of(retryConfig),
                CircuitBreakerRegistry.of(breakerConfig),
                new DependencyMetrics(new SimpleMeterRegistry()));
    }

    protected DependencyClientsProperties properties() {
        return new DependencyClientsProperties();
    }
}
