package com.tradingcapture.enrichment.domain.service;

import com.tradingcapture.enrichment.api.dto.InstrumentResponse;
import com.tradingcapture.enrichment.api.mapper.InstrumentMapper;
import com.tradingcapture.enrichment.cache.ReferenceDataCache;
import com.tradingcapture.enrichment.domain.exception.BusinessRuleException;
import com.tradingcapture.enrichment.domain.exception.ErrorCode;
import com.tradingcapture.enrichment.domain.exception.InvalidReferenceException;
import com.tradingcapture.enrichment.domain.exception.ResourceNotFoundException;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.Instrument;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.repository.AlternateIdentifierRepository;
import com.tradingcapture.enrichment.domain.repository.CurrencyRepository;
import com.tradingcapture.enrichment.domain.repository.ExchangeRepository;
import com.tradingcapture.enrichment.domain.repository.InstrumentRepository;
import com.tradingcapture.enrichment.domain.service.command.InstrumentCommands;
import com.tradingcapture.enrichment.observability.EnrichmentMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @Mock private InstrumentRepository instruments;
    @Mock private AlternateIdentifierRepository identifiers;
    @Mock private ExchangeRepository exchanges;
    @Mock private CurrencyRepository currencies;
    @Mock private ReferenceDataCache cache;
    @Mock private EnrichmentMetrics metrics;

    private InstrumentService service() {
        return new InstrumentService(instruments, identifiers, exchanges, currencies,
                new InstrumentMapper(), cache, metrics, CLOCK);
    }

    private void validReferences() {
        lenient().when(exchanges.existsById("XNAS")).thenReturn(true);
        lenient().when(currencies.existsById("USD")).thenReturn(true);
    }

    private static InstrumentCommands.Create create(List<InstrumentCommands.Identifier> ids) {
        return new InstrumentCommands.Create("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD", ids);
    }

    private static Instrument instrument() {
        return new Instrument("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD", CLOCK.instant());
    }

    @Test
    void createRejectsUnknownExchange() {
        when(exchanges.existsById("XNAS")).thenReturn(false);
        assertThatThrownBy(() -> service().create(create(List.of())))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void createRejectsUnknownCurrency() {
        when(exchanges.existsById("XNAS")).thenReturn(true);
        when(currencies.existsById("USD")).thenReturn(false);
        assertThatThrownBy(() -> service().create(create(List.of())))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void createRejectsDuplicateInstrumentId() {
        validReferences();
        when(instruments.existsById("AAPL")).thenReturn(true);
        assertThatThrownBy(() -> service().create(create(List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_INSTRUMENT_ID);
    }

    @Test
    void createNormalizesIdentifierValues() {
        validReferences();
        when(instruments.existsById("AAPL")).thenReturn(false);

        service().create(create(List.of(new InstrumentCommands.Identifier(IdentifierSource.ISIN, " us0378331005 "))));

        ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
        verify(instruments).save(captor.capture());
        assertThat(captor.getValue().getAlternateIdentifiers()).singleElement()
                .satisfies(id -> assertThat(id.getValue()).isEqualTo("US0378331005"));
    }

    @Test
    void createRejectsDuplicateIdentifierWithinRequest() {
        validReferences();
        when(instruments.existsById("AAPL")).thenReturn(false);

        List<InstrumentCommands.Identifier> ids = List.of(
                new InstrumentCommands.Identifier(IdentifierSource.ISIN, "US0378331005"),
                new InstrumentCommands.Identifier(IdentifierSource.ISIN, "us0378331005"));

        assertThatThrownBy(() -> service().create(create(ids)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_IDENTIFIER);
        verify(instruments, never()).save(any());
    }

    @Test
    void createRejectsAGloballyAssignedIsin() {
        validReferences();
        when(instruments.existsById("AAPL")).thenReturn(false);
        when(identifiers.existsForOtherInstrument(IdentifierSource.ISIN, "US0378331005", "AAPL")).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                create(List.of(new InstrumentCommands.Identifier(IdentifierSource.ISIN, "US0378331005")))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_IDENTIFIER);
    }

    @Test
    void exchangeSymbolUniquenessIsNotGloballyPrechecked() {
        validReferences();
        when(instruments.existsById("AAPL")).thenReturn(false);

        // An exchange symbol is unique only per exchange (DB constraint), so no global precheck.
        service().create(create(List.of(new InstrumentCommands.Identifier(IdentifierSource.EXCHANGE_SYMBOL, "AAPL"))));

        verify(identifiers, never()).existsForOtherInstrument(eq(IdentifierSource.EXCHANGE_SYMBOL), anyString(), anyString());
        verify(instruments).save(any());
    }

    @Test
    void getByIdentifierRejectsAmbiguousMatches() {
        when(cache.get(anyString(), eq(InstrumentResponse.class))).thenReturn(Optional.empty());
        when(identifiers.findInstruments(IdentifierSource.EXCHANGE_SYMBOL, "XYZ"))
                .thenReturn(List.of(instrument(), instrument()));

        assertThatThrownBy(() -> service().getByIdentifier(IdentifierSource.EXCHANGE_SYMBOL, "xyz", null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.AMBIGUOUS_IDENTIFIER);
    }

    @Test
    void getByIdentifierNotFound() {
        when(cache.get(anyString(), eq(InstrumentResponse.class))).thenReturn(Optional.empty());
        when(identifiers.findInstruments(IdentifierSource.CUSIP, "000000000")).thenReturn(List.of());

        assertThatThrownBy(() -> service().getByIdentifier(IdentifierSource.CUSIP, "000000000", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdReturnsCachedResponseWithoutHittingTheDatabase() {
        InstrumentResponse cached = new InstrumentResponse("AAPL", "Apple", InstrumentType.EQUITY, "XNAS", "USD",
                com.tradingcapture.enrichment.domain.model.ActivationStatus.ACTIVE, List.of(),
                CLOCK.instant(), CLOCK.instant(), 0L);
        when(cache.get(anyString(), eq(InstrumentResponse.class))).thenReturn(Optional.of(cached));

        assertThat(service().getById("AAPL")).isSameAs(cached);
        verify(instruments, never()).findWithIdentifiersByInstrumentId(anyString());
    }

    @Test
    void getByIdCacheMissLoadsAndPopulates() {
        when(cache.get(anyString(), eq(InstrumentResponse.class))).thenReturn(Optional.empty());
        when(instruments.findWithIdentifiersByInstrumentId("AAPL")).thenReturn(Optional.of(instrument()));

        InstrumentResponse response = service().getById("AAPL");

        assertThat(response.instrumentId()).isEqualTo("AAPL");
        verify(cache).put(anyString(), any());
    }
}
