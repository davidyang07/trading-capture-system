package com.tradingcapture.enrichment.api.controller;

import com.tradingcapture.enrichment.security.SecurityConfig;
import com.tradingcapture.observability.ObservabilitySupportAutoConfiguration;
import com.tradingcapture.security.KeycloakJwtAuthenticationConverter;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared setup for @WebMvcTest slices: imports the real security configuration so the slice
 * exercises production authorization behavior. The JwtDecoder is mocked because {@code jwt()}
 * post-processors inject the authentication directly.
 */
@Import({SecurityConfig.class, ObservabilitySupportAutoConfiguration.class})
public abstract class ControllerSliceTestSupport {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    private static final KeycloakJwtAuthenticationConverter CONVERTER = new KeycloakJwtAuthenticationConverter();

    protected static RequestPostProcessor auth(Jwt jwt) {
        return jwt().jwt(jwt).authorities(CONVERTER.convert(jwt).getAuthorities());
    }
}
