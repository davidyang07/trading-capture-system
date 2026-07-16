package com.tradingcapture.enrichment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** A settlement convention (e.g. {@code T_PLUS_2}) with its standard settlement-day count. */
@Entity
@Table(name = "settlement_options")
public class SettlementOption {

    @Id
    @Column(name = "code", length = 16, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "settlement_days", nullable = false)
    private int settlementDays;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected SettlementOption() {
        // JPA only
    }

    public SettlementOption(String code, String name, int settlementDays, String description, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.settlementDays = settlementDays;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, int settlementDays, String description, Instant now) {
        this.name = name;
        this.settlementDays = settlementDays;
        this.description = description;
        this.updatedAt = now;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSettlementDays() {
        return settlementDays;
    }

    public String getDescription() {
        return description;
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
