package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    /** Business-duplicate lookup on the unique (allocId, allocTransType) key (ADR-0007). */
    Optional<Allocation> findByAllocIdAndAllocTransType(String allocId, AllocTransType allocTransType);
}
