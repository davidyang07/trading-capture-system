package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.AllocTransTypeConverter;
import com.tradingcapture.allocation.domain.model.converter.AllocationStatusConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One parsed FIX AllocationInstruction (35=J) with its repeating groups. Created only when
 * the business identity (allocId, allocTransType) parsed; the persisted {@code status}
 * column is the ADR-0006 state machine. Parsed field values are immutable after insert —
 * only the state column (and its bookkeeping pair) transitions.
 */
@Entity
@Table(name = "allocations")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "alloc_id", length = 64, nullable = false, updatable = false)
    private String allocId;

    @Convert(converter = AllocTransTypeConverter.class)
    @Column(name = "alloc_trans_type", length = 2, nullable = false, updatable = false)
    private AllocTransType allocTransType;

    @Column(name = "alloc_type", length = 3, updatable = false)
    private String allocType;

    @Column(name = "alloc_no_orders_type", length = 2, updatable = false)
    private String allocNoOrdersType;

    @Column(name = "ref_alloc_id", length = 64, updatable = false)
    private String refAllocId;

    @Convert(converter = AllocationStatusConverter.class)
    @Column(name = "status", length = 30, nullable = false)
    private AllocationStatus status;

    @Column(name = "side", length = 2, updatable = false)
    private String side;

    @Column(name = "quantity", precision = 19, scale = 4, updatable = false)
    private BigDecimal quantity;

    @Column(name = "avg_px", precision = 19, scale = 8, updatable = false)
    private BigDecimal avgPx;

    @Column(name = "currency", length = 3, updatable = false)
    private String currency;

    @Column(name = "trade_date", updatable = false)
    private LocalDate tradeDate;

    @Column(name = "transact_time", updatable = false)
    private Instant transactTime;

    @Column(name = "settl_type", length = 2, updatable = false)
    private String settlType;

    @Column(name = "settl_date", updatable = false)
    private LocalDate settlDate;

    @Column(name = "net_money", precision = 19, scale = 4, updatable = false)
    private BigDecimal netMoney;

    @Column(name = "gross_trade_amt", precision = 19, scale = 4, updatable = false)
    private BigDecimal grossTradeAmt;

    @Column(name = "symbol", length = 32, updatable = false)
    private String symbol;

    @Column(name = "security_id", length = 64, updatable = false)
    private String securityId;

    @Column(name = "security_id_source", length = 2, updatable = false)
    private String securityIdSource;

    @Column(name = "security_exchange", length = 10, updatable = false)
    private String securityExchange;

    @Column(name = "raw_message_id", nullable = false, updatable = false)
    private Long rawMessageId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationOrder> orders = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationExecution> executions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationAccount> accounts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Allocation() {
        // JPA
    }

    public Allocation(String allocId, AllocTransType allocTransType, AllocationStatus initialStatus,
                      Long rawMessageId, Instant now) {
        if (initialStatus != AllocationStatus.RECEIVED && initialStatus != AllocationStatus.INVALID) {
            throw new IllegalArgumentException(
                    "Ingestion creates allocations only in RECEIVED or INVALID, not " + initialStatus);
        }
        this.allocId = allocId;
        this.allocTransType = allocTransType;
        this.status = initialStatus;
        this.rawMessageId = rawMessageId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- parsed-field setters used once during construction (rows are immutable after insert) ---

    public void setInstructionFields(String allocType, String allocNoOrdersType, String refAllocId,
                                     String side, BigDecimal quantity, BigDecimal avgPx, String currency,
                                     LocalDate tradeDate, Instant transactTime, String settlType,
                                     LocalDate settlDate, BigDecimal netMoney, BigDecimal grossTradeAmt) {
        this.allocType = allocType;
        this.allocNoOrdersType = allocNoOrdersType;
        this.refAllocId = refAllocId;
        this.side = side;
        this.quantity = quantity;
        this.avgPx = avgPx;
        this.currency = currency;
        this.tradeDate = tradeDate;
        this.transactTime = transactTime;
        this.settlType = settlType;
        this.settlDate = settlDate;
        this.netMoney = netMoney;
        this.grossTradeAmt = grossTradeAmt;
    }

    public void setInstrumentFields(String symbol, String securityId, String securityIdSource,
                                    String securityExchange) {
        this.symbol = symbol;
        this.securityId = securityId;
        this.securityIdSource = securityIdSource;
        this.securityExchange = securityExchange;
    }

    public void addOrder(AllocationOrder order) {
        orders.add(order);
    }

    public void addExecution(AllocationExecution execution) {
        executions.add(execution);
    }

    public void addAccount(AllocationAccount account) {
        accounts.add(account);
    }

    public Long getId() {
        return id;
    }

    public String getAllocId() {
        return allocId;
    }

    public AllocTransType getAllocTransType() {
        return allocTransType;
    }

    public String getAllocType() {
        return allocType;
    }

    public String getAllocNoOrdersType() {
        return allocNoOrdersType;
    }

    public String getRefAllocId() {
        return refAllocId;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgPx() {
        return avgPx;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public Instant getTransactTime() {
        return transactTime;
    }

    public String getSettlType() {
        return settlType;
    }

    public LocalDate getSettlDate() {
        return settlDate;
    }

    public BigDecimal getNetMoney() {
        return netMoney;
    }

    public BigDecimal getGrossTradeAmt() {
        return grossTradeAmt;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSecurityId() {
        return securityId;
    }

    public String getSecurityIdSource() {
        return securityIdSource;
    }

    public String getSecurityExchange() {
        return securityExchange;
    }

    public Long getRawMessageId() {
        return rawMessageId;
    }

    public List<AllocationOrder> getOrders() {
        return orders;
    }

    public List<AllocationExecution> getExecutions() {
        return executions;
    }

    public List<AllocationAccount> getAccounts() {
        return accounts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
