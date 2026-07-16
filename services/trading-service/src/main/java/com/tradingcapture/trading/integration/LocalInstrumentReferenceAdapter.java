package com.tradingcapture.trading.integration;

import com.tradingcapture.trading.domain.exception.InvalidReferenceException;
import com.tradingcapture.trading.domain.port.InstrumentReferencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Phase 3 local implementation of the enrichment boundary: enforces the identifier format
 * contract only. Remote existence validation against enrichment-service arrives in Phase 5
 * and replaces this adapter behind the same port (documented in docs/status/known-gaps.md).
 * Domain invariants do not depend on this adapter's leniency — it rejects malformed IDs
 * rather than approving anything blindly.
 */
@Component
public class LocalInstrumentReferenceAdapter implements InstrumentReferencePort {

    private static final Logger log = LoggerFactory.getLogger(LocalInstrumentReferenceAdapter.class);
    private static final Pattern INSTRUMENT_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public void validateInstrument(String instrumentId) {
        if (instrumentId == null || !INSTRUMENT_ID.matcher(instrumentId).matches()) {
            throw new InvalidReferenceException(
                    "instrumentId must match " + INSTRUMENT_ID.pattern());
        }
        log.debug("Instrument {} accepted by local format validation (remote enrichment validation is a Phase 5 integration)",
                instrumentId);
    }
}
