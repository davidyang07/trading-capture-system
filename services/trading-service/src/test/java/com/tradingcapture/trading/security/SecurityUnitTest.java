package com.tradingcapture.trading.security;

import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.testsupport.TradingTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Converter, audience validator, and access-context derivation rules. */
class SecurityUnitTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();
    private final AccessContextFactory accessContextFactory = new AccessContextFactory();

    @Test
    void converterMapsScopesAndRealmRoles() {
        AbstractAuthenticationToken token = converter.convert(TradingTestTokens.adminJwt());

        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).contains("SCOPE_order:create", "SCOPE_order:read",
                "SCOPE_execution:write", "ROLE_ADMIN");
    }

    @Test
    void converterHandlesTokensWithoutRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("svc")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "allocation:process")
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactly("SCOPE_allocation:process");
    }

    @Test
    void audienceValidatorAcceptsMatchingAudience() {
        AudienceValidator validator = new AudienceValidator("trading-service");

        OAuth2TokenValidatorResult result = validator.validate(TradingTestTokens.adminJwt());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void audienceValidatorRejectsMissingAudience() {
        AudienceValidator validator = new AudienceValidator("trading-service");
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("x").audience(List.of("other-service"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void accessContextPrefersPreferredUsernameOverSubject() {
        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(TradingTestTokens.investorJwt("investor1"));

        AccessContext ctx = accessContextFactory.from(authentication);

        assertThat(ctx.subject()).isEqualTo("investor1");
        assertThat(ctx.admin()).isFalse();
        assertThat(ctx.machineRead()).isFalse();
        assertThat(ctx.canAccessAllOrders()).isFalse();
    }

    @Test
    void accessContextFallsBackToSubjectWhenNoPreferredUsername() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("raw-subject-uuid")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "order:read")
                .build();
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        AccessContext ctx = accessContextFactory.from(authentication);

        assertThat(ctx.subject()).isEqualTo("raw-subject-uuid");
    }

    @Test
    void accessContextFlagsAdminAndMachine() {
        AccessContext admin = accessContextFactory.from(
                (JwtAuthenticationToken) converter.convert(TradingTestTokens.adminJwt()));
        AccessContext machine = accessContextFactory.from(
                (JwtAuthenticationToken) converter.convert(TradingTestTokens.machineJwt()));

        assertThat(admin.admin()).isTrue();
        assertThat(admin.canAccessAllOrders()).isTrue();
        assertThat(machine.admin()).isFalse();
        assertThat(machine.machineRead()).isTrue();
        assertThat(machine.canAccessAllOrders()).isTrue();
    }

    @Test
    void accessContextRejectsNonJwtAuthentications() {
        assertThatThrownBy(() -> accessContextFactory.from(
                new UsernamePasswordAuthenticationToken("user", "password")))
                .isInstanceOf(IllegalStateException.class);
    }
}
