package com.tradingcapture.allocation.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One NoAllocs group entry (allocation account) of an AllocationInstruction, immutable,
 * in wire order (seq). Owns its nested NoMiscFees entries.
 */
@Entity
@Table(name = "allocation_accounts")
public class AllocationAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Column(name = "alloc_account", length = 64, updatable = false)
    private String allocAccount;

    @Column(name = "alloc_qty", precision = 19, scale = 4, updatable = false)
    private BigDecimal allocQty;

    @Column(name = "alloc_price", precision = 19, scale = 8, updatable = false)
    private BigDecimal allocPrice;

    @Column(name = "alloc_avg_px", precision = 19, scale = 8, updatable = false)
    private BigDecimal allocAvgPx;

    @Column(name = "alloc_net_money", precision = 19, scale = 4, updatable = false)
    private BigDecimal allocNetMoney;

    @Column(name = "alloc_settl_currency", length = 3, updatable = false)
    private String allocSettlCurrency;

    @Column(name = "alloc_settl_curr_amt", precision = 19, scale = 4, updatable = false)
    private BigDecimal allocSettlCurrAmt;

    @Column(name = "alloc_text", length = 255, updatable = false)
    private String allocText;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_account_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationMiscFee> miscFees = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AllocationAccount() {
        // JPA
    }

    public AllocationAccount(int seq, String allocAccount, BigDecimal allocQty, BigDecimal allocPrice,
                             BigDecimal allocAvgPx, BigDecimal allocNetMoney, String allocSettlCurrency,
                             BigDecimal allocSettlCurrAmt, String allocText, Instant now) {
        this.seq = seq;
        this.allocAccount = allocAccount;
        this.allocQty = allocQty;
        this.allocPrice = allocPrice;
        this.allocAvgPx = allocAvgPx;
        this.allocNetMoney = allocNetMoney;
        this.allocSettlCurrency = allocSettlCurrency;
        this.allocSettlCurrAmt = allocSettlCurrAmt;
        this.allocText = allocText;
        this.createdAt = now;
    }

    public void addMiscFee(AllocationMiscFee fee) {
        miscFees.add(fee);
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public String getAllocAccount() {
        return allocAccount;
    }

    public BigDecimal getAllocQty() {
        return allocQty;
    }

    public BigDecimal getAllocPrice() {
        return allocPrice;
    }

    public BigDecimal getAllocAvgPx() {
        return allocAvgPx;
    }

    public BigDecimal getAllocNetMoney() {
        return allocNetMoney;
    }

    public String getAllocSettlCurrency() {
        return allocSettlCurrency;
    }

    public BigDecimal getAllocSettlCurrAmt() {
        return allocSettlCurrAmt;
    }

    public String getAllocText() {
        return allocText;
    }

    public List<AllocationMiscFee> getMiscFees() {
        return miscFees;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
