package com.tradingcapture.account.domain.model;

import com.tradingcapture.account.domain.model.converter.CashDirectionConverter;
import com.tradingcapture.account.domain.model.converter.CashTransactionTypeConverter;
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
 * An immutable cash ledger entry. Rows are only ever appended — the cash balance for a
 * (account, currency) pair is {@code Σ CREDIT.amount − Σ DEBIT.amount}. Amounts are always
 * strictly positive; direction carries the sign.
 */
@Entity
@Table(name = "cash_transactions")
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "transaction_ref", length = 64, nullable = false)
    private String transactionRef;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Convert(converter = CashDirectionConverter.class)
    @Column(name = "direction", length = 10, nullable = false)
    private CashDirection direction;

    @Convert(converter = CashTransactionTypeConverter.class)
    @Column(name = "transaction_type", length = 20, nullable = false)
    private CashTransactionType transactionType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "external_ref", length = 64)
    private String externalRef;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected CashTransaction() {
        // JPA only
    }

    public CashTransaction(String transactionRef,
                           String accountId,
                           CashDirection direction,
                           CashTransactionType transactionType,
                           BigDecimal amount,
                           String currency,
                           String externalRef,
                           String idempotencyKey,
                           String description,
                           Instant postedAt) {
        this.transactionRef = transactionRef;
        this.accountId = accountId;
        this.direction = direction;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
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

    public CashDirection getDirection() {
        return direction;
    }

    public CashTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
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
