package com.tradingcapture.testsupport;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds decoded {@link Jwt} objects with Keycloak-shaped claims for MockMvc slice tests
 * (via {@code SecurityMockMvcRequestPostProcessors.jwt().jwt(...)}). Services supply their
 * own domain-specific factories on top of this mechanical builder.
 */
public final class TestJwts {

    private TestJwts() {
    }

    /**
     * @param subject           stable test subject (Keycloak would use a UUID)
     * @param preferredUsername Keycloak login name; {@code null} to omit the claim
     * @param realmRoles        contents of {@code realm_access.roles}
     * @param scope             space-separated scope string, e.g. {@code "order:read order:create"}
     * @param audience          {@code aud} values, e.g. the resource server's expected audience
     */
    public static Jwt jwt(String subject,
                          String preferredUsername,
                          List<String> realmRoles,
                          String scope,
                          List<String> audience) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(TestTokenIssuer.ISSUER)
                .subject(subject)
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .claim("realm_access", Map.of("roles", realmRoles));
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        return builder.build();
    }
}
