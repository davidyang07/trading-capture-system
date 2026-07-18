package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One NoMiscFees group entry. In FIX 4.4 AllocationInstruction the fee group nests inside
 * NoAllocs, so every fee row belongs to an {@link AllocationAccount}.
 */
@Entity
@Table(name = "allocation_misc_fees")
public class AllocationMiscFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Column(name = "misc_fee_amt", precision = 19, scale = 4, updatable = false)
    private BigDecimal miscFeeAmt;

    @Column(name = "misc_fee_curr", length = 3, updatable = false)
    private String miscFeeCurr;

    @Column(name = "misc_fee_type", length = 3, updatable = false)
    private String miscFeeType;

    @Column(name = "misc_fee_basis", length = 2, updatable = false)
    private String miscFeeBasis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AllocationMiscFee() {
        // JPA
    }

    public AllocationMiscFee(int seq, BigDecimal miscFeeAmt, String miscFeeCurr,
                             String miscFeeType, String miscFeeBasis, Instant now) {
        this.seq = seq;
        this.miscFeeAmt = miscFeeAmt;
        this.miscFeeCurr = miscFeeCurr;
        this.miscFeeType = miscFeeType;
        this.miscFeeBasis = miscFeeBasis;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public BigDecimal getMiscFeeAmt() {
        return miscFeeAmt;
    }

    public String getMiscFeeCurr() {
        return miscFeeCurr;
    }

    public String getMiscFeeType() {
        return miscFeeType;
    }

    public String getMiscFeeBasis() {
        return miscFeeBasis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
