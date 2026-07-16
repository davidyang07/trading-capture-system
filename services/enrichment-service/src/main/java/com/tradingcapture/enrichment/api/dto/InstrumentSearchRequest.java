package com.tradingcapture.enrichment.api.dto;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import com.tradingcapture.enrichment.domain.service.InstrumentSearchCriteria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Instrument search query parameters; sorting always appends instrumentId ASC as tiebreaker. */
public record InstrumentSearchRequest(
        InstrumentType instrumentType,
        String exchangeMic,
        String currency,
        ActivationStatus status,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        InstrumentSortField sortBy,
        SortDirection sortDir) {

    public InstrumentSearchCriteria toCriteria() {
        return new InstrumentSearchCriteria(instrumentType, exchangeMic, currency, status);
    }

    public Pageable toPageable() {
        InstrumentSortField field = sortBy != null ? sortBy : InstrumentSortField.INSTRUMENT_ID;
        SortDirection direction = sortDir != null ? sortDir : SortDirection.ASC;
        Sort sort = Sort.by(direction.direction(), field.property())
                .and(Sort.by(Sort.Direction.ASC, "instrumentId"));
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20, sort);
    }
}
