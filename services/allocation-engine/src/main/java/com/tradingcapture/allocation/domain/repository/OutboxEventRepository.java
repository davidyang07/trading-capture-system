package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Poller scan (ADR-0005): oldest unpublished rows first, bounded batch. */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);
}
