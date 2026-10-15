package com.tradingcapture.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wire format the OpenAPI contract promises (ADR-0014): decimals are JSON strings.
 * Jackson's default emits BigDecimal as a bare number, which silently truncates any value wider
 * than a double before a consumer can inspect it — so this asserts the serialized text directly
 * rather than a round-trip, which would hide the loss.
 */
class JsonConfigTest {

    private final ObjectMapper mapper = mapperWithCustomizer();

    private static ObjectMapper mapperWithCustomizer() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JsonConfig().decimalsAsStrings().customize(builder);
        return builder.build();
    }

    @Test
    void writesBigDecimalAsAQuotedStringKeepingTrailingZeros() throws Exception {
        record Payload(BigDecimal quantity) { }

        assertThat(mapper.writeValueAsString(new Payload(new BigDecimal("100.00"))))
                .isEqualTo("{\"quantity\":\"100.00\"}");
    }

    @Test
    void preservesPrecisionWiderThanADouble() throws Exception {
        record Payload(BigDecimal notional) { }

        String exact = "12345678901234567.89";
        assertThat(mapper.writeValueAsString(new Payload(new BigDecimal(exact))))
                .isEqualTo("{\"notional\":\"" + exact + "\"}");
    }

    @Test
    void neverUsesExponentNotation() throws Exception {
        record Payload(BigDecimal small, BigDecimal zero, BigDecimal stripped) { }

        assertThat(mapper.writeValueAsString(new Payload(new BigDecimal("0.00000001"),
                new BigDecimal("0E-8"), new BigDecimal("1E+3"))))
                .isEqualTo("{\"small\":\"0.00000001\",\"zero\":\"0.00000000\",\"stripped\":\"1000\"}");
    }
}
