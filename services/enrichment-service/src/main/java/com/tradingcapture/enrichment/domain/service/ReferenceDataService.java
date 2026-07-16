package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.api.dto.CurrencyDtos.CurrencyResponse;
import com.tradingcapture.enrichment.api.dto.ExchangeDtos.ExchangeResponse;
import com.tradingcapture.enrichment.api.dto.SettlementOptionDtos.SettlementOptionResponse;
import com.tradingcapture.enrichment.api.mapper.ReferenceMapper;
import com.tradingcapture.enrichment.cache.CacheKeys;
import com.tradingcapture.enrichment.cache.ReferenceDataCache;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.exception.ResourceNotFoundException;
import com.tradingcapture.enrichment.domain.model.Currency;
import com.tradingcapture.enrichment.domain.model.Exchange;
import com.tradingcapture.enrichment.domain.model.SettlementOption;
import com.tradingcapture.enrichment.domain.repository.CurrencyRepository;
import com.tradingcapture.enrichment.domain.repository.ExchangeRepository;
import com.tradingcapture.enrichment.domain.repository.SettlementOptionRepository;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Administration and cached lookups for exchanges, currencies, and settlement options. Exchange
 * and currency single-record reads are Redis-cached (consumed by other services); administrative
 * mutations invalidate the affected key. The database is always authoritative.
 */
@Service
public class ReferenceDataService {

    private final ExchangeRepository exchanges;
    private final CurrencyRepository currencies;
    private final SettlementOptionRepository settlementOptions;
    private final ReferenceMapper mapper;
    private final ReferenceDataCache cache;
    private final EnrichmentMetrics metrics;
    private final Clock clock;

    public ReferenceDataService(ExchangeRepository exchanges,
                                CurrencyRepository currencies,
                                SettlementOptionRepository settlementOptions,
                                ReferenceMapper mapper,
                                ReferenceDataCache cache,
                                EnrichmentMetrics metrics,
                                Clock clock) {
        this.exchanges = exchanges;
        this.currencies = currencies;
        this.settlementOptions = settlementOptions;
        this.mapper = mapper;
        this.cache = cache;
        this.metrics = metrics;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- exchanges

    @Transactional
    public ExchangeResponse createExchange(String mic, String name, String country, String timezone) {
        if (exchanges.existsById(mic)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_EXCHANGE, "Exchange already exists: " + mic);
        }
        Exchange exchange = exchanges.save(new Exchange(mic, name, country, timezone, Instant.now(clock)));
        metrics.referenceMutation();
        return mapper.toResponse(exchange);
    }

    @Transactional
    public ExchangeResponse updateExchange(String mic, String name, String country, String timezone) {
        Exchange exchange = exchanges.findById(mic).orElseThrow(() -> ResourceNotFoundException.exchange(mic));
        exchange.update(name, country, timezone, Instant.now(clock));
        cache.evict(CacheKeys.exchange(mic));
        metrics.referenceMutation();
        return mapper.toResponse(exchange);
    }

    @Transactional(readOnly = true)
    public ExchangeResponse getExchange(String mic) {
        String key = CacheKeys.exchange(mic);
        return cache.get(key, ExchangeResponse.class).orElseGet(() -> {
            ExchangeResponse response = exchanges.findById(mic)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> ResourceNotFoundException.exchange(mic));
            cache.put(key, response);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public List<ExchangeResponse> listExchanges() {
        return exchanges.findAll(Sort.by("mic")).stream().map(mapper::toResponse).toList();
    }

    // ---------------------------------------------------------------- currencies

    @Transactional
    public CurrencyResponse createCurrency(String code, String name, int minorUnits) {
        if (currencies.existsById(code)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_CURRENCY, "Currency already exists: " + code);
        }
        Currency currency = currencies.save(new Currency(code, name, minorUnits, Instant.now(clock)));
        metrics.referenceMutation();
        return mapper.toResponse(currency);
    }

    @Transactional
    public CurrencyResponse updateCurrency(String code, String name, int minorUnits) {
        Currency currency = currencies.findById(code).orElseThrow(() -> ResourceNotFoundException.currency(code));
        currency.update(name, minorUnits, Instant.now(clock));
        cache.evict(CacheKeys.currency(code));
        metrics.referenceMutation();
        return mapper.toResponse(currency);
    }

    @Transactional(readOnly = true)
    public CurrencyResponse getCurrency(String code) {
        String key = CacheKeys.currency(code);
        return cache.get(key, CurrencyResponse.class).orElseGet(() -> {
            CurrencyResponse response = currencies.findById(code)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> ResourceNotFoundException.currency(code));
            cache.put(key, response);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> listCurrencies() {
        return currencies.findAll(Sort.by("code")).stream().map(mapper::toResponse).toList();
    }

    // ---------------------------------------------------------------- settlement options

    @Transactional
    public SettlementOptionResponse createSettlementOption(String code, String name, int settlementDays, String description) {
        if (settlementOptions.existsById(code)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_SETTLEMENT_OPTION, "Settlement option already exists: " + code);
        }
        SettlementOption option = settlementOptions.save(
                new SettlementOption(code, name, settlementDays, description, Instant.now(clock)));
        metrics.referenceMutation();
        return mapper.toResponse(option);
    }

    @Transactional
    public SettlementOptionResponse updateSettlementOption(String code, String name, int settlementDays, String description) {
        SettlementOption option = settlementOptions.findById(code)
                .orElseThrow(() -> ResourceNotFoundException.settlementOption(code));
        option.update(name, settlementDays, description, Instant.now(clock));
        metrics.referenceMutation();
        return mapper.toResponse(option);
    }

    @Transactional(readOnly = true)
    public SettlementOptionResponse getSettlementOption(String code) {
        return settlementOptions.findById(code)
                .map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.settlementOption(code));
    }

    @Transactional(readOnly = true)
    public List<SettlementOptionResponse> listSettlementOptions() {
        return settlementOptions.findAll(Sort.by("code")).stream().map(mapper::toResponse).toList();
    }
}
