package com.tradingcapture.trading.testsupport;

import com.tradingcapture.testsupport.TestJwts;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Trading-service-specific token fixtures on top of the shared test-support mechanics.
 * Claim shapes mirror the Keycloak dev realm: user tokens carry every trading scope (the
 * admin-web client's default scopes) — role and ownership rules do the differentiation,
 * exactly like production.
 */
public final class TradingTestTokens {

    public static final String AUDIENCE = "trading-service";
    public static final String USER_SCOPES =
            "order:create order:read order:update order:delete execution:read execution:write";
    public static final String MACHINE_SCOPES =
            "allocation:process order:read execution:read execution:write";

    private TradingTestTokens() {
    }

    // --- decoded Jwt objects for @WebMvcTest (SecurityMockMvcRequestPostProcessors.jwt()) ---

    public static Jwt adminJwt() {
        return TestJwts.jwt("sub-tcs-admin", "tcs-admin",
                List.of("ADMIN"), USER_SCOPES, List.of(AUDIENCE));
    }

    public static Jwt investorJwt(String username) {
        return TestJwts.jwt("sub-" + username, username,
                List.of("INVESTOR"), USER_SCOPES, List.of(AUDIENCE));
    }

    public static Jwt machineJwt() {
        return TestJwts.jwt("sub-allocation-engine",
                "service-account-allocation-engine", List.of(), MACHINE_SCOPES, List.of(AUDIENCE));
    }

    // --- encoded bearer tokens for full-stack tests (validated by the real decoder path) ---

    public static String adminBearer() {
        return bearer("tcs-admin", List.of("ADMIN"), USER_SCOPES, List.of(AUDIENCE));
    }

    public static String investorBearer(String username) {
        return bearer(username, List.of("INVESTOR"), USER_SCOPES, List.of(AUDIENCE));
    }

    public static String machineBearer() {
        return bearer("service-account-allocation-engine", List.of(), MACHINE_SCOPES, List.of(AUDIENCE));
    }

    public static String bearerWithoutAudience() {
        return bearer("tcs-admin", List.of("ADMIN"), USER_SCOPES, List.of("other-service"));
    }

    public static String expiredAdminBearer() {
        return TestTokenIssuer.issue(claims -> claims
                .claim("preferred_username", "tcs-admin")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .claim("scope", USER_SCOPES)
                .audience(List.of(AUDIENCE))
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(300))));
    }

    private static String bearer(String username, List<String> roles, String scopes, List<String> audience) {
        return TestTokenIssuer.issue(claims -> claims
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", roles))
                .claim("scope", scopes)
                .audience(audience));
    }
}
