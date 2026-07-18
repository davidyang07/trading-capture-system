package com.tradingcapture.allocation.domain.model.converter;

import com.tradingcapture.allocation.domain.model.AssignmentStatus;
import jakarta.persistence.Converter;

@Converter
public class AssignmentStatusConverter extends CodeEnumConverter<AssignmentStatus> {

    public AssignmentStatusConverter() {
        super(AssignmentStatus.class);
    }
}
