package com.tradingcapture.trading.testsupport;

import com.tradingcapture.testsupport.TestTokenIssuer;
import com.tradingcapture.trading.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Full-stack test decoder: verifies signatures against the in-JVM test RSA key while
 * reusing the production validator chain (issuer + audience) verbatim, so integration
 * tests exercise real 401 behavior for bad issuer/audience/expiry. Activated together
 * with {@code tcs.security.jwt-mode=local-keys}, which disables the OIDC decoder bean.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public JwtDecoder testJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokenIssuer.publicKey()).build();
        decoder.setJwtValidator(SecurityConfig.tokenValidator(TestTokenIssuer.ISSUER, TradingTestTokens.AUDIENCE));
        return decoder;
    }
}
