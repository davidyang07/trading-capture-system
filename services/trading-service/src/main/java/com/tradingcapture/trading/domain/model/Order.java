package com.tradingcapture.trading.domain.model;

import com.tradingcapture.trading.domain.model.converter.OrderStatusConverter;
import com.tradingcapture.trading.domain.model.converter.OrderTypeConverter;
import com.tradingcapture.trading.domain.model.converter.PriceTypeConverter;
import com.tradingcapture.trading.domain.model.converter.SettleTypeConverter;
import com.tradingcapture.trading.domain.model.converter.SideConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An order owned by the authenticated subject that created it. Fill state (status, cumQty,
 * leavesQty) is denormalized here and recomputed transactionally by the execution flows;
 * {@code @Version} guards concurrent execution capture against lost updates.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id", length = 64, nullable = false)
    private String orderId;

    @Column(name = "cl_ord_id", length = 64)
    private String clOrdId;

    @Column(name = "owner_subject", length = 255, nullable = false)
    private String ownerSubject;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Column(name = "instrument_id", length = 64, nullable = false)
    private String instrumentId;

    @Convert(converter = OrderStatusConverter.class)
    @Column(name = "status", length = 2, nullable = false)
    private OrderStatus status;

    @Convert(converter = SideConverter.class)
    @Column(name = "side", length = 2, nullable = false)
    private Side side;

    @Convert(converter = OrderTypeConverter.class)
    @Column(name = "order_type", length = 2, nullable = false)
    private OrderType orderType;

    @Column(name = "price", precision = 19, scale = 8, nullable = false)
    private BigDecimal price;

    @Convert(converter = PriceTypeConverter.class)
    @Column(name = "price_type", length = 2, nullable = false)
    private PriceType priceType;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "cum_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal cumQty;

    @Column(name = "leaves_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal leavesQty;

    @Convert(converter = SettleTypeConverter.class)
    @Column(name = "settle_type", length = 2, nullable = false)
    private SettleType settleType;

    @Column(name = "settle_date", nullable = false)
    private LocalDate settleDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Order() {
        // JPA only
    }

    public Order(String orderId,
                 String clOrdId,
                 String ownerSubject,
                 String accountId,
                 String instrumentId,
                 Side side,
                 OrderType orderType,
                 BigDecimal price,
                 PriceType priceType,
                 String currency,
                 BigDecimal quantity,
                 SettleType settleType,
                 LocalDate settleDate,
                 Instant createdAt) {
        this.orderId = orderId;
        this.clOrdId = clOrdId;
        this.ownerSubject = ownerSubject;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.status = OrderStatus.NEW;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.priceType = priceType;
        this.currency = currency;
        this.quantity = quantity;
        this.cumQty = BigDecimal.ZERO;
        this.leavesQty = quantity;
        this.settleType = settleType;
        this.settleDate = settleDate;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Applies the aggregate fill state derived from the order's active executions. The status
     * is a pure function of cumQty: 0 = NEW, quantity = FILLED, in between = PARTIALLY_FILLED.
     */
    public void applyFillState(BigDecimal newCumQty, Instant now) {
        if (newCumQty.signum() < 0 || newCumQty.compareTo(quantity) > 0) {
            throw new IllegalStateException(
                    "Derived cumQty " + newCumQty + " outside [0, " + quantity + "] for order " + orderId);
        }
        this.cumQty = newCumQty;
        this.leavesQty = quantity.subtract(newCumQty);
        if (newCumQty.signum() == 0) {
            this.status = OrderStatus.NEW;
        } else if (newCumQty.compareTo(quantity) == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
        this.updatedAt = now;
    }

    /** Replaces the client-editable fields; callers must have verified NEW eligibility. */
    public void applyUpdate(String clOrdId,
                            String accountId,
                            String instrumentId,
                            Side side,
                            OrderType orderType,
                            BigDecimal price,
                            PriceType priceType,
                            String currency,
                            BigDecimal quantity,
                            SettleType settleType,
                            LocalDate settleDate,
                            Instant now) {
        this.clOrdId = clOrdId;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.priceType = priceType;
        this.currency = currency;
        this.quantity = quantity;
        this.leavesQty = quantity.subtract(this.cumQty);
        this.settleType = settleType;
        this.settleDate = settleDate;
        this.updatedAt = now;
    }

    public boolean isNew() {
        return status == OrderStatus.NEW;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isOwnedBy(String subject) {
        return ownerSubject.equals(subject);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getCumQty() {
        return cumQty;
    }

    public BigDecimal getLeavesQty() {
        return leavesQty;
    }

    public SettleType getSettleType() {
        return settleType;
    }

    public LocalDate getSettleDate() {
        return settleDate;
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
