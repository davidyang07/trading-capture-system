package com.tradingcapture.allocation.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Supplier;

/**
 * Wraps every remote HTTP call in the Resilience4j retry + circuit breaker for its dependency and
 * translates raw HTTP failures into a typed {@link DependencyCallException} <em>before</em> the
 * resilience predicates see them — so retry and breaker-recording act only on genuinely transient
 * transport failures (timeouts, connection loss, 5xx), never on 4xx business/authorization results.
 * A circuit-open ({@link CallNotPermittedException}) is surfaced as a retryable UNAVAILABLE failure.
 *
 * <p>Logs carry the dependency, operation, outcome, and correlation id (via MDC) — never a token,
 * Authorization header, response body, or secret.</p>
 */
@Component
public class RemoteCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemoteCallExecutor.class);

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DependencyMetrics metrics;

    public RemoteCallExecutor(RetryRegistry retryRegistry,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              DependencyMetrics metrics) {
        this.retryRegistry = retryRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.metrics = metrics;
    }

    /**
     * Execute {@code httpCall} (a raw RestClient invocation) under the named dependency's resilience
     * policies. Returns the successful result or throws a typed {@link DependencyCallException}.
     */
    public <T> T call(String dependency, String instance, String operation, Supplier<T> httpCall) {
        Retry retry = retryRegistry.retry(instance);
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(instance);

        Supplier<T> translating = () -> {
            try {
                return httpCall.get();
            } catch (DependencyCallException e) {
                throw e;
            } catch (RestClientResponseException e) {
                throw fromStatus(dependency, e);
            } catch (RuntimeException e) {
                throw fromThrowable(dependency, e);
            }
        };

        // Retry outermost so each attempt passes through the circuit breaker, which records only
        // transient failures (per the predicate) — 4xx business results neither retry nor trip it.
        Supplier<T> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(breaker, translating));

        try {
            T result = decorated.get();
            metrics.record(dependency, operation, "success");
            return result;
        } catch (CallNotPermittedException e) {
            metrics.record(dependency, operation, "circuit_open");
            log.warn("Dependency call short-circuited: dependency={} operation={} (circuit open)",
                    dependency, operation);
            throw new DependencyCallException(DependencyFailureType.UNAVAILABLE, dependency,
                    "circuit open");
        } catch (DependencyCallException e) {
            metrics.record(dependency, operation, e.type().outcome());
            log.warn("Dependency call failed: dependency={} operation={} type={}",
                    dependency, operation, e.type());
            throw e;
        }
    }

    private static DependencyCallException fromStatus(String dependency, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        DependencyFailureType type;
        if (status == 401 || status == 403) {
            type = DependencyFailureType.UNAUTHORIZED;
        } else if (status == 503) {
            type = DependencyFailureType.UNAVAILABLE;
        } else if (e.getStatusCode().is5xxServerError()) {
            type = DependencyFailureType.SERVER_ERROR;
        } else {
            type = DependencyFailureType.BAD_REQUEST;
        }
        return new DependencyCallException(type, dependency, "HTTP " + status);
    }

    /**
     * Classify a non-status boundary failure by walking the cause chain — the timeout/connect
     * cause can be wrapped differently (ResourceAccessException, a conversion exception, …) depending
     * on where in the exchange it surfaced, so type detection must not depend on the outermost class.
     */
    private static DependencyCallException fromThrowable(String dependency, Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof TokenAcquisitionException token) {
                return new DependencyCallException(token.isTransient()
                        ? DependencyFailureType.UNAVAILABLE : DependencyFailureType.UNAUTHORIZED,
                        dependency, token.getMessage());
            }
            if (cause instanceof SocketTimeoutException) {
                return new DependencyCallException(DependencyFailureType.TIMEOUT, dependency, "read/connect timeout");
            }
            if (cause instanceof ConnectException) {
                return new DependencyCallException(DependencyFailureType.UNAVAILABLE, dependency, "connection refused");
            }
            if (cause instanceof HttpMessageConversionException) {
                return new DependencyCallException(DependencyFailureType.MALFORMED_RESPONSE, dependency, "unreadable response");
            }
        }
        if (throwable instanceof ResourceAccessException) {
            return new DependencyCallException(DependencyFailureType.UNAVAILABLE, dependency, "I/O error");
        }
        if (throwable instanceof RestClientException) {
            // Non-status client error (e.g. unknown content type, unreadable body).
            return new DependencyCallException(DependencyFailureType.MALFORMED_RESPONSE, dependency, "unreadable response");
        }
        // Any other unexpected failure at the boundary — fail safe (terminal, not retried).
        return new DependencyCallException(DependencyFailureType.UNEXPECTED, dependency,
                throwable.getClass().getSimpleName());
    }
}
