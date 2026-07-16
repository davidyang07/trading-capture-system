package com.tradingcapture.enrichment.domain.model;

import com.tradingcapture.enrichment.domain.model.converter.IdentifierSourceConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An alternate identifier for an instrument (CUSIP/ISIN/SEDOL/EXCHANGE_SYMBOL). {@code value}
 * is stored normalized (trimmed, upper-cased). {@code exchangeMic} is copied from the owning
 * instrument so the DB can enforce per-exchange uniqueness for exchange symbols.
 */
@Entity
@Table(name = "instrument_alt_ids")
public class AlternateIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Convert(converter = IdentifierSourceConverter.class)
    @Column(name = "source", length = 20, nullable = false)
    private IdentifierSource source;

    @Column(name = "id_value", length = 64, nullable = false)
    private String value;

    @Column(name = "exchange_mic", length = 4, nullable = false)
    private String exchangeMic;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlternateIdentifier() {
        // JPA only
    }

    AlternateIdentifier(Instrument instrument, IdentifierSource source, String value, String exchangeMic, Instant createdAt) {
        this.instrument = instrument;
        this.source = source;
        this.value = value;
        this.exchangeMic = exchangeMic;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public IdentifierSource getSource() {
        return source;
    }

    public String getValue() {
        return value;
    }

    public String getExchangeMic() {
        return exchangeMic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
