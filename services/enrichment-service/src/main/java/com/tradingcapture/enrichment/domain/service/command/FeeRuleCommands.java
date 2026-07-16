package com.tradingcapture.enrichment.domain.service.command;

import com.tradingcapture.enrichment.domain.model.FeeBasis;
import com.tradingcapture.enrichment.domain.model.FeeType;
import com.tradingcapture.enrichment.domain.model.InstrumentType;

import java.math.BigDecimal;

/** Service-layer commands for fee-rule administration. */
public final class FeeRuleCommands {

    private FeeRuleCommands() {
    }

    public record Create(String feeRuleId,
                         String name,
                         FeeType feeType,
                         FeeBasis basis,
                         BigDecimal rate,
                         String currencyCode,
                         String exchangeMic,
                         InstrumentType instrumentType) {
    }

    public record Update(String name,
                         FeeType feeType,
                         FeeBasis basis,
                         BigDecimal rate,
                         String currencyCode,
                         String exchangeMic,
                         InstrumentType instrumentType) {
    }
}
