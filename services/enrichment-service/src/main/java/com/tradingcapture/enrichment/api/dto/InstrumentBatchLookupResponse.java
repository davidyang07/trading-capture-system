package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;

import java.util.List;

/** Per-item instrument-resolution verdicts, in request order. */
public record InstrumentBatchLookupResponse(List<Result> results) {

    public record Result(IdentifierSource source,
                         String value,
                         String exchangeMic,
                         boolean found,
                         boolean ambiguous,
                         InstrumentSummaryResponse instrument) {
    }
}
