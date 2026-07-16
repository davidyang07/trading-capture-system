package com.tradingcapture.account.domain.repository;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    Page<CashTransaction> findByAccountId(String accountId, Pageable pageable);

    Optional<CashTransaction> findByAccountIdAndIdempotencyKey(String accountId, String idempotencyKey);

    /** Sum of one direction's amounts for a (account, currency); {@code null} when there are none. */
    @Query("""
            select sum(t.amount) from CashTransaction t
            where t.accountId = :accountId and t.currency = :currency and t.direction = :direction
            """)
    BigDecimal sumAmount(@Param("accountId") String accountId,
                         @Param("currency") String currency,
                         @Param("direction") CashDirection direction);

    @Query("select distinct t.currency from CashTransaction t where t.accountId = :accountId order by t.currency")
    List<String> findCurrencies(@Param("accountId") String accountId);
}
