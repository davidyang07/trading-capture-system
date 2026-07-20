package com.tradingcapture.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Shared resource-server beans (ADR-0008), imported by each service's {@code SecurityConfig}.
 * The service keeps its own {@link org.springframework.security.web.SecurityFilterChain}
 * because path/scope rules (which paths are public, which {@code /internal/**} scope) differ
 * per service; the mechanical parts — Keycloak claim mapping, issuer+audience validation, and
 * the problem-shaped 401/403 writers — live here so no two services copy them.
 */
@Configuration
public class ResourceServerSecuritySupport {

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    public ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public ProblemAccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ProblemAccessDeniedHandler(objectMapper);
    }

    /**
     * Production decoder: JWKS-backed, validating signature, expiry, issuer and audience.
     * Wrapped in a supplier so JWKS/OIDC discovery happens on first use, not at boot (the
     * service must start without Keycloak). Tests switch {@code tcs.security.jwt-mode} to
     * {@code local-keys} and supply a decoder with the same validators over a test key.
     *
     * <p>By default the JWKS location is discovered from the issuer. When Keycloak's
     * browser-facing (frontchannel) URL differs from the URL the service can reach it at
     * (backchannel) — e.g. the containerized full stack, where tokens carry
     * {@code iss=http://localhost:8180/...} but the service must fetch keys from
     * {@code http://keycloak:8180/...} — set {@code tcs.security.jwk-set-uri} to the backchannel
     * JWKS URL. A TCS-owned property (not {@code spring.security.oauth2.resourceserver.jwt.*}) is
     * used deliberately so Spring Boot's own JWKS auto-configuration never activates a competing
     * decoder. The {@code iss} claim is still validated against the configured issuer either way.
     */
    @Bean
    @ConditionalOnProperty(name = "tcs.security.jwt-mode", havingValue = "oidc", matchIfMissing = true)
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${tcs.security.jwk-set-uri:}") String jwkSetUri,
            @Value("${tcs.security.audience}") String audience) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = (jwkSetUri == null || jwkSetUri.isBlank())
                    ? NimbusJwtDecoder.withIssuerLocation(issuer).build()
                    : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            decoder.setJwtValidator(tokenValidator(issuer, audience));
            return decoder;
        });
    }

    /** Issuer + audience validation, shared verbatim by the test decoder configuration. */
    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
    }
}
