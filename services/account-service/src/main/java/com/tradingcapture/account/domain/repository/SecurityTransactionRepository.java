package com.tradingcapture.account.domain.repository;

import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SecurityTransactionRepository extends JpaRepository<SecurityTransaction, Long> {

    Page<SecurityTransaction> findByAccountId(String accountId, Pageable pageable);

    Optional<SecurityTransaction> findByAccountIdAndIdempotencyKey(String accountId, String idempotencyKey);

    /** Sum of one direction's quantities for a (account, instrument); {@code null} when there are none. */
    @Query("""
            select sum(t.quantity) from SecurityTransaction t
            where t.accountId = :accountId and t.instrumentId = :instrumentId and t.direction = :direction
            """)
    BigDecimal sumQuantity(@Param("accountId") String accountId,
                           @Param("instrumentId") String instrumentId,
                           @Param("direction") SecurityDirection direction);

    @Query("select distinct t.instrumentId from SecurityTransaction t where t.accountId = :accountId order by t.instrumentId")
    List<String> findInstruments(@Param("accountId") String accountId);
}
