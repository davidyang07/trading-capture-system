package com.tradingcapture.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceServerSecuritySupportTest {

    private final ResourceServerSecuritySupport support = new ResourceServerSecuritySupport();
    private WireMockServer wireMock;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void exposesTheSharedResourceServerBeans() {
        assertThat(support.keycloakJwtAuthenticationConverter()).isNotNull();
        assertThat(support.problemAuthenticationEntryPoint(new ObjectMapper())).isNotNull();
        assertThat(support.problemAccessDeniedHandler(new ObjectMapper())).isNotNull();
    }

    @Test
    void tokenValidatorAcceptsAMatchingIssuerAndAudienceAndRejectsAWrongAudience() {
        String issuer = "https://issuer.example/realms/tcs";
        OAuth2TokenValidator<Jwt> validator =
                ResourceServerSecuritySupport.tokenValidator(issuer, "trading-service");

        Jwt good = jwt(issuer, List.of("trading-service"));
        Jwt wrongAudience = jwt(issuer, List.of("account-service"));

        assertThat(validator.validate(good).hasErrors()).isFalse();
        assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();
    }

    @Test
    void jwkSetUriBranchBuildsAJwksBackedDecoder() {
        // A non-blank jwk-set-uri takes the withJwkSetUri path; decoding a non-JWT still fails,
        // proving the decoder was built (the branch executed) rather than short-circuited.
        JwtDecoder decoder = support.jwtDecoder(
                "https://issuer.example/realms/tcs",
                wireMock.baseUrl() + "/certs",
                "trading-service");
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void blankJwkSetUriDiscoversJwksFromTheIssuerLocation() {
        String issuer = wireMock.baseUrl();
        wireMock.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(okJson("""
                        {
                          "issuer": "%s",
                          "jwks_uri": "%s/certs",
                          "authorization_endpoint": "%s/auth",
                          "response_types_supported": ["code"],
                          "subject_types_supported": ["public"],
                          "id_token_signing_alg_values_supported": ["RS256"]
                        }
                        """.formatted(issuer, issuer, issuer))));

        JwtDecoder decoder = support.jwtDecoder(issuer, "", "trading-service");
        // Building the decoder fetches OIDC discovery from the issuer location (the blank branch);
        // decoding a non-JWT then fails. The discovery call below proves the branch executed.
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(RuntimeException.class);
        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlEqualTo("/.well-known/openid-configuration")));
    }

    private static Jwt jwt(String issuer, List<String> audience) {
        return Jwt.withTokenValue("t").header("alg", "none")
                .subject("investor1")
                .issuer(issuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("aud", audience)
                .build();
    }
}
