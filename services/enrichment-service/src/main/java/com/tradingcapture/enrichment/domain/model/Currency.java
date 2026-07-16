package com.tradingcapture.enrichment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** An ISO 4217 currency. */
@Entity
@Table(name = "currencies")
public class Currency {

    @Id
    @Column(name = "code", length = 3, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "minor_units", nullable = false)
    private int minorUnits;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Currency() {
        // JPA only
    }

    public Currency(String code, String name, int minorUnits, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.minorUnits = minorUnits;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, int minorUnits, Instant now) {
        this.name = name;
        this.minorUnits = minorUnits;
        this.updatedAt = now;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMinorUnits() {
        return minorUnits;
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
}
