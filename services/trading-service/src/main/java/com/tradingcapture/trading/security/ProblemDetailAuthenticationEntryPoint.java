package com.tradingcapture.trading.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingcapture.trading.api.error.ProblemDetailFactory;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Standardized 401: missing/invalid/expired token. Emits the same problem shape as the
 * business error handler; the failure reason is deliberately generic (no token internals).
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer");
        objectMapper.writeValue(response.getWriter(), ProblemDetailFactory.build(
                HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Authentication is required: the bearer token is missing or invalid"));
    }
}
