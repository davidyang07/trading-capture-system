package com.tradingcapture.account.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lifecycle transition rules — the single source of truth used by the service. */
class AccountLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private Account newAccount() {
        return new Account("ACC-1", "investor1", "Cash", AccountType.CASH, "USD", NOW);
    }

    @Test
    void newAccountStartsPending() {
        assertThat(newAccount().getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,ACTIVE,true",
            "PENDING,CLOSED,true",
            "PENDING,SUSPENDED,false",
            "ACTIVE,SUSPENDED,true",
            "ACTIVE,CLOSED,true",
            "ACTIVE,PENDING,false",
            "SUSPENDED,ACTIVE,true",
            "SUSPENDED,CLOSED,true",
            "SUSPENDED,PENDING,false",
            "CLOSED,ACTIVE,false",
            "CLOSED,SUSPENDED,false"
    })
    void transitionMatrixIsEnforced(AccountStatus from, AccountStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void applyStatusMovesThroughTheHappyPath() {
        Account account = newAccount();
        account.applyStatus(AccountStatus.ACTIVE, NOW);
        assertThat(account.isActive()).isTrue();
        account.applyStatus(AccountStatus.SUSPENDED, NOW);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        account.applyStatus(AccountStatus.CLOSED, NOW);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void applyStatusRejectsAnIllegalTransition() {
        Account account = newAccount(); // PENDING
        assertThatThrownBy(() -> account.applyStatus(AccountStatus.SUSPENDED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closedIsTerminal() {
        Account account = newAccount();
        account.applyStatus(AccountStatus.CLOSED, NOW);
        assertThat(account.getStatus().canTransitionTo(AccountStatus.ACTIVE)).isFalse();
    }
}
