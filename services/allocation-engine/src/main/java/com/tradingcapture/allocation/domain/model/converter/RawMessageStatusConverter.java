package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.RawMessageStatus;
import jakarta.persistence.Converter;

@Converter
public class RawMessageStatusConverter extends CodeEnumConverter<RawMessageStatus> {

    public RawMessageStatusConverter() {
        super(RawMessageStatus.class);
    }
}
