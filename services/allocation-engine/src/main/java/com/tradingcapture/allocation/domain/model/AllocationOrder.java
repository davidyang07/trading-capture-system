package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One NoOrders group entry of an AllocationInstruction, immutable, in wire order (seq). */
@Entity
@Table(name = "allocation_orders")
public class AllocationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Column(name = "cl_ord_id", length = 64, updatable = false)
    private String clOrdId;

    @Column(name = "order_id", length = 64, updatable = false)
    private String orderId;

    @Column(name = "order_qty", precision = 19, scale = 4, updatable = false)
    private BigDecimal orderQty;

    @Column(name = "order_avg_px", precision = 19, scale = 8, updatable = false)
    private BigDecimal orderAvgPx;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AllocationOrder() {
        // JPA
    }

    public AllocationOrder(int seq, String clOrdId, String orderId,
                           BigDecimal orderQty, BigDecimal orderAvgPx, Instant now) {
        this.seq = seq;
        this.clOrdId = clOrdId;
        this.orderId = orderId;
        this.orderQty = orderQty;
        this.orderAvgPx = orderAvgPx;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public String getOrderId() {
        return orderId;
    }

    public BigDecimal getOrderQty() {
        return orderQty;
    }

    public BigDecimal getOrderAvgPx() {
        return orderAvgPx;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
