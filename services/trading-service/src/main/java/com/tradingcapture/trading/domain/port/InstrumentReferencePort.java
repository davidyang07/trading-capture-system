package com.tradingcapture.trading.domain.port;

import com.tradingcapture.trading.domain.exception.InvalidReferenceException;

/**
 * Boundary to instrument reference data, owned by enrichment-service (Phase 5). Trading
 * never stores instrument details — only the identifier — so this port is validation-only.
 */
public interface InstrumentReferencePort {

    /** @throws InvalidReferenceException when the identifier is not acceptable */
    void validateInstrument(String instrumentId);
}
