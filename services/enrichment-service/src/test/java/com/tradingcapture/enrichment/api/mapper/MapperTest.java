package com.tradingcapture.enrichment.api.mapper;

import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.FeeRuleResponse;
import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.domain.model.FeeBasis;
import com.tradingcapture.enrichment.domain.model.FeeRule;
import com.tradingcapture.enrichment.domain.model.FeeType;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.Instrument;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Test
    void instrumentMapsWithIdentifiers() {
        Instrument instrument = new Instrument("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD", NOW);
        instrument.addIdentifier(IdentifierSource.ISIN, "US0378331005", NOW);

        InstrumentResponse response = new InstrumentMapper().toResponse(instrument);

        assertThat(response.instrumentId()).isEqualTo("AAPL");
        assertThat(response.exchangeMic()).isEqualTo("XNAS");
        assertThat(response.alternateIdentifiers()).singleElement()
                .satisfies(id -> {
                    assertThat(id.source()).isEqualTo(IdentifierSource.ISIN);
                    assertThat(id.value()).isEqualTo("US0378331005");
                    assertThat(id.exchangeMic()).isEqualTo("XNAS");
                });
    }

    @Test
    void instrumentSummaryOmitsIdentifiers() {
        Instrument instrument = new Instrument("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD", NOW);
        assertThat(new InstrumentMapper().toSummary(instrument).instrumentId()).isEqualTo("AAPL");
    }

    @Test
    void feeRuleMaps() {
        FeeRule rule = new FeeRule("R1", "Commission", FeeType.COMMISSION, FeeBasis.PERCENTAGE,
                new BigDecimal("0.00100000"), "USD", null, InstrumentType.EQUITY, NOW);

        FeeRuleResponse response = new ReferenceMapper().toResponse(rule);

        assertThat(response.feeRuleId()).isEqualTo("R1");
        assertThat(response.basis()).isEqualTo(FeeBasis.PERCENTAGE);
        assertThat(response.rate()).isEqualByComparingTo("0.001");
        assertThat(response.instrumentType()).isEqualTo(InstrumentType.EQUITY);
    }
}
