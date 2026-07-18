package com.tradingcapture.allocation.client;

import java.util.function.Predicate;

/**
 * The single source of truth for "is this failure transient?", used by both the Resilience4j retry
 * (retry only these) and circuit breaker (count only these as failures) via
 * {@code retry-exception-predicate} / {@code record-exception-predicate} in application.yml. Only a
 * retryable {@link DependencyCallException} qualifies — 4xx business/authorization results are
 * neither retried nor allowed to trip the breaker. Resilience4j instantiates it via its no-arg
 * constructor.
 */
public class RetryableDependencyPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof DependencyCallException dependencyFailure
                && dependencyFailure.isRetryable();
    }
}
