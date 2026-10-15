package com.tradingcapture.trading.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializerBase;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Serializes every {@link BigDecimal} in an API response as a JSON string, not a JSON number
 * (ADR-0014). The OpenAPI contract in {@code contracts/openapi/} declares these fields as
 * {@code type: string, format: decimal} precisely because IEEE-754 doubles cannot round-trip
 * an arbitrary-precision decimal: a consumer parsing {@code 12345678901234567.89} as a JSON
 * number has already lost the value before it can be inspected. Jackson's default is to emit
 * BigDecimal as a bare number, so the conversion must be registered explicitly.
 *
 * <p>The string is the plain form ({@link BigDecimal#toPlainString()}): {@code toString()} switches to
 * exponent notation for small or rescaled values, so a zero at scale 8 would go out as
 * {@code "0E-8"}, which most decimal parsers on the consumer side reject.</p>
 */
@Configuration
public class JsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimalsAsStrings() {
        return builder -> builder.serializerByType(BigDecimal.class, new PlainDecimalSerializer());
    }

    static final class PlainDecimalSerializer extends ToStringSerializerBase {

        PlainDecimalSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public String valueToString(Object value) {
            return ((BigDecimal) value).toPlainString();
        }
    }
}
