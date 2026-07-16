package com.tradingcapture.enrichment.domain.model;

import com.tradingcapture.enrichment.domain.model.converter.ActivationStatusConverter;
import com.tradingcapture.enrichment.domain.model.converter.InstrumentTypeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A tradable instrument and its alternate identifiers. The identifier collection is owned by the
 * instrument (cascade + orphan removal) so a create/update persists identifiers atomically.
 * {@code status} governs whether the instrument is usable; reference data is edited by admins.
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @Column(name = "instrument_id", length = 64, nullable = false)
    private String instrumentId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Convert(converter = InstrumentTypeConverter.class)
    @Column(name = "instrument_type", length = 20, nullable = false)
    private InstrumentType instrumentType;

    @Column(name = "exchange_mic", length = 4, nullable = false)
    private String exchangeMic;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Convert(converter = ActivationStatusConverter.class)
    @Column(name = "status", length = 20, nullable = false)
    private ActivationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlternateIdentifier> alternateIdentifiers = new ArrayList<>();

    protected Instrument() {
        // JPA only
    }

    public Instrument(String instrumentId,
                      String name,
                      InstrumentType instrumentType,
                      String exchangeMic,
                      String currencyCode,
                      Instant createdAt) {
        this.instrumentId = instrumentId;
        this.name = name;
        this.instrumentType = instrumentType;
        this.exchangeMic = exchangeMic;
        this.currencyCode = currencyCode;
        this.status = ActivationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, InstrumentType instrumentType, String exchangeMic, String currencyCode, Instant now) {
        this.name = name;
        this.instrumentType = instrumentType;
        this.exchangeMic = exchangeMic;
        this.currencyCode = currencyCode;
        this.updatedAt = now;
    }

    public void setStatus(ActivationStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Adds an alternate identifier ({@code value} must already be normalized by the caller). */
    public void addIdentifier(IdentifierSource source, String value, Instant now) {
        alternateIdentifiers.add(new AlternateIdentifier(this, source, value, exchangeMic, now));
    }

    /** Removes all identifiers (orphan removal deletes the rows) — used when replacing them on update. */
    public void clearIdentifiers() {
        alternateIdentifiers.clear();
    }

    public boolean isActive() {
        return status == ActivationStatus.ACTIVE;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getName() {
        return name;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public String getExchangeMic() {
        return exchangeMic;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<AlternateIdentifier> getAlternateIdentifiers() {
        return List.copyOf(alternateIdentifiers);
    }
}
