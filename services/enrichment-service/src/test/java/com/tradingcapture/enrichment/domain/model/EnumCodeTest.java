package com.tradingcapture.enrichment.domain.model;

import com.tradingcapture.enrichment.domain.model.converter.FeeBasisConverter;
import com.tradingcapture.enrichment.domain.model.converter.IdentifierSourceConverter;
import com.tradingcapture.enrichment.domain.model.converter.InstrumentTypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Enum converters round-trip their string codes and fail loudly on unknown codes (ADR-0014). */
class EnumCodeTest {

    @Test
    void instrumentTypeRoundTrips() {
        InstrumentTypeConverter converter = new InstrumentTypeConverter();
        for (InstrumentType type : InstrumentType.values()) {
            assertThat(converter.convertToDatabaseColumn(type)).isEqualTo(type.name());
            assertThat(converter.convertToEntityAttribute(type.name())).isEqualTo(type);
        }
    }

    @Test
    void feeBasisRoundTrips() {
        FeeBasisConverter converter = new FeeBasisConverter();
        assertThat(converter.convertToDatabaseColumn(FeeBasis.PERCENTAGE)).isEqualTo("PERCENTAGE");
        assertThat(converter.convertToEntityAttribute("ABSOLUTE")).isEqualTo(FeeBasis.ABSOLUTE);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> new IdentifierSourceConverter().convertToEntityAttribute("BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void exchangeSymbolIsTheOnlyExchangeScopedSource() {
        assertThat(IdentifierSource.EXCHANGE_SYMBOL.isExchangeScoped()).isTrue();
        assertThat(IdentifierSource.CUSIP.isExchangeScoped()).isFalse();
        assertThat(IdentifierSource.ISIN.isExchangeScoped()).isFalse();
        assertThat(IdentifierSource.SEDOL.isExchangeScoped()).isFalse();
    }
}
