package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 correlation proof (ADR-0015). The correlation id that entered the pipeline on the
 * inbound Kafka {@code x-correlation-id} header (persisted on {@code raw_messages}) must ride
 * every downstream dependency call the orchestrator makes, as the shared {@code X-Correlation-ID}
 * HTTP header — so a single id ties the HTTP request boundary, the log MDC, and (in
 * {@code IngestionPipelineIntegrationTest}) the outbound Kafka event header together.
 *
 * <p>This exercises the real production wiring: {@link AllocationOrchestrator} resolves the stored
 * correlation id, puts it on the MDC, and {@code CorrelationPropagationInterceptor} copies the MDC
 * value onto every outbound account/enrichment/trading request. WireMock verifies the header
 * actually arrived — no sleeps, deterministic.</p>
 */
class CorrelationPropagationIntegrationTest extends OrchestrationIntegrationTestSupport {

    @Test
    void correlationIdRidesEveryDownstreamDependencyCall() {
        String allocId = "A-CORR-1";
        String qty = "100.0000";
        // persistAllocation stores raw_messages.correlation_id = "cid-" + allocId (the id the
        // inbound Kafka header would have carried in production).
        String expectedCorrelationId = "cid-" + allocId;

        Long id = persistAllocation(allocId, AllocTransType.NEW, null, "AAPL", "1", qty,
                List.of(new AccountQty("ACC-1", qty)), List.of("E1"));
        stubAccountsActive("ACC-1");
        stubInstrumentActive("AAPL");
        stubExecutionEligible("E1", "exec_E1", qty, "2026-07-17T10:00:00Z");
        stubAllocationStateOk();

        driver.runOnce();

        // The happy path drove the allocation to completion...
        assertThat(reload(id).getStatus()).isEqualTo(AllocationStatus.COMPLETED);

        // ...and every downstream HTTP dependency saw the same correlation id.
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/accounts/batch"))
                .withHeader("X-Correlation-ID", equalTo(expectedCorrelationId)));
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/instruments/lookup"))
                .withHeader("X-Correlation-ID", equalTo(expectedCorrelationId)));
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/executions/resolve"))
                .withHeader("X-Correlation-ID", equalTo(expectedCorrelationId)));
        wireMock.verify(putRequestedFor(urlEqualTo("/internal/v1/executions/allocation-state"))
                .withHeader("X-Correlation-ID", equalTo(expectedCorrelationId)));
    }
}
