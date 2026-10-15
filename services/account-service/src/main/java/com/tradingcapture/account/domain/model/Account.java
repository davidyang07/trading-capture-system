package com.tradingcapture.account.domain.model;

import com.tradingcapture.account.domain.model.converter.AccountStatusConverter;
import com.tradingcapture.account.domain.model.converter.AccountTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A financial account owned by the authenticated subject that created it (or an admin-nominated
 * owner). This is the only mutable aggregate in the service: its {@code status} changes through
 * the lifecycle and {@code @Version} guards it against lost updates. The ledger append flows lock
 * this row ({@code SELECT … FOR UPDATE}) so concurrent postings to one account queue behind each
 * other, which makes the sufficient-funds check race-safe.
 *
 * <p>Balances are never stored here; they are always summed from the immutable ledger rows
 * (ADR-0002, data-ownership balance semantics).</p>
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Column(name = "owner_subject", length = 255, nullable = false)
    private String ownerSubject;

    @Column(name = "account_name", length = 255, nullable = false)
    private String accountName;

    @Convert(converter = AccountTypeConverter.class)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType;

    @Convert(converter = AccountStatusConverter.class)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status;

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Account() {
        // JPA only
    }

    public Account(String accountId,
                   String ownerSubject,
                   String accountName,
                   AccountType accountType,
                   String baseCurrency,
                   Instant createdAt) {
        this.accountId = accountId;
        this.ownerSubject = ownerSubject;
        this.accountName = accountName;
        this.accountType = accountType;
        this.status = AccountStatus.PENDING;
        this.baseCurrency = baseCurrency;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Renames the account; callers must have verified ownership/eligibility. */
    public void rename(String accountName, Instant now) {
        this.accountName = accountName;
        this.updatedAt = now;
    }

    /** Applies a lifecycle transition; callers must have verified it is legal and permitted. */
    public void applyStatus(AccountStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal transition " + status + " -> " + target + " for account " + accountId);
        }
        this.status = target;
        this.updatedAt = now;
    }

    /** Records that a ledger row was appended, bumping the audit timestamp. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(String subject) {
        return ownerSubject.equals(subject);
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getBaseCurrency() {
        return baseCurrency;
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
