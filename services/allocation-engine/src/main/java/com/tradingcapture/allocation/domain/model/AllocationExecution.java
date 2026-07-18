package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One NoExecs group entry of an AllocationInstruction, immutable, in wire order (seq). */
@Entity
@Table(name = "allocation_executions")
public class AllocationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Column(name = "exec_id", length = 64, updatable = false)
    private String execId;

    @Column(name = "last_qty", precision = 19, scale = 4, updatable = false)
    private BigDecimal lastQty;

    @Column(name = "last_px", precision = 19, scale = 8, updatable = false)
    private BigDecimal lastPx;

    @Column(name = "last_capacity", length = 2, updatable = false)
    private String lastCapacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AllocationExecution() {
        // JPA
    }

    public AllocationExecution(int seq, String execId, BigDecimal lastQty,
                               BigDecimal lastPx, String lastCapacity, Instant now) {
        this.seq = seq;
        this.execId = execId;
        this.lastQty = lastQty;
        this.lastPx = lastPx;
        this.lastCapacity = lastCapacity;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public String getExecId() {
        return execId;
    }

    public BigDecimal getLastQty() {
        return lastQty;
    }

    public BigDecimal getLastPx() {
        return lastPx;
    }

    public String getLastCapacity() {
        return lastCapacity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
