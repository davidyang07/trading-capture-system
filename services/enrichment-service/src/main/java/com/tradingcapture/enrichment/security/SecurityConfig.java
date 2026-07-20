package com.tradingcapture.enrichment.security;

import com.tradingcapture.security.KeycloakJwtAuthenticationConverter;
import com.tradingcapture.security.ProblemAccessDeniedHandler;
import com.tradingcapture.security.ProblemAuthenticationEntryPoint;
import com.tradingcapture.security.ResourceServerSecuritySupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource-server configuration (Spring Security 6, ADR-0008). Mechanical beans come from
 * the shared {@link ResourceServerSecuritySupport}; this class declares the service's path/scope
 * rules only. Read endpoints require {@code enrichment:read}; administrative mutations require
 * {@code enrichment:admin} (enforced per controller). The machine namespace {@code /internal/**}
 * requires {@code enrichment:read} or {@code allocation:process}.
 *
 * <p>Public paths: actuator health/info, the Prometheus scrape endpoint (metrics only,
 * ADR-0015), and the springdoc documentation UI.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/internal/**")
                                .hasAnyAuthority("SCOPE_enrichment:read", "SCOPE_allocation:process")
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
