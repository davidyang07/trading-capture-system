package com.tradingcapture.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingcapture.observability.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponsesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void problemDetailsCarriesErrorCodeCorrelationIdAndTimestamp() {
        MDC.put(CorrelationId.MDC_KEY, "cid-777");
        ProblemDetail problem = ProblemDetails.build(HttpStatus.CONFLICT, "DUPLICATE_ORDER", "already exists");

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
        assertThat(problem.getProperties()).containsEntry("errorCode", "DUPLICATE_ORDER");
        assertThat(problem.getProperties()).containsEntry("correlationId", "cid-777");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void entryPointWritesA401ProblemWithBearerChallengeAndNoTokenInternals() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProblemAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest(), response, new StubAuthException("boom"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"UNAUTHORIZED\"");
        assertThat(response.getContentAsString()).doesNotContain("boom");
    }

    @Test
    void accessDeniedHandlerWritesA403Problem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProblemAccessDeniedHandler(objectMapper).handle(
                new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"FORBIDDEN\"");
    }

    private static final class StubAuthException extends AuthenticationException {
        StubAuthException(String message) {
            super(message);
        }
    }
}
