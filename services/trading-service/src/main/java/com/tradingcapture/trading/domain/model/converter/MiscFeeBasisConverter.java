package com.tradingcapture.trading.domain.model.converter;

import com.tradingcapture.trading.domain.model.MiscFeeBasis;
import jakarta.persistence.Converter;

@Converter
public class MiscFeeBasisConverter extends CodeEnumConverter<MiscFeeBasis> {

    public MiscFeeBasisConverter() {
        super(MiscFeeBasis::code, MiscFeeBasis::fromCode);
    }
}
