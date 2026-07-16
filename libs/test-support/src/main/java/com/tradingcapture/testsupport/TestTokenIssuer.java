package com.tradingcapture.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Issues RS256-signed JWTs from a per-JVM RSA key pair so full-stack integration tests can
 * exercise the real resource-server validation path (signature, issuer, audience, expiry)
 * without a running Keycloak. Tests expose {@link #publicKey()} to a locally built
 * {@code NimbusJwtDecoder} configured with the same validators as production.
 *
 * <p>Claim shapes intentionally mirror the Keycloak realm {@code tcs}: {@code scope} is a
 * space-separated string, realm roles live in {@code realm_access.roles}, and the user's
 * login name is {@code preferred_username}.</p>
 */
public final class TestTokenIssuer {

    /** Issuer written into test tokens and expected by test-configured decoders. */
    public static final String ISSUER = "https://test-issuer.tcs.local/realms/tcs";

    private static final RSAKey RSA_KEY = generateKey();

    private TestTokenIssuer() {
    }

    public static RSAPublicKey publicKey() {
        try {
            return RSA_KEY.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Test RSA key cannot be exported", e);
        }
    }

    /**
     * Issues a signed token with sane defaults (issuer, subject, iat, exp now+5m, jti) that the
     * customizer may override — including with invalid values for negative tests.
     */
    public static String issue(Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("tcs-test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate test RSA key", e);
        }
    }
}
