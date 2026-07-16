package com.tradingcapture.enrichment.api.mapper;

import com.tradingcapture.enrichment.api.dto.CurrencyDtos.CurrencyResponse;
import com.tradingcapture.enrichment.api.dto.ExchangeDtos.ExchangeResponse;
import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.FeeRuleResponse;
import com.tradingcapture.enrichment.api.dto.SettlementOptionDtos.SettlementOptionResponse;
import com.tradingcapture.enrichment.domain.model.Currency;
import com.tradingcapture.enrichment.domain.model.Exchange;
import com.tradingcapture.enrichment.domain.model.FeeRule;
import com.tradingcapture.enrichment.domain.model.SettlementOption;
import org.springframework.stereotype.Component;

/** Hand-written mapping for the reference-data aggregates. */
@Component
public class ReferenceMapper {

    public ExchangeResponse toResponse(Exchange exchange) {
        return new ExchangeResponse(exchange.getMic(), exchange.getName(), exchange.getCountry(),
                exchange.getTimezone(), exchange.getCreatedAt(), exchange.getUpdatedAt(), exchange.getVersion());
    }

    public CurrencyResponse toResponse(Currency currency) {
        return new CurrencyResponse(currency.getCode(), currency.getName(), currency.getMinorUnits(),
                currency.getCreatedAt(), currency.getUpdatedAt(), currency.getVersion());
    }

    public SettlementOptionResponse toResponse(SettlementOption option) {
        return new SettlementOptionResponse(option.getCode(), option.getName(), option.getSettlementDays(),
                option.getDescription(), option.getCreatedAt(), option.getUpdatedAt(), option.getVersion());
    }

    public FeeRuleResponse toResponse(FeeRule rule) {
        return new FeeRuleResponse(rule.getFeeRuleId(), rule.getName(), rule.getFeeType(), rule.getBasis(),
                rule.getRate(), rule.getCurrencyCode(), rule.getExchangeMic(), rule.getInstrumentType(),
                rule.getStatus(), rule.getCreatedAt(), rule.getUpdatedAt(), rule.getVersion());
    }
}
