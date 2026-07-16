package com.tradingcapture.account.domain.model;

import com.tradingcapture.account.domain.model.converter.SecurityDirectionConverter;
import com.tradingcapture.account.domain.model.converter.SecurityTransactionTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable security ledger entry. Rows are only ever appended — the position for a
 * (account, instrument) pair is {@code Σ RECEIPT.quantity − Σ DELIVERY.quantity}. Quantities
 * are always strictly positive; direction carries the sign.
 */
@Entity
@Table(name = "security_transactions")
public class SecurityTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "transaction_ref", length = 64, nullable = false)
    private String transactionRef;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Convert(converter = SecurityDirectionConverter.class)
    @Column(name = "direction", length = 10, nullable = false)
    private SecurityDirection direction;

    @Convert(converter = SecurityTransactionTypeConverter.class)
    @Column(name = "transaction_type", length = 20, nullable = false)
    private SecurityTransactionType transactionType;

    @Column(name = "instrument_id", length = 64, nullable = false)
    private String instrumentId;

    @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "external_ref", length = 64)
    private String externalRef;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected SecurityTransaction() {
        // JPA only
    }

    public SecurityTransaction(String transactionRef,
                               String accountId,
                               SecurityDirection direction,
                               SecurityTransactionType transactionType,
                               String instrumentId,
                               BigDecimal quantity,
                               String externalRef,
                               String idempotencyKey,
                               String description,
                               Instant postedAt) {
        this.transactionRef = transactionRef;
        this.accountId = accountId;
        this.direction = direction;
        this.transactionType = transactionType;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.externalRef = externalRef;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.postedAt = postedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getAccountId() {
        return accountId;
    }

    public SecurityDirection getDirection() {
        return direction;
    }

    public SecurityTransactionType getTransactionType() {
        return transactionType;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
