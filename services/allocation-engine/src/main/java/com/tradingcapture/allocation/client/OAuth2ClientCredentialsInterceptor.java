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
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.Set;

/**
 * Attaches a machine (client-credentials) bearer token to each outbound dependency request. Token
 * acquisition, caching, and refresh are delegated to the Spring Security
 * {@link OAuth2AuthorizedClientManager} (no custom static caching). The access token is never
 * logged. There is no user principal for a background client-credentials flow, so a stable
 * anonymous principal is used.
 */
public class OAuth2ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

    /** RFC 6749 §5.2 / §4.1.2.1 error codes that mean "try again later", not "you are not allowed". */
    private static final Set<String> TRANSIENT_OAUTH_ERRORS = Set.of("server_error", "temporarily_unavailable");

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
        OAuth2AuthorizedClient authorizedClient;
        try {
            authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        } catch (OAuth2AuthorizationException e) {
            String code = e.getError() == null ? "unknown" : e.getError().getErrorCode();
            throw new TokenAcquisitionException("token request for " + registrationId + " failed: " + code,
                    isTransient(e), e);
        }
        if (authorizedClient == null) {
            throw new TokenAcquisitionException(
                    "no client-credentials token for registration " + registrationId, false, null);
        }
        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }

    /**
     * The identity provider was unreachable, timed out, answered 5xx or 429, or said so itself
     * ({@code server_error}/{@code temporarily_unavailable}). Anything else (e.g. {@code invalid_client})
     * is a configuration problem that retrying cannot fix.
     */
    static boolean isTransient(OAuth2AuthorizationException e) {
        if (e.getError() != null && TRANSIENT_OAUTH_ERRORS.contains(e.getError().getErrorCode())) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof ResourceAccessException
                    || cause instanceof HttpServerErrorException) {
                return true;
            }
            if (cause instanceof HttpStatusCodeException status && status.getStatusCode().value() == 429) {
                return true;
            }
        }
        return false;
    }
}
