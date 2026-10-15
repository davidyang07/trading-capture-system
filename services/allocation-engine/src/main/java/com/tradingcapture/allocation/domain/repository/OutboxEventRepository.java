package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.OutboxEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Poller scan (ADR-0005): oldest unpublished, unparked rows first, bounded batch. */
    List<OutboxEvent> findByPublishedAtIsNullAndParkedAtIsNullOrderByIdAsc(Limit limit);

    /** Rows still to publish — surfaced as the Phase 11 {@code tcs.allocation.outbox.backlog} gauge. */
    long countByPublishedAtIsNullAndParkedAtIsNull();

    /** Rows parked as unpublishable — surfaced as {@code tcs.allocation.outbox.parked}. */
    long countByParkedAtIsNotNull();
}
