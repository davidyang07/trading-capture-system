package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.client.AccountValidationClient.AccountValidation;
import com.tradingcapture.allocation.client.AccountValidationClient.Check;
import com.tradingcapture.observability.CorrelationId;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
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

class AccountValidationClientTest extends M2MClientTestSupport {

    private static final String PATH = "/internal/v1/accounts/batch";

    private AccountValidationClient client() {
        return new AccountValidationClient(restClient(), executor(), properties());
    }

    private List<Check> checks() {
        return List.of(new Check("ACC-1", "USD", new BigDecimal("100.0000"), "AAPL", new BigDecimal("60")));
    }

    @Test
    void resolvesPerItemVerdictsAcrossActiveInactiveInsufficientAndMissing() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("""
                { "results": [
                  { "accountId": "ACC-1", "found": true, "status": "ACTIVE", "active": true,
                    "ownerSubject": "investor1", "sufficientCash": true, "sufficientSecurities": true,
                    "reasons": [] },
                  { "accountId": "ACC-2", "found": true, "status": "SUSPENDED", "active": false,
                    "ownerSubject": "investor2", "sufficientCash": null, "sufficientSecurities": null,
                    "reasons": ["account not active"] },
                  { "accountId": "ACC-3", "found": true, "status": "ACTIVE", "active": true,
                    "ownerSubject": "investor3", "sufficientCash": false, "sufficientSecurities": false,
                    "reasons": ["insufficient cash", "insufficient securities"] },
                  { "accountId": "ACC-4", "found": false, "status": null, "active": false,
                    "ownerSubject": null, "sufficientCash": null, "sufficientSecurities": null,
                    "reasons": ["account not found"] }
                ] }
                """)));

        List<AccountValidation> results = client().validate(checks());

        assertThat(results).hasSize(4);
        assertThat(results.get(0).active()).isTrue();
        assertThat(results.get(0).sufficientCash()).isTrue();
        assertThat(results.get(1).status()).isEqualTo("SUSPENDED");
        assertThat(results.get(1).active()).isFalse();
        assertThat(results.get(2).sufficientCash()).isFalse();
        assertThat(results.get(2).reasons()).contains("insufficient cash");
        assertThat(results.get(3).found()).isFalse();
    }

    @Test
    void propagatesTheActiveCorrelationId() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{ \"results\": [] }")));
        MDC.put(CorrelationId.MDC_KEY, "cid-account-1");

        client().validate(checks());

        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader(CorrelationId.HTTP_HEADER, equalTo("cid-account-1")));
    }

    @Test
    void serverErrorIsRetried() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().validate(checks()))
                .satisfies(e -> assertThat(e.isRetryable()).isTrue());
        wireMock.verify(exactly(3), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void forbiddenIsUnauthorizedAndNotRetried() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(403)));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> client().validate(checks()))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.UNAUTHORIZED);
                    assertThat(e.isRetryable()).isFalse();
                });
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }
}
