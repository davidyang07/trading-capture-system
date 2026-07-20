package com.tradingcapture.trading.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource-server configuration (Spring Security 6, ADR-0008).
 *
 * <p>Public paths are exactly: actuator health/info (probes), the actuator Prometheus
 * scrape endpoint (metrics only, ADR-0015), and the springdoc documentation UI — a documented
 * development aid that exposes only the API contract.
 * The machine namespace {@code /internal/**} requires the {@code allocation:process}
 * scope at the chain level in addition to method-level rules. Everything else needs a
 * verified JWT; fine-grained scope/role rules live on the controllers, ownership rules
 * in the services.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   KeycloakJwtAuthenticationConverter authenticationConverter,
                                                   ProblemDetailAuthenticationEntryPoint entryPoint,
                                                   ProblemDetailAccessDeniedHandler deniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/internal/**").hasAuthority("SCOPE_allocation:process")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /**
     * Production decoder: JWKS from the issuer, validating signature, expiry, issuer and
     * audience. Wrapped in a supplier so OIDC discovery happens on first use, not at boot
     * (the service must start without Keycloak). Tests switch {@code tcs.security.jwt-mode}
     * to {@code local-keys} and provide a decoder with the same validators over a test key.
     */
    @Bean
    @ConditionalOnProperty(name = "tcs.security.jwt-mode", havingValue = "oidc", matchIfMissing = true)
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${tcs.security.audience}") String audience) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            decoder.setJwtValidator(tokenValidator(issuer, audience));
            return decoder;
        });
    }

    /** Issuer + audience validation, shared verbatim by the test decoder configuration. */
    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
    }
}
