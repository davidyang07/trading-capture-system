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
 * validation, problem-shaped 401/403). Phase 7 exposes NO business endpoints — the service
 * is Kafka-driven — so only actuator health/info is public and everything else requires a
 * valid token; Phase 8+ endpoints will declare their scopes here.
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
