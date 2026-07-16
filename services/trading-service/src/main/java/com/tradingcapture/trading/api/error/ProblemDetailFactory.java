package com.tradingcapture.trading.api.error;

import com.tradingcapture.observability.CorrelationId;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Single construction point for the service's RFC 7807 problem responses so the business
 * error handler and the security 401/403 writers emit exactly the same shape:
 * standard fields plus {@code errorCode}, {@code correlationId}, {@code timestamp}.
 */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    public static ProblemDetail build(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("correlationId", MDC.get(CorrelationId.MDC_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
