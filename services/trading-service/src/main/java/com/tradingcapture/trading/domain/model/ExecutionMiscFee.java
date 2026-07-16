package com.tradingcapture.trading.domain.model;

import com.tradingcapture.trading.domain.model.converter.MiscFeeBasisConverter;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A miscellaneous fee attached to one immutable execution row. Fees are never updated;
 * a correction restates the fee set on its own new execution row.
 */
@Entity
@Table(name = "execution_misc_fees")
public class ExecutionMiscFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_internal_id", nullable = false)
    private Execution execution;

    @Column(name = "fee_type", length = 20, nullable = false)
    private String feeType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Convert(converter = MiscFeeBasisConverter.class)
    @Column(name = "basis", length = 1, nullable = false)
    private MiscFeeBasis basis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExecutionMiscFee() {
        // JPA only
    }

    public ExecutionMiscFee(Execution execution,
                            String feeType,
                            BigDecimal amount,
                            String currency,
                            MiscFeeBasis basis,
                            Instant createdAt) {
        this.execution = execution;
        this.feeType = feeType;
        this.amount = amount;
        this.currency = currency;
        this.basis = basis;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Execution getExecution() {
        return execution;
    }

    public String getFeeType() {
        return feeType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public MiscFeeBasis getBasis() {
        return basis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
