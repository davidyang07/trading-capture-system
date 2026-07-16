package com.tradingcapture.account.security;

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
 * OAuth2 resource-server configuration (Spring Security 6, ADR-0008). The mechanical beans
 * (Keycloak claim mapping, issuer+audience decoder, problem-shaped 401/403) come from the
 * shared {@link ResourceServerSecuritySupport}; this class only declares the service's own
 * path/scope rules.
 *
 * <p>Public paths are exactly actuator health/info (probes) and the springdoc documentation
 * UI. The machine namespace {@code /internal/**} requires a dedicated service scope — either
 * {@code account:validate} (trading-service) or {@code allocation:process} (allocation-engine).
 * That scope is granted only to the machine clients, never to the admin-web user client, so an
 * investor's user token (which carries {@code account:balance}) cannot reach the cross-owner
 * batch endpoint. Everything else needs a verified JWT; fine-grained scope/role rules live on
 * the controllers, ownership rules in the services.</p>
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
                                "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/internal/**")
                                .hasAnyAuthority("SCOPE_account:validate", "SCOPE_allocation:process")
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
