package com.tradingcapture.allocation.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.io.IOException;

/**
 * Attaches a machine (client-credentials) bearer token to each outbound dependency request. Token
 * acquisition, caching, and refresh are delegated to the Spring Security
 * {@link OAuth2AuthorizedClientManager} (no custom static caching). The access token is never
 * logged. There is no user principal for a background client-credentials flow, so a stable
 * anonymous principal is used.
 */
public class OAuth2ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

    private static final Authentication PRINCIPAL = new AnonymousAuthenticationToken(
            "allocation-engine-m2m", "allocation-engine-m2m",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public OAuth2ClientCredentialsInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
                                              String registrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(PRINCIPAL)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new IllegalStateException(
                    "Could not obtain a client-credentials token for registration " + registrationId);
        }
        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
