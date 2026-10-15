package com.tradingcapture.observability;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesIncomingCorrelationIdAndClearsMdcAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HTTP_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        mdcDuringRequest.set(MDC.get(CorrelationId.MDC_KEY));
                    }
                });

        assertThat(mdcDuringRequest.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissingOrBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String echoed = response.getHeader(CorrelationId.HTTP_HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\r\nX-Injected: yes", "fake\n2026-01-01 ERROR forged log line", "has space", "a/b", "<script>"})
    void replacesAnUnsafeCorrelationId(String unsafe) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HTTP_HEADER, unsafe);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesACorrelationIdLongerThanItsColumn() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HTTP_HEADER, "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).hasSize(36);
        assertThat(CorrelationId.isSafe("a".repeat(64))).isTrue();
    }
}
