package com.tradingcapture.account.domain.repository;

import com.tradingcapture.account.domain.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String>, JpaSpecificationExecutor<Account> {

    /**
     * Loads the account for a ledger-mutating transaction under an optimistic force-increment
     * lock: the account's {@code @Version} is bumped even though no account column changes, so
     * two concurrent transactions posting against the same account serialize — the second flush
     * fails with {@code OptimisticLockingFailureException} (→ 409) instead of both reading a
     * stale balance and double-spending.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") String accountId);
}
