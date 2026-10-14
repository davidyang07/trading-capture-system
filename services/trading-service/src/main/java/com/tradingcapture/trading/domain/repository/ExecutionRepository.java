package com.tradingcapture.trading.domain.repository;

import com.tradingcapture.trading.domain.model.Execution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, String>, JpaSpecificationExecutor<Execution> {

    List<Execution> findByOrderOrderIdOrderByCreatedAtAscInternalIdAsc(String orderId);

    boolean existsByOrderOrderId(String orderId);

    boolean existsByExecId(String execId);

    boolean existsByExecRefId(String execRefId);

    List<Execution> findByExecRefIdIn(Collection<String> execRefIds);

    List<Execution> findByExecIdIn(Collection<String> execIds);

    List<Execution> findByRootExecutionIdOrderByCreatedAtAscInternalIdAsc(String rootExecutionId);

    List<Execution> findByAllocationId(String allocationId);

    /**
     * Loads executions under a row write lock ({@code SELECT ... FOR UPDATE}) held until the
     * transaction ends, in id order so two lockers of overlapping sets cannot deadlock. Allocation
     * (which writes the execution row) and correction/cancellation (which only inserts a new row
     * referencing it) both lock the target first, so they serialize: whichever runs second sees
     * the first one's committed outcome before its eligibility checks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Execution e where e.internalId in :internalIds order by e.internalId")
    List<Execution> findAllByInternalIdInForUpdate(@Param("internalIds") Collection<String> internalIds);
}
