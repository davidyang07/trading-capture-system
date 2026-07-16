package com.tradingcapture.enrichment.testsupport;

import com.tradingcapture.testsupport.TestJwts;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Enrichment-service token fixtures. Reference data has no per-owner rules, so authorization is
 * purely by scope: {@code enrichment:read} for lookups, {@code enrichment:admin} for mutations.
 */
public final class EnrichmentTestTokens {

    public static final String AUDIENCE = "enrichment-service";
    public static final String ADMIN_SCOPES = "enrichment:read enrichment:admin";
    public static final String READ_SCOPES = "enrichment:read";
    /** allocation-engine machine client. */
    public static final String ALLOCATION_SCOPES = "allocation:process enrichment:read";

    private EnrichmentTestTokens() {
    }

    // --- decoded Jwt objects for @WebMvcTest ---

    public static Jwt adminJwt() {
        return TestJwts.jwt("sub-tcs-admin", "tcs-admin", List.of("ADMIN"), ADMIN_SCOPES, List.of(AUDIENCE));
    }

    public static Jwt readerJwt() {
        return TestJwts.jwt("sub-reader", "investor1", List.of("INVESTOR"), READ_SCOPES, List.of(AUDIENCE));
    }

    public static Jwt allocationMachineJwt() {
        return TestJwts.jwt("sub-allocation", "service-account-allocation-engine", List.of(), ALLOCATION_SCOPES, List.of(AUDIENCE));
    }

    public static Jwt scopedJwt(String scopes) {
        return TestJwts.jwt("sub-scoped", "tcs-admin", List.of("ADMIN"), scopes, List.of(AUDIENCE));
    }

    // --- encoded bearer tokens for full-stack tests ---

    public static String adminBearer() {
        return bearer("tcs-admin", List.of("ADMIN"), ADMIN_SCOPES, List.of(AUDIENCE));
    }

    public static String readerBearer() {
        return bearer("investor1", List.of("INVESTOR"), READ_SCOPES, List.of(AUDIENCE));
    }

    public static String allocationMachineBearer() {
        return bearer("service-account-allocation-engine", List.of(), ALLOCATION_SCOPES, List.of(AUDIENCE));
    }

    public static String bearerWithoutAudience() {
        return bearer("tcs-admin", List.of("ADMIN"), ADMIN_SCOPES, List.of("other-service"));
    }

    public static String expiredReaderBearer() {
        return TestTokenIssuer.issue(claims -> claims
                .claim("preferred_username", "investor1")
                .claim("realm_access", Map.of("roles", List.of("INVESTOR")))
                .claim("scope", READ_SCOPES)
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
