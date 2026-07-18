package com.tradingcapture.allocation.client;

import com.tradingcapture.allocation.client.DependencyClientsProperties.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Wires the account/enrichment/trading M2M {@link RestClient}s (per-dependency base URL and
 * connect/read timeouts, correlation propagation, and a machine client-credentials bearer) plus the
 * Spring Security {@link OAuth2AuthorizedClientManager} that acquires and caches those tokens.
 * Resilience (retry + circuit breaker) is applied by {@link RemoteCallExecutor} using the
 * Resilience4j registries auto-configured from {@code resilience4j.*}.
 */
@Configuration
@EnableConfigurationProperties(DependencyClientsProperties.class)
public class ClientConfig {

    /**
     * Background (no user request) client-credentials manager: tokens are acquired, cached, and
     * refreshed by Spring Security, keyed by client registration id.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public RestClient accountRestClient(DependencyClientsProperties properties,
                                        OAuth2AuthorizedClientManager authorizedClientManager) {
        return build(properties.getAccount(), authorizedClientManager);
    }

    @Bean
    public RestClient enrichmentRestClient(DependencyClientsProperties properties,
                                           OAuth2AuthorizedClientManager authorizedClientManager) {
        return build(properties.getEnrichment(), authorizedClientManager);
    }

    @Bean
    public RestClient tradingRestClient(DependencyClientsProperties properties,
                                        OAuth2AuthorizedClientManager authorizedClientManager) {
        return build(properties.getTrading(), authorizedClientManager);
    }

    private static RestClient build(Client client, OAuth2AuthorizedClientManager authorizedClientManager) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) client.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) client.getReadTimeout().toMillis());
        return RestClient.builder()
                .baseUrl(client.getBaseUrl())
                .requestFactory(factory)
                .requestInterceptor(new CorrelationPropagationInterceptor())
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(
                        authorizedClientManager, client.getRegistrationId()))
                .build();
    }
}
