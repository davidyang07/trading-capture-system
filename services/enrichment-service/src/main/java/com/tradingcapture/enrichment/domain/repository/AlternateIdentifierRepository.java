package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.AlternateIdentifier;
import com.tradingcapture.enrichment.domain.model.IdentifierSource;
import com.tradingcapture.enrichment.domain.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlternateIdentifierRepository extends JpaRepository<AlternateIdentifier, Long> {

    /** Instruments carrying this identifier across all exchanges (may be >1 for an exchange symbol). */
    @Query("select ai.instrument from AlternateIdentifier ai where ai.source = :source and ai.value = :value")
    List<Instrument> findInstruments(@Param("source") IdentifierSource source, @Param("value") String value);

    /** Instruments carrying this identifier on a specific exchange (disambiguates a symbol). */
    @Query("""
            select ai.instrument from AlternateIdentifier ai
            where ai.source = :source and ai.value = :value and ai.exchangeMic = :exchangeMic
            """)
    List<Instrument> findInstruments(@Param("source") IdentifierSource source,
                                     @Param("value") String value,
                                     @Param("exchangeMic") String exchangeMic);

    /** Global uniqueness pre-check for CUSIP/ISIN/SEDOL (excluding the instrument being edited). */
    @Query("""
            select case when count(ai) > 0 then true else false end from AlternateIdentifier ai
            where ai.source = :source and ai.value = :value and ai.instrument.instrumentId <> :excludeInstrumentId
            """)
    boolean existsForOtherInstrument(@Param("source") IdentifierSource source,
                                     @Param("value") String value,
                                     @Param("excludeInstrumentId") String excludeInstrumentId);
}
