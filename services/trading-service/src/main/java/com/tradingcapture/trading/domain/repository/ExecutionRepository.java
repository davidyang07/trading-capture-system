package com.tradingcapture.trading.domain.repository;

import com.tradingcapture.trading.domain.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
