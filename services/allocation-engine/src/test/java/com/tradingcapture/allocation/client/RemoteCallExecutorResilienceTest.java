package com.tradingcapture.allocation.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Resilience semantics of {@link RemoteCallExecutor}, driven by supplier fault injection and direct
 * circuit-breaker state transitions (no timing-sensitive sleeps): bounded retry, no retry for
 * permanent business results, breaker opening + short-circuiting, half-open recovery, typed
 * exhaustion failure, and the guarantee that failure messages carry no secrets.
 */
class RemoteCallExecutorResilienceTest {

    private static final String DEP = "dep";
    private static final String INSTANCE = "dep";

    private RetryRegistry retryRegistry(int maxAttempts) {
        return RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .retryOnException(new RetryableDependencyPredicate())
                .build());
    }

    private CircuitBreakerRegistry breakerRegistry(int windowSize) {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(windowSize)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(new RetryableDependencyPredicate())
                .build());
    }

    private RemoteCallExecutor executor(RetryRegistry retry, CircuitBreakerRegistry breaker) {
        return new RemoteCallExecutor(retry, breaker, new DependencyMetrics(new SimpleMeterRegistry()));
    }

    private static Supplier<String> failing(AtomicInteger counter, DependencyFailureType type) {
        return () -> {
            counter.incrementAndGet();
            throw new DependencyCallException(type, DEP, "HTTP " + (type == DependencyFailureType.SERVER_ERROR ? 500 : 400));
        };
    }

    @Test
    void retriesUpToTheConfiguredMaximumThenReturnsATypedFailure() {
        RemoteCallExecutor executor = executor(retryRegistry(3), breakerRegistry(20));
        AtomicInteger attempts = new AtomicInteger();

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> executor.call(DEP, INSTANCE, "op",
                        failing(attempts, DependencyFailureType.SERVER_ERROR)))
                .satisfies(e -> assertThat(e.type()).isEqualTo(DependencyFailureType.SERVER_ERROR));
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryAPermanentBusinessFailure() {
        RemoteCallExecutor executor = executor(retryRegistry(3), breakerRegistry(20));
        AtomicInteger attempts = new AtomicInteger();

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> executor.call(DEP, INSTANCE, "op",
                        failing(attempts, DependencyFailureType.BAD_REQUEST)));
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void circuitOpensAfterFailuresAndShortCircuitsWithoutInvokingTheSupplier() {
        RetryRegistry retry = retryRegistry(1); // one attempt per call keeps the failure count exact
        CircuitBreakerRegistry breakers = breakerRegistry(5);
        RemoteCallExecutor executor = executor(retry, breakers);
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertThatExceptionOfType(DependencyCallException.class).isThrownBy(() ->
                    executor.call(DEP, INSTANCE, "op", failing(attempts, DependencyFailureType.SERVER_ERROR)));
        }
        assertThat(breakers.circuitBreaker(INSTANCE).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int invocationsBefore = attempts.get();
        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> executor.call(DEP, INSTANCE, "op",
                        failing(attempts, DependencyFailureType.SERVER_ERROR)))
                .satisfies(e -> assertThat(e.type()).isEqualTo(DependencyFailureType.UNAVAILABLE));
        assertThat(attempts.get()).isEqualTo(invocationsBefore); // supplier never called while open
    }

    @Test
    void halfOpenRecoveryClosesTheCircuitOnSuccess() {
        RetryRegistry retry = retryRegistry(1);
        CircuitBreakerRegistry breakers = breakerRegistry(5);
        RemoteCallExecutor executor = executor(retry, breakers);
        CircuitBreaker breaker = breakers.circuitBreaker(INSTANCE);
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertThatExceptionOfType(DependencyCallException.class).isThrownBy(() ->
                    executor.call(DEP, INSTANCE, "op", failing(attempts, DependencyFailureType.SERVER_ERROR)));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        breaker.transitionToHalfOpenState(); // deterministic, no sleep
        // Two successful calls (permitted in half-open) close the breaker.
        assertThat(executor.call(DEP, INSTANCE, "op", () -> "ok")).isEqualTo("ok");
        assertThat(executor.call(DEP, INSTANCE, "op", () -> "ok")).isEqualTo("ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void failureMessagesCarryNoSecrets() {
        RemoteCallExecutor executor = executor(retryRegistry(1), breakerRegistry(20));

        DependencyCallException thrown = catchDependencyFailure(() -> executor.call(DEP, INSTANCE, "op", () -> {
            throw new DependencyCallException(DependencyFailureType.UNAUTHORIZED, DEP, "HTTP 401");
        }));

        assertThat(thrown.getMessage())
                .doesNotContainIgnoringCase("bearer")
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("authorization")
                .isEqualTo("[dep] UNAUTHORIZED: HTTP 401");
    }

    private static DependencyCallException catchDependencyFailure(Runnable call) {
        try {
            call.run();
            throw new AssertionError("expected a DependencyCallException");
        } catch (DependencyCallException e) {
            return e;
        }
    }
}
