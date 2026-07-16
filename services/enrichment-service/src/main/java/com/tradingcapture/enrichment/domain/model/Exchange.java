package com.tradingcapture.enrichment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** A trading venue, keyed by its ISO 10383 Market Identifier Code (MIC). */
@Entity
@Table(name = "exchanges")
public class Exchange {

    @Id
    @Column(name = "mic", length = 4, nullable = false)
    private String mic;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Exchange() {
        // JPA only
    }

    public Exchange(String mic, String name, String country, String timezone, Instant createdAt) {
        this.mic = mic;
        this.name = name;
        this.country = country;
        this.timezone = timezone;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, String country, String timezone, Instant now) {
        this.name = name;
        this.country = country;
        this.timezone = timezone;
        this.updatedAt = now;
    }

    public String getMic() {
        return mic;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getTimezone() {
        return timezone;
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
