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
     * Loads the account for a ledger-mutating transaction under a row lock ({@code SELECT … FOR
     * UPDATE}): concurrent postings to one account queue behind each other, so each balance check
     * sees every earlier posting (no double-spend) and both succeed when both are valid.
     *
     * <p>It used to be an optimistic force-increment. Inserting a ledger row takes a shared lock
     * on the account (the foreign-key check), and both transactions then tried to upgrade it to
     * bump the version: InnoDB killed one as a deadlock, which surfaced as a 500.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") String accountId);
}
