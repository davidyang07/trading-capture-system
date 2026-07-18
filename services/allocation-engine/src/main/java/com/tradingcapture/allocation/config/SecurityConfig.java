package com.tradingcapture.allocation.config;

import com.tradingcapture.security.KeycloakJwtAuthenticationConverter;
import com.tradingcapture.security.ProblemAccessDeniedHandler;
import com.tradingcapture.security.ProblemAuthenticationEntryPoint;
import com.tradingcapture.security.ResourceServerSecuritySupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource-server configuration (ADR-0008) via the shared
 * {@link ResourceServerSecuritySupport} (issuer + audience {@code allocation-engine}
 * validation, problem-shaped 401/403). The allocation-engine remains Kafka-driven and exposes
 * NO inbound business HTTP endpoints (Phase 8 orchestration calls out to trading/account/enrichment
 * as a machine client), so only actuator health/info is public and everything else requires a valid
 * token; any future inbound endpoint would declare its scope here.
 */
@Configuration
@EnableWebSecurity
@Import(ResourceServerSecuritySupport.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   KeycloakJwtAuthenticationConverter authenticationConverter,
                                                   ProblemAuthenticationEntryPoint entryPoint,
                                                   ProblemAccessDeniedHandler deniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
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
}
