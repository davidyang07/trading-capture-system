package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.client.AccountValidationClient.Check;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * A failure to obtain the machine token is classified like the dependency call itself: an identity
 * provider outage is retried (UNAVAILABLE), a rejected client is not (UNAUTHORIZED). Before, both
 * surfaced as UNEXPECTED, so a short Keycloak outage failed allocations terminally.
 */
class TokenAcquisitionFailureTest extends M2MClientTestSupport {

    private static final String TOKEN = "/token";
    private static final String ACCOUNTS = "/internal/v1/accounts/batch";

    private AccountValidationClient clientWithRealTokenFlow() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("m2m")
                .clientId("allocation-engine")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(baseUrl() + TOKEN)
                .build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,
                new InMemoryOAuth2AuthorizedClientService(registrations));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(10_000);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(factory)
                .requestInterceptor(new CorrelationPropagationInterceptor())
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(manager, "m2m"))
                .build();
        return new AccountValidationClient(restClient, executor(), properties());
    }

    private static List<Check> checks() {
        return List.of(new Check("ACC-1", "USD", new BigDecimal("100.0000"), null, null));
    }

    @Test
    void anIdentityProviderOutageIsRetryable() {
        wireMock.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withStatus(503).withBody("down")));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> clientWithRealTokenFlow().validate(checks()))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                });
        // Retried within the call, and the dependency itself was never reached without a token.
        wireMock.verify(exactly(3), postRequestedFor(urlEqualTo(TOKEN)));
        wireMock.verify(exactly(0), postRequestedFor(urlEqualTo(ACCOUNTS)));
    }

    @Test
    void anIdentityProviderSayingTemporarilyUnavailableIsRetryable() {
        wireMock.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"error\": \"temporarily_unavailable\" }")));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> clientWithRealTokenFlow().validate(checks()))
                .satisfies(e -> assertThat(e.type()).isEqualTo(DependencyFailureType.UNAVAILABLE));
    }

    @Test
    void aRejectedClientIsUnauthorizedAndNotRetried() {
        wireMock.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"error\": \"invalid_client\" }")));

        assertThatExceptionOfType(DependencyCallException.class)
                .isThrownBy(() -> clientWithRealTokenFlow().validate(checks()))
                .satisfies(e -> {
                    assertThat(e.type()).isEqualTo(DependencyFailureType.UNAUTHORIZED);
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("token request for m2m failed").doesNotContain("secret");
                });
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(TOKEN)));
    }
}
