package com.tradingcapture.allocation.client;

import com.tradingcapture.observability.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propagates the active correlation id (set on the MDC by the inbound Kafka processing) to every
 * outbound dependency request as the shared {@code X-Correlation-ID} header. Only the trusted MDC
 * value is forwarded — never an arbitrary caller-supplied header — and nothing is generated here;
 * a missing correlation id simply means no header (the inbound pipeline is responsible for minting one).
 */
public class CorrelationPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().set(CorrelationId.HTTP_HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
