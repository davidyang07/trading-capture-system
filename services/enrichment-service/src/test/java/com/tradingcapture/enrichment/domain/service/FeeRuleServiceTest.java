package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.FeeRuleResponse;
import com.tradingcapture.enrichment.api.mapper.ReferenceMapper;
import com.tradingcapture.enrichment.cache.ReferenceDataCache;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.FeeBasis;
import com.tradingcapture.enrichment.domain.model.FeeType;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.repository.CurrencyRepository;
import com.tradingcapture.enrichment.domain.repository.ExchangeRepository;
import com.tradingcapture.enrichment.domain.repository.FeeRuleRepository;
import com.tradingcapture.enrichment.domain.service.command.FeeRuleCommands;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeRuleServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @Mock private FeeRuleRepository feeRules;
    @Mock private ExchangeRepository exchanges;
    @Mock private CurrencyRepository currencies;
    @Mock private ReferenceDataCache cache;
    @Mock private EnrichmentMetrics metrics;

    private FeeRuleService service() {
        return new FeeRuleService(feeRules, exchanges, currencies, new ReferenceMapper(), cache, metrics, CLOCK);
    }

    private static FeeRuleResponse rule(String id, String exchange, InstrumentType type, String ccy) {
        return new FeeRuleResponse(id, id, FeeType.COMMISSION, FeeBasis.PERCENTAGE, new BigDecimal("0.001"),
                ccy, exchange, type, ActivationStatus.ACTIVE, CLOCK.instant(), CLOCK.instant(), 0L);
    }

    @Test
    void createRejectsDuplicate() {
        when(feeRules.existsById("R1")).thenReturn(true);
        FeeRuleCommands.Create command = new FeeRuleCommands.Create("R1", "n", FeeType.COMMISSION,
                FeeBasis.PERCENTAGE, new BigDecimal("0.001"), null, null, null);

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_FEE_RULE);
    }

    @Test
    void applicableRulesMatchWildcardsAndDimensions() {
        List<FeeRuleResponse> active = List.of(
                rule("US-EQ", null, InstrumentType.EQUITY, "USD"),
                rule("LSE", "XLON", null, "GBP"),
                rule("GLOBAL", null, null, null));
        when(cache.getList(anyString(), eq(FeeRuleResponse.class))).thenReturn(Optional.of(active));

        List<FeeRuleResponse> forUsEquity = service().findApplicable("XNAS", InstrumentType.EQUITY, "USD");
        assertThat(forUsEquity).extracting(FeeRuleResponse::feeRuleId).containsExactlyInAnyOrder("US-EQ", "GLOBAL");

        List<FeeRuleResponse> forLondon = service().findApplicable("XLON", InstrumentType.EQUITY, "GBP");
        assertThat(forLondon).extracting(FeeRuleResponse::feeRuleId).containsExactlyInAnyOrder("LSE", "GLOBAL");
    }

    @Test
    void applicableLoadsFromDatabaseOnCacheMissAndPopulates() {
        when(cache.getList(anyString(), eq(FeeRuleResponse.class))).thenReturn(Optional.empty());
        when(feeRules.findByStatusOrderByFeeRuleId(ActivationStatus.ACTIVE)).thenReturn(List.of());

        service().findApplicable(null, null, null);

        org.mockito.Mockito.verify(cache).put(anyString(), org.mockito.ArgumentMatchers.any());
    }
}
