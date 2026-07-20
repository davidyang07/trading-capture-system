package com.tradingcapture.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsScopesToScopeAuthoritiesAndRealmRolesToRoleAuthorities() {
        Jwt jwt = base()
                .claim("scope", "order:read execution:write")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "INVESTOR")))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorities(token))
                .contains("SCOPE_order:read", "SCOPE_execution:write", "ROLE_ADMIN", "ROLE_INVESTOR");
    }

    @Test
    void yieldsNoRolesWhenRealmAccessIsMissing() {
        Jwt jwt = base().claim("scope", "order:read").build();
        assertThat(authorities(converter.convert(jwt)))
                .contains("SCOPE_order:read")
                .noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    void yieldsNoRolesWhenRealmAccessRolesIsNotACollection() {
        Jwt jwt = base()
                .claim("scope", "order:read")
                .claim("realm_access", Map.of("roles", "ADMIN")) // not a collection
                .build();
        assertThat(authorities(converter.convert(jwt))).noneMatch(a -> a.startsWith("ROLE_"));
    }

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "none")
                .subject("investor1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private static List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    }
}
