package com.tradingcapture.trading.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingcapture.trading.api.error.ProblemDetailFactory;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Standardized 403: the token is valid but lacks a required scope or role. Ownership
 * denials never reach this handler — they are masked as 404 in the service layer.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ProblemDetailFactory.build(
                HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "The token does not carry the scope or role required for this operation"));
    }
}
