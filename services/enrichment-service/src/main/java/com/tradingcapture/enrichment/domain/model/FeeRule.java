package com.tradingcapture.enrichment.domain.model;

import com.tradingcapture.enrichment.domain.model.converter.ActivationStatusConverter;
import com.tradingcapture.enrichment.domain.model.converter.FeeBasisConverter;
import com.tradingcapture.enrichment.domain.model.converter.FeeTypeConverter;
import com.tradingcapture.enrichment.domain.model.converter.InstrumentTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A miscellaneous fee rule. Its dimensions ({@code exchangeMic}, {@code instrumentType},
 * {@code currencyCode}) are optional filters — a null dimension means "applies to any" — so
 * later trading/allocation processing can select the applicable rules for a trade. This service
 * stores and serves the rules; it does not compute fees.
 */
@Entity
@Table(name = "fee_rules")
public class FeeRule {

    @Id
    @Column(name = "fee_rule_id", length = 64, nullable = false)
    private String feeRuleId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Convert(converter = FeeTypeConverter.class)
    @Column(name = "fee_type", length = 20, nullable = false)
    private FeeType feeType;

    @Convert(converter = FeeBasisConverter.class)
    @Column(name = "basis", length = 20, nullable = false)
    private FeeBasis basis;

    @Column(name = "rate", precision = 19, scale = 8, nullable = false)
    private BigDecimal rate;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "exchange_mic", length = 4)
    private String exchangeMic;

    @Convert(converter = InstrumentTypeConverter.class)
    @Column(name = "instrument_type", length = 20)
    private InstrumentType instrumentType;

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

    protected FeeRule() {
        // JPA only
    }

    public FeeRule(String feeRuleId,
                   String name,
                   FeeType feeType,
                   FeeBasis basis,
                   BigDecimal rate,
                   String currencyCode,
                   String exchangeMic,
                   InstrumentType instrumentType,
                   Instant createdAt) {
        this.feeRuleId = feeRuleId;
        this.name = name;
        this.feeType = feeType;
        this.basis = basis;
        this.rate = rate;
        this.currencyCode = currencyCode;
        this.exchangeMic = exchangeMic;
        this.instrumentType = instrumentType;
        this.status = ActivationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, FeeType feeType, FeeBasis basis, BigDecimal rate,
                       String currencyCode, String exchangeMic, InstrumentType instrumentType, Instant now) {
        this.name = name;
        this.feeType = feeType;
        this.basis = basis;
        this.rate = rate;
        this.currencyCode = currencyCode;
        this.exchangeMic = exchangeMic;
        this.instrumentType = instrumentType;
        this.updatedAt = now;
    }

    public void setStatus(ActivationStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public String getFeeRuleId() {
        return feeRuleId;
    }

    public String getName() {
        return name;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public FeeBasis getBasis() {
        return basis;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getExchangeMic() {
        return exchangeMic;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
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
}
