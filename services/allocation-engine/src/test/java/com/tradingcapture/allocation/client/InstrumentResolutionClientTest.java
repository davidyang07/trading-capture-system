package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.client.InstrumentResolutionClient.InstrumentResolution;
import com.tradingcapture.allocation.client.InstrumentResolutionClient.Key;
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

class InstrumentResolutionClientTest extends M2MClientTestSupport {

    private static final String PATH = "/internal/v1/instruments/lookup";

    private InstrumentResolutionClient client() {
        return new InstrumentResolutionClient(restClient(), executor(), properties());
    }

    private List<Key> keys() {
        return List.of(new Key("EXCHANGE_SYMBOL", "AAPL", "XNAS"));
    }

    @Test
    void resolvesFoundAmbiguousAndNotFoundPerItem() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("""
                { "results": [
                  { "source": "EXCHANGE_SYMBOL", "value": "AAPL", "exchangeMic": "XNAS",
                    "found": true, "ambiguous": false,
                    "instrument": { "instrumentId": "AAPL", "name": "Apple Inc.",
                      "instrumentType": "EQUITY", "exchangeMic": "XNAS", "currencyCode": "USD",
                      "status": "ACTIVE", "createdAt": "2026-07-01T00:00:00Z",
                      "updatedAt": "2026-07-01T00:00:00Z", "version": 1 } },
                  { "source": "EXCHANGE_SYMBOL", "value": "XYZ", "exchangeMic": null,
                    "found": false, "ambiguous": true, "instrument": null },
                  { "source": "CUSIP", "value": "NONE", "exchangeMic": null,
                    "found": false, "ambiguous": false, "instrument": null }
                ] }
                """)));

        List<InstrumentResolution> results = client().resolve(keys());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).found()).isTrue();
        assertThat(results.get(0).instrument().instrumentId()).isEqualTo("AAPL");
        assertThat(results.get(0).instrument().currencyCode()).isEqualTo("USD");
        assertThat(results.get(0).instrument().status()).isEqualTo("ACTIVE");
        assertThat(results.get(1).ambiguous()).isTrue();
        assertThat(results.get(1).instrument()).isNull();
        assertThat(results.get(2).found()).isFalse();
    }

    @Test
    void propagatesTheActiveCorrelationId() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{ \"results\": [] }")));
        MDC.put(CorrelationId.MDC_KEY, "cid-enrichment-1");

        client().resolve(keys());

        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader(CorrelationId.HTTP_HEADER, equalTo("cid-enrichment-1")));
    }

    @Test
    void serviceUnavailableIsRetried() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().resolve(keys()))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                });
        wireMock.verify(exactly(3), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void badRequestIsNotRetried() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().resolve(keys()))
                .satisfies(e -> assertThat(e.isRetryable()).isFalse());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }
}
