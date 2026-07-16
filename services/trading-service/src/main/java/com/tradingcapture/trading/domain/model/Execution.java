package com.tradingcapture.trading.domain.model;

import com.tradingcapture.trading.domain.model.converter.AllocationStateConverter;
import com.tradingcapture.trading.domain.model.converter.ExecutionTypeConverter;
import com.tradingcapture.trading.domain.model.converter.OrderStatusConverter;
import com.tradingcapture.trading.domain.model.converter.SettleTypeConverter;
import com.tradingcapture.trading.domain.model.converter.SideConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One immutable execution event. Trade history is append-only: corrections and cancellations
 * are new rows linked via {@code execRefId} (direct target) and {@code rootExecutionId}
 * (chain start, resolved at write time; TRADE rows are their own root). The only mutable
 * state is the allocation pair ({@code allocationState}/{@code allocationId}), which is
 * operational state owned by the allocation machine API and guarded by {@code @Version}.
 *
 * <p>The cumQty/leavesQty/avgPx/ordStatus columns are the server-derived aggregate snapshot
 * as of this event — clients never supply them (the reference implementation trusted the
 * client here; this design deliberately does not).</p>
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @Column(name = "internal_id", length = 50, nullable = false)
    private String internalId;

    @Column(name = "exec_id", length = 64, nullable = false)
    private String execId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Convert(converter = ExecutionTypeConverter.class)
    @Column(name = "exec_type", length = 1, nullable = false)
    private ExecutionType execType;

    @Column(name = "exec_ref_id", length = 50)
    private String execRefId;

    @Column(name = "root_execution_id", length = 50, nullable = false)
    private String rootExecutionId;

    @Convert(converter = OrderStatusConverter.class)
    @Column(name = "ord_status", length = 2, nullable = false)
    private OrderStatus ordStatus;

    @Convert(converter = SideConverter.class)
    @Column(name = "side", length = 2, nullable = false)
    private Side side;

    @Column(name = "last_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal lastQty;

    @Column(name = "last_px", precision = 19, scale = 8)
    private BigDecimal lastPx;

    @Column(name = "cum_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal cumQty;

    @Column(name = "leaves_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal leavesQty;

    @Column(name = "avg_px", precision = 19, scale = 8)
    private BigDecimal avgPx;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "transact_time", nullable = false)
    private Instant transactTime;

    @Convert(converter = SettleTypeConverter.class)
    @Column(name = "settle_type", length = 2, nullable = false)
    private SettleType settleType;

    @Column(name = "settle_date", nullable = false)
    private LocalDate settleDate;

    @Column(name = "text", length = 500)
    private String text;

    @Convert(converter = AllocationStateConverter.class)
    @Column(name = "allocation_state", length = 20, nullable = false)
    private AllocationState allocationState;

    @Column(name = "allocation_id", length = 64)
    private String allocationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<ExecutionMiscFee> miscFees = new ArrayList<>();

    protected Execution() {
        // JPA only
    }

    private Execution(Builder builder) {
        this.internalId = builder.internalId;
        this.execId = builder.execId;
        this.order = builder.order;
        this.execType = builder.execType;
        this.execRefId = builder.execRefId;
        this.rootExecutionId = builder.rootExecutionId;
        this.ordStatus = builder.ordStatus;
        this.side = builder.side;
        this.lastQty = builder.lastQty;
        this.lastPx = builder.lastPx;
        this.cumQty = builder.cumQty;
        this.leavesQty = builder.leavesQty;
        this.avgPx = builder.avgPx;
        this.currency = builder.currency;
        this.tradeDate = builder.tradeDate;
        this.transactTime = builder.transactTime;
        this.settleType = builder.settleType;
        this.settleDate = builder.settleDate;
        this.text = builder.text;
        this.allocationState = AllocationState.UNALLOCATED;
        this.allocationId = null;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Binds this execution to an allocation; legality is validated by the service layer. */
    public void allocate(String allocationId, Instant now) {
        this.allocationState = AllocationState.ALLOCATED;
        this.allocationId = allocationId;
        this.updatedAt = now;
    }

    /** Returns this execution to the free pool (Replace/Cancel release semantics). */
    public void releaseAllocation(Instant now) {
        this.allocationState = AllocationState.UNALLOCATED;
        this.allocationId = null;
        this.updatedAt = now;
    }

    public boolean isCancellation() {
        return execType == ExecutionType.TRADE_CANCEL;
    }

    public boolean isAllocated() {
        return allocationState == AllocationState.ALLOCATED;
    }

    public void addMiscFee(ExecutionMiscFee fee) {
        miscFees.add(fee);
    }

    public String getInternalId() {
        return internalId;
    }

    public String getExecId() {
        return execId;
    }

    public Order getOrder() {
        return order;
    }

    public ExecutionType getExecType() {
        return execType;
    }

    public String getExecRefId() {
        return execRefId;
    }

    public String getRootExecutionId() {
        return rootExecutionId;
    }

    public OrderStatus getOrdStatus() {
        return ordStatus;
    }

    public Side getSide() {
        return side;
    }

    public BigDecimal getLastQty() {
        return lastQty;
    }

    public BigDecimal getLastPx() {
        return lastPx;
    }

    public BigDecimal getCumQty() {
        return cumQty;
    }

    public BigDecimal getLeavesQty() {
        return leavesQty;
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

    public SettleType getSettleType() {
        return settleType;
    }

    public LocalDate getSettleDate() {
        return settleDate;
    }

    public String getText() {
        return text;
    }

    public AllocationState getAllocationState() {
        return allocationState;
    }

    public String getAllocationId() {
        return allocationId;
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

    public List<ExecutionMiscFee> getMiscFees() {
        return List.copyOf(miscFees);
    }

    /** Hand-written builder (no Lombok, ADR-0014); required fields are validated on build. */
    public static final class Builder {
        private String internalId;
        private String execId;
        private Order order;
        private ExecutionType execType;
        private String execRefId;
        private String rootExecutionId;
        private OrderStatus ordStatus;
        private Side side;
        private BigDecimal lastQty;
        private BigDecimal lastPx;
        private BigDecimal cumQty;
        private BigDecimal leavesQty;
        private BigDecimal avgPx;
        private String currency;
        private LocalDate tradeDate;
        private Instant transactTime;
        private SettleType settleType;
        private LocalDate settleDate;
        private String text;
        private Instant createdAt;

        private Builder() {
        }

        public Builder internalId(String internalId) {
            this.internalId = internalId;
            return this;
        }

        public Builder execId(String execId) {
            this.execId = execId;
            return this;
        }

        public Builder order(Order order) {
            this.order = order;
            return this;
        }

        public Builder execType(ExecutionType execType) {
            this.execType = execType;
            return this;
        }

        public Builder execRefId(String execRefId) {
            this.execRefId = execRefId;
            return this;
        }

        public Builder rootExecutionId(String rootExecutionId) {
            this.rootExecutionId = rootExecutionId;
            return this;
        }

        public Builder ordStatus(OrderStatus ordStatus) {
            this.ordStatus = ordStatus;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder lastQty(BigDecimal lastQty) {
            this.lastQty = lastQty;
            return this;
        }

        public Builder lastPx(BigDecimal lastPx) {
            this.lastPx = lastPx;
            return this;
        }

        public Builder cumQty(BigDecimal cumQty) {
            this.cumQty = cumQty;
            return this;
        }

        public Builder leavesQty(BigDecimal leavesQty) {
            this.leavesQty = leavesQty;
            return this;
        }

        public Builder avgPx(BigDecimal avgPx) {
            this.avgPx = avgPx;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder tradeDate(LocalDate tradeDate) {
            this.tradeDate = tradeDate;
            return this;
        }

        public Builder transactTime(Instant transactTime) {
            this.transactTime = transactTime;
            return this;
        }

        public Builder settleType(SettleType settleType) {
            this.settleType = settleType;
            return this;
        }

        public Builder settleDate(LocalDate settleDate) {
            this.settleDate = settleDate;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Execution build() {
            requireNonNull(internalId, "internalId");
            requireNonNull(execId, "execId");
            requireNonNull(order, "order");
            requireNonNull(execType, "execType");
            requireNonNull(rootExecutionId, "rootExecutionId");
            requireNonNull(ordStatus, "ordStatus");
            requireNonNull(side, "side");
            requireNonNull(lastQty, "lastQty");
            requireNonNull(cumQty, "cumQty");
            requireNonNull(leavesQty, "leavesQty");
            requireNonNull(currency, "currency");
            requireNonNull(tradeDate, "tradeDate");
            requireNonNull(transactTime, "transactTime");
            requireNonNull(settleType, "settleType");
            requireNonNull(settleDate, "settleDate");
            requireNonNull(createdAt, "createdAt");
            if ((execType == ExecutionType.TRADE) != (execRefId == null)) {
                throw new IllegalStateException(
                        "execRefId must be absent for TRADE and present for corrections/cancellations");
            }
            return new Execution(this);
        }

        private static void requireNonNull(Object value, String field) {
            if (value == null) {
                throw new IllegalStateException("Execution." + field + " is required");
            }
        }
    }
}
