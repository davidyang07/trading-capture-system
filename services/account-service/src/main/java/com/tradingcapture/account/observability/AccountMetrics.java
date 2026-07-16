package com.tradingcapture.account.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Domain counters (Micrometer). Exposure over Prometheus arrives in Phase 11; the meters are
 * registered now so the behavior is measurable and the wiring is proven.
 */
@Component
public class AccountMetrics {

    private final Counter accountsCreated;
    private final Counter accountsStatusChanged;
    private final Counter cashPosted;
    private final Counter securityPosted;
    private final Counter insufficientFunds;
    private final Counter insufficientSecurities;
    private final Counter idempotentReplays;

    public AccountMetrics(MeterRegistry registry) {
        this.accountsCreated = Counter.builder("tcs.account.accounts.created")
                .description("Accounts created").register(registry);
        this.accountsStatusChanged = Counter.builder("tcs.account.accounts.status_changed")
                .description("Account lifecycle transitions applied").register(registry);
        this.cashPosted = Counter.builder("tcs.account.cash.posted")
                .description("Cash ledger rows appended").register(registry);
        this.securityPosted = Counter.builder("tcs.account.security.posted")
                .description("Security ledger rows appended").register(registry);
        this.insufficientFunds = Counter.builder("tcs.account.cash.insufficient")
                .description("Cash debits rejected for insufficient funds").register(registry);
        this.insufficientSecurities = Counter.builder("tcs.account.security.insufficient")
                .description("Security deliveries rejected for insufficient position").register(registry);
        this.idempotentReplays = Counter.builder("tcs.account.idempotent.replays")
                .description("Ledger posts recognized as idempotent replays").register(registry);
    }

    public void accountCreated() {
        accountsCreated.increment();
    }

    public void accountStatusChanged() {
        accountsStatusChanged.increment();
    }

    public void cashPosted() {
        cashPosted.increment();
    }

    public void securityPosted() {
        securityPosted.increment();
    }

    public void insufficientFunds() {
        insufficientFunds.increment();
    }

    public void insufficientSecurities() {
        insufficientSecurities.increment();
    }

    public void idempotentReplay() {
        idempotentReplays.increment();
    }
}
