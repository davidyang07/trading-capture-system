package com.tradingcapture.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("trading-service");

    @Test
    void succeedsWhenTheRequiredAudienceIsPresent() {
        Jwt jwt = jwtWithAudience(List.of("account-service", "trading-service"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void failsWhenTheRequiredAudienceIsAbsent() {
        Jwt jwt = jwtWithAudience(List.of("account-service"));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(e ->
                assertThat(e.getDescription()).contains("trading-service"));
    }

    @Test
    void failsWhenTheAudienceClaimIsMissing() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject("investor1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "order:read")
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private static Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("t").header("alg", "none")
                .subject("investor1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("aud", audience)
                .claim("realm_access", Map.of("roles", List.of("INVESTOR")))
                .build();
    }
}
