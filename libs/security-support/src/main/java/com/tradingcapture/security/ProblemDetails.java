package com.tradingcapture.security;

import com.tradingcapture.observability.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Single construction point for the platform's RFC 7807 problem responses. Every service's
 * business error handler and the shared security 401/403 writers emit exactly the same shape:
 * the standard fields plus {@code errorCode}, {@code correlationId}, and {@code timestamp}.
 *
 * <p>The {@code errorCode} is passed as a plain string so each service can keep its own typed
 * {@code ErrorCode} enum and hand its {@code name()} here without this library depending on
 * any service's domain.</p>
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("correlationId", MDC.get(CorrelationId.MDC_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
