package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.Instrument;
import com.tradingcapture.enrichment.domain.model.InstrumentType;
import org.springframework.data.jpa.domain.Specification;

/** Composable JPA Specifications for the instrument search endpoint (deterministic, indexed columns). */
public final class InstrumentSpecifications {

    private InstrumentSpecifications() {
    }

    public static Specification<Instrument> hasType(InstrumentType type) {
        return (root, query, cb) -> cb.equal(root.get("instrumentType"), type);
    }

    public static Specification<Instrument> hasExchange(String mic) {
        return (root, query, cb) -> cb.equal(root.get("exchangeMic"), mic);
    }

    public static Specification<Instrument> hasCurrency(String currency) {
        return (root, query, cb) -> cb.equal(root.get("currencyCode"), currency);
    }

    public static Specification<Instrument> hasStatus(ActivationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
