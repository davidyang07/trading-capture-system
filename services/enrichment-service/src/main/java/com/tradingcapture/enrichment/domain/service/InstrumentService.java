package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.api.dto.InstrumentSummaryResponse;
import com.tradingcapture.enrichment.api.mapper.InstrumentMapper;
import com.tradingcapture.enrichment.cache.CacheKeys;
import com.tradingcapture.enrichment.cache.ReferenceDataCache;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.exception.InvalidReferenceException;
import com.tradingcapture.enrichment.domain.exception.ResourceNotFoundException;
import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.Instrument;
import com.tradingcapture.enrichment.domain.repository.AlternateIdentifierRepository;
import com.tradingcapture.enrichment.domain.repository.CurrencyRepository;
import com.tradingcapture.enrichment.domain.repository.ExchangeRepository;
import com.tradingcapture.enrichment.domain.repository.InstrumentRepository;
import com.tradingcapture.enrichment.domain.repository.InstrumentSpecifications;
import com.tradingcapture.enrichment.domain.service.command.InstrumentCommands;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Instrument administration and lookups. Reads are served through the Redis read-through cache
 * ({@link ReferenceDataCache}); administrative mutations invalidate the affected cache keys. The
 * database is always authoritative — a Redis outage only costs cache hits.
 */
@Service
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);

    private final InstrumentRepository instruments;
    private final AlternateIdentifierRepository identifiers;
    private final ExchangeRepository exchanges;
    private final CurrencyRepository currencies;
    private final InstrumentMapper mapper;
    private final ReferenceDataCache cache;
    private final EnrichmentMetrics metrics;
    private final Clock clock;

    public InstrumentService(InstrumentRepository instruments,
                             AlternateIdentifierRepository identifiers,
                             ExchangeRepository exchanges,
                             CurrencyRepository currencies,
                             InstrumentMapper mapper,
                             ReferenceDataCache cache,
                             EnrichmentMetrics metrics,
                             Clock clock) {
        this.instruments = instruments;
        this.identifiers = identifiers;
        this.exchanges = exchanges;
        this.currencies = currencies;
        this.mapper = mapper;
        this.cache = cache;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** An alternate-identifier lookup key; {@code exchangeMic} is optional (disambiguates symbols). */
    public record LookupKey(IdentifierSource source, String value, String exchangeMic) {
    }

    // ---------------------------------------------------------------- administration

    @Transactional
    public InstrumentResponse create(InstrumentCommands.Create command) {
        String exchangeMic = command.exchangeMic();
        String currencyCode = command.currencyCode();
        requireReferences(exchangeMic, currencyCode);
        if (instruments.existsById(command.instrumentId())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_INSTRUMENT_ID,
                    "Instrument ID already in use: " + command.instrumentId());
        }
        Instant now = Instant.now(clock);
        Instrument instrument = new Instrument(command.instrumentId(), command.name(),
                command.instrumentType(), exchangeMic, currencyCode, now);
        applyIdentifiers(instrument, command.identifiers(), now);
        instruments.save(instrument);
        metrics.referenceMutation();
        log.info("Instrument created: instrumentId={} exchange={} identifiers={}",
                instrument.getInstrumentId(), exchangeMic, command.identifiers().size());
        return mapper.toResponse(instrument);
    }

    @Transactional
    public InstrumentResponse update(String instrumentId, InstrumentCommands.Update command) {
        Instrument instrument = instruments.findWithIdentifiersByInstrumentId(instrumentId)
                .orElseThrow(() -> ResourceNotFoundException.instrument(instrumentId));
        requireReferences(command.exchangeMic(), command.currencyCode());

        Set<String> keysToEvict = identifierCacheKeys(instrument);
        Instant now = Instant.now(clock);
        instrument.update(command.name(), command.instrumentType(), command.exchangeMic(), command.currencyCode(), now);
        instrument.clearIdentifiers();
        // Flush the orphan-removal DELETEs before re-inserting: Hibernate orders child INSERTs
        // before orphan DELETEs in one flush, so an unchanged identifier would otherwise collide
        // on uq_alt_ids_source_value_exchange. Flushing here forces DELETE-before-INSERT.
        instruments.flush();
        applyIdentifiers(instrument, command.identifiers(), now);
        instruments.save(instrument);

        keysToEvict.addAll(identifierCacheKeys(instrument));
        keysToEvict.add(CacheKeys.instrument(instrumentId));
        evict(keysToEvict);
        metrics.referenceMutation();
        log.info("Instrument updated: instrumentId={}", instrumentId);
        return mapper.toResponse(instrument);
    }

    @Transactional
    public InstrumentResponse setStatus(String instrumentId, ActivationStatus status) {
        Instrument instrument = instruments.findWithIdentifiersByInstrumentId(instrumentId)
                .orElseThrow(() -> ResourceNotFoundException.instrument(instrumentId));
        instrument.setStatus(status, Instant.now(clock));
        Set<String> keys = identifierCacheKeys(instrument);
        keys.add(CacheKeys.instrument(instrumentId));
        evict(keys);
        metrics.referenceMutation();
        log.info("Instrument status changed: instrumentId={} status={}", instrumentId, status);
        return mapper.toResponse(instrument);
    }

    @Transactional(readOnly = true)
    public Page<InstrumentSummaryResponse> search(InstrumentSearchCriteria criteria, Pageable pageable) {
        List<Specification<Instrument>> parts = new ArrayList<>();
        if (criteria.instrumentType() != null) {
            parts.add(InstrumentSpecifications.hasType(criteria.instrumentType()));
        }
        if (StringUtils.hasText(criteria.exchangeMic())) {
            parts.add(InstrumentSpecifications.hasExchange(criteria.exchangeMic()));
        }
        if (StringUtils.hasText(criteria.currency())) {
            parts.add(InstrumentSpecifications.hasCurrency(criteria.currency()));
        }
        if (criteria.status() != null) {
            parts.add(InstrumentSpecifications.hasStatus(criteria.status()));
        }
        return instruments.findAll(Specification.allOf(parts), pageable).map(mapper::toSummary);
    }

    // ---------------------------------------------------------------- lookups (cached)

    @Transactional(readOnly = true)
    public InstrumentResponse getById(String instrumentId) {
        String key = CacheKeys.instrument(instrumentId);
        return cache.get(key, InstrumentResponse.class).orElseGet(() -> {
            InstrumentResponse response = instruments.findWithIdentifiersByInstrumentId(instrumentId)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> ResourceNotFoundException.instrument(instrumentId));
            cache.put(key, response);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public InstrumentResponse getByIdentifier(IdentifierSource source, String rawValue, String rawExchangeMic) {
        String value = normalize(rawValue);
        String exchangeMic = StringUtils.hasText(rawExchangeMic) ? normalize(rawExchangeMic) : null;
        String key = CacheKeys.identifier(source, value, exchangeMic);
        return cache.get(key, InstrumentResponse.class).orElseGet(() -> {
            List<Instrument> matches = exchangeMic != null
                    ? identifiers.findInstruments(source, value, exchangeMic)
                    : identifiers.findInstruments(source, value);
            if (matches.isEmpty()) {
                throw ResourceNotFoundException.identifier(source.name(), value);
            }
            if (matches.size() > 1) {
                throw new BusinessRuleException(ErrorCode.AMBIGUOUS_IDENTIFIER,
                        "Identifier " + source + ":" + value + " matches " + matches.size()
                                + " instruments; specify an exchange");
            }
            // Reload with identifiers so the cached full response includes them.
            InstrumentResponse response = instruments
                    .findWithIdentifiersByInstrumentId(matches.get(0).getInstrumentId())
                    .map(mapper::toResponse)
                    .orElseThrow(() -> ResourceNotFoundException.identifier(source.name(), value));
            cache.put(key, response);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public List<BatchResolution> resolveBatch(List<LookupKey> keys) {
        List<BatchResolution> results = new ArrayList<>(keys.size());
        for (LookupKey key : keys) {
            String value = normalize(key.value());
            String exchangeMic = StringUtils.hasText(key.exchangeMic()) ? normalize(key.exchangeMic()) : null;
            List<Instrument> matches = exchangeMic != null
                    ? identifiers.findInstruments(key.source(), value, exchangeMic)
                    : identifiers.findInstruments(key.source(), value);
            if (matches.isEmpty()) {
                results.add(new BatchResolution(key, false, false, null));
            } else if (matches.size() > 1) {
                results.add(new BatchResolution(key, true, true, null));
            } else {
                results.add(new BatchResolution(key, true, false, mapper.toSummary(matches.get(0))));
            }
        }
        return results;
    }

    /** Batch resolution outcome for one lookup key (summary, no alternate identifiers). */
    public record BatchResolution(LookupKey key, boolean found, boolean ambiguous, InstrumentSummaryResponse instrument) {
    }

    // ---------------------------------------------------------------- helpers

    private void requireReferences(String exchangeMic, String currencyCode) {
        if (!exchanges.existsById(exchangeMic)) {
            throw new InvalidReferenceException("Unknown exchange MIC: " + exchangeMic);
        }
        if (!currencies.existsById(currencyCode)) {
            throw new InvalidReferenceException("Unknown currency code: " + currencyCode);
        }
    }

    private void applyIdentifiers(Instrument instrument, List<InstrumentCommands.Identifier> requested, Instant now) {
        Set<String> seen = new LinkedHashSet<>();
        for (InstrumentCommands.Identifier identifier : requested) {
            String value = normalize(identifier.value());
            String dedupeKey = identifier.source().name() + ":" + value;
            if (!seen.add(dedupeKey)) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_IDENTIFIER,
                        "Duplicate identifier in request: " + dedupeKey);
            }
            // Globally-unique sources must not already belong to another instrument. Exchange
            // symbols are unique only per exchange, enforced by the DB unique constraint.
            if (!identifier.source().isExchangeScoped()
                    && identifiers.existsForOtherInstrument(identifier.source(), value, instrument.getInstrumentId())) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_IDENTIFIER,
                        identifier.source() + " identifier already assigned: " + value);
            }
            instrument.addIdentifier(identifier.source(), value, now);
        }
    }

    private Set<String> identifierCacheKeys(Instrument instrument) {
        Set<String> keys = new LinkedHashSet<>();
        instrument.getAlternateIdentifiers().forEach(id -> {
            keys.add(CacheKeys.identifier(id.getSource(), id.getValue(), id.getExchangeMic()));
            keys.add(CacheKeys.identifier(id.getSource(), id.getValue(), null));
        });
        return keys;
    }

    private void evict(Set<String> keys) {
        if (!keys.isEmpty()) {
            cache.evict(keys.toArray(String[]::new));
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
