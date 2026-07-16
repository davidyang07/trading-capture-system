package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.Instrument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, String>, JpaSpecificationExecutor<Instrument> {

    /** Loads an instrument together with its alternate identifiers (avoids a lazy-init on detail reads). */
    @EntityGraph(attributePaths = "alternateIdentifiers")
    Optional<Instrument> findWithIdentifiersByInstrumentId(String instrumentId);
}
