package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.client.TradingExecutionClient.ExecutionResolution;
import com.tradingcapture.observability.CorrelationId;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TradingExecutionClientTest extends M2MClientTestSupport {

    private static final String PATH = "/internal/v1/executions/resolve";

    private TradingExecutionClient client() {
        return new TradingExecutionClient(restClient(), executor(), properties());
    }

    @Test
    void resolvesBatchMappingExternalToInternalWithPerItemVerdicts() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("""
                { "results": [
                  { "execId": "E1", "found": true, "internalId": "exec_1", "orderId": "ORD-1",
                    "instrumentId": "AAPL", "accountId": "ACC-1", "side": "1",
                    "executedQuantity": 60.0000, "allocatableQuantity": 60.0000, "currency": "USD",
                    "tradeDate": "2026-07-17", "settleType": "0", "settleDate": "2026-07-20",
                    "transactTime": "2026-07-17T14:00:00Z", "allocationState": "UNALLOCATED",
                    "allocationId": null, "eligible": true, "reason": "ELIGIBLE" },
                  { "execId": "E2", "found": false, "eligible": false, "reason": "NOT_FOUND" },
                  { "execId": "E3", "found": true, "internalId": "exec_3", "eligible": false,
                    "reason": "ALREADY_ALLOCATED", "allocationId": "ALLOC-9",
                    "allocatableQuantity": 0 }
                ] }
                """)));

        List<ExecutionResolution> results = client().resolveExecutions(List.of("E1", "E2", "E3"));

        assertThat(results).hasSize(3);
        ExecutionResolution eligible = results.get(0);
        assertThat(eligible.internalId()).isEqualTo("exec_1");
        assertThat(eligible.instrumentId()).isEqualTo("AAPL");
        assertThat(eligible.side()).isEqualTo("1");
        assertThat(eligible.allocatableQuantity()).isEqualByComparingTo("60.0000");
        assertThat(eligible.eligible()).isTrue();
        assertThat(eligible.reason()).isEqualTo("ELIGIBLE");
        assertThat(results.get(1).found()).isFalse();
        assertThat(results.get(1).reason()).isEqualTo("NOT_FOUND");
        assertThat(results.get(2).reason()).isEqualTo("ALREADY_ALLOCATED");
        assertThat(results.get(2).allocationId()).isEqualTo("ALLOC-9");
    }

    @Test
    void propagatesTheActiveCorrelationId() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{ \"results\": [] }")));
        MDC.put(CorrelationId.MDC_KEY, "cid-trading-1");

        client().resolveExecutions(List.of("E1"));

        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader(CorrelationId.HTTP_HEADER, equalTo("cid-trading-1")));
    }

    @Test
    void serverErrorIsRetriedThenSurfacesAsRetryableFailure() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().resolveExecutions(List.of("E1")))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.SERVER_ERROR);
                    assertThat(e.isRetryable()).isTrue();
                });
        wireMock.verify(exactly(3), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void badRequestIsNotRetriedAndIsTerminal() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().resolveExecutions(List.of("E1")))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.BAD_REQUEST);
                    assertThat(e.isRetryable()).isFalse();
                });
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void readTimeoutIsClassifiedAsRetryableTimeout() {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson("{ \"results\": [] }").withFixedDelay(400)));
        TradingExecutionClient slow = new TradingExecutionClient(restClient(100), executor(), properties());

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> slow.resolveExecutions(List.of("E1")))
                .satisfies(e -> assertThat(e.type()).isEqualTo(DependencyFailureType.TIMEOUT));
    }

    @Test
    void malformedResponseIsTerminal() {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson("this is not json")));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().resolveExecutions(List.of("E1")))
                .satisfies(e -> assertThat(e.type()).isEqualTo(DependencyFailureType.MALFORMED_RESPONSE));
    }
}
