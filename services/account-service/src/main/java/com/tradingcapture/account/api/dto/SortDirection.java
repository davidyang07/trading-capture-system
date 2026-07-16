package com.tradingcapture.account.api.dto;

import org.springframework.data.domain.Sort;

/** API-facing sort direction, decoupled from Spring's {@code Sort.Direction}. */
public enum SortDirection {
    ASC,
    DESC;

    public Sort.Direction direction() {
        return this == ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
