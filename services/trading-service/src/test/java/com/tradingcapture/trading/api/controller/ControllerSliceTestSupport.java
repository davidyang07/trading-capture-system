package com.tradingcapture.trading.api.controller;

import com.tradingcapture.observability.ObservabilitySupportAutoConfiguration;
import com.tradingcapture.trading.security.AccessContextFactory;
import com.tradingcapture.trading.security.KeycloakJwtAuthenticationConverter;
import com.tradingcapture.trading.security.ProblemDetailAccessDeniedHandler;
import com.tradingcapture.trading.security.ProblemDetailAuthenticationEntryPoint;
import com.tradingcapture.trading.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared setup for @WebMvcTest slices: imports the real security configuration (filter
 * chain, converter, problem-shaped 401/403) so the slice exercises production authorization
 * behavior. The JwtDecoder is mocked because {@code jwt()} post-processors inject the
 * authentication directly — token decoding is covered by the full integration tests.
 */
@Import({SecurityConfig.class,
        KeycloakJwtAuthenticationConverter.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class,
        AccessContextFactory.class,
        ObservabilitySupportAutoConfiguration.class})
public abstract class ControllerSliceTestSupport {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    private static final KeycloakJwtAuthenticationConverter CONVERTER = new KeycloakJwtAuthenticationConverter();

    /** Authentication post-processor running the production claim-to-authority mapping. */
    protected static RequestPostProcessor auth(Jwt jwt) {
        return jwt().jwt(jwt).authorities(CONVERTER.convert(jwt).getAuthorities());
    }
}
