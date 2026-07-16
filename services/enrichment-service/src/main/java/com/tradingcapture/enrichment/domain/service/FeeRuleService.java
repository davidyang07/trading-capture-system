package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.api.dto.FeeRuleDtos.FeeRuleResponse;
import com.tradingcapture.enrichment.api.mapper.ReferenceMapper;
import com.tradingcapture.enrichment.cache.CacheKeys;
import com.tradingcapture.enrichment.cache.ReferenceDataCache;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.exception.InvalidReferenceException;
import com.tradingcapture.enrichment.domain.exception.ResourceNotFoundException;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.FeeRule;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.repository.CurrencyRepository;
import com.tradingcapture.enrichment.domain.repository.ExchangeRepository;
import com.tradingcapture.enrichment.domain.repository.FeeRuleRepository;
import com.tradingcapture.enrichment.domain.service.command.FeeRuleCommands;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Fee-rule administration and applicable-rule lookup. The active-rule set is cached under one
 * Redis key and filtered by dimension in memory, so any fee-rule mutation invalidates the cache
 * with a single eviction. This service stores/serves rules only — it never computes fees.
 */
@Service
public class FeeRuleService {

    private final FeeRuleRepository feeRules;
    private final ExchangeRepository exchanges;
    private final CurrencyRepository currencies;
    private final ReferenceMapper mapper;
    private final ReferenceDataCache cache;
    private final EnrichmentMetrics metrics;
    private final Clock clock;

    public FeeRuleService(FeeRuleRepository feeRules,
                          ExchangeRepository exchanges,
                          CurrencyRepository currencies,
                          ReferenceMapper mapper,
                          ReferenceDataCache cache,
                          EnrichmentMetrics metrics,
                          Clock clock) {
        this.feeRules = feeRules;
        this.exchanges = exchanges;
        this.currencies = currencies;
        this.mapper = mapper;
        this.cache = cache;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public FeeRuleResponse create(FeeRuleCommands.Create command) {
        if (feeRules.existsById(command.feeRuleId())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_FEE_RULE, "Fee rule already exists: " + command.feeRuleId());
        }
        requireDimensions(command.exchangeMic(), command.currencyCode());
        FeeRule rule = new FeeRule(command.feeRuleId(), command.name(), command.feeType(), command.basis(),
                command.rate(), command.currencyCode(), command.exchangeMic(), command.instrumentType(), Instant.now(clock));
        feeRules.save(rule);
        evictAll();
        metrics.referenceMutation();
        return mapper.toResponse(rule);
    }

    @Transactional
    public FeeRuleResponse update(String feeRuleId, FeeRuleCommands.Update command) {
        FeeRule rule = feeRules.findById(feeRuleId).orElseThrow(() -> ResourceNotFoundException.feeRule(feeRuleId));
        requireDimensions(command.exchangeMic(), command.currencyCode());
        rule.update(command.name(), command.feeType(), command.basis(), command.rate(),
                command.currencyCode(), command.exchangeMic(), command.instrumentType(), Instant.now(clock));
        evictAll();
        metrics.referenceMutation();
        return mapper.toResponse(rule);
    }

    @Transactional
    public FeeRuleResponse setStatus(String feeRuleId, ActivationStatus status) {
        FeeRule rule = feeRules.findById(feeRuleId).orElseThrow(() -> ResourceNotFoundException.feeRule(feeRuleId));
        rule.setStatus(status, Instant.now(clock));
        evictAll();
        metrics.referenceMutation();
        return mapper.toResponse(rule);
    }

    @Transactional(readOnly = true)
    public FeeRuleResponse getById(String feeRuleId) {
        return feeRules.findById(feeRuleId).map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.feeRule(feeRuleId));
    }

    @Transactional(readOnly = true)
    public List<FeeRuleResponse> listAll() {
        return feeRules.findAll().stream()
                .sorted((a, b) -> a.getFeeRuleId().compareTo(b.getFeeRuleId()))
                .map(mapper::toResponse).toList();
    }

    /**
     * Active fee rules applicable to a trade with the given (optional) dimensions. Served from the
     * cached active-rule set and filtered in memory: a rule matches a dimension when its own value
     * is null (wildcard) or equals the supplied value; a null argument does not filter that dimension.
     */
    @Transactional(readOnly = true)
    public List<FeeRuleResponse> findApplicable(String exchangeMic, InstrumentType instrumentType, String currencyCode) {
        String key = CacheKeys.feeRulesActive();
        List<FeeRuleResponse> active = cache.getList(key, FeeRuleResponse.class).orElseGet(() -> {
            List<FeeRuleResponse> loaded = feeRules.findByStatusOrderByFeeRuleId(ActivationStatus.ACTIVE)
                    .stream().map(mapper::toResponse).toList();
            cache.put(key, loaded);
            return loaded;
        });
        return active.stream()
                .filter(rule -> dimensionMatches(rule.exchangeMic(), exchangeMic))
                .filter(rule -> instrumentType == null || rule.instrumentType() == null || rule.instrumentType() == instrumentType)
                .filter(rule -> dimensionMatches(rule.currencyCode(), currencyCode))
                .toList();
    }

    private static boolean dimensionMatches(String ruleValue, String requested) {
        return requested == null || ruleValue == null || ruleValue.equals(requested);
    }

    private void requireDimensions(String exchangeMic, String currencyCode) {
        if (StringUtils.hasText(exchangeMic) && !exchanges.existsById(exchangeMic)) {
            throw new InvalidReferenceException("Unknown exchange MIC: " + exchangeMic);
        }
        if (StringUtils.hasText(currencyCode) && !currencies.existsById(currencyCode)) {
            throw new InvalidReferenceException("Unknown currency code: " + currencyCode);
        }
    }

    private void evictAll() {
        cache.evict(CacheKeys.feeRulesActive());
    }
}
