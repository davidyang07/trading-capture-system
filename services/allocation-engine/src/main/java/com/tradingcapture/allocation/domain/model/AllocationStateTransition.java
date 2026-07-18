package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.AllocationStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One append-only row of an allocation's ADR-0006 state-transition history. Written on every
 * accepted {@link Allocation#transitionTo} and never updated or deleted, so the full lifecycle
 * (including which attempt drove a transition) stays auditable after crashes and retries.
 */
@Entity
@Table(name = "allocation_state_transitions")
public class AllocationStateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Convert(converter = AllocationStatusConverter.class)
    @Column(name = "from_status", length = 30, nullable = false, updatable = false)
    private AllocationStatus fromStatus;

    @Convert(converter = AllocationStatusConverter.class)
    @Column(name = "to_status", length = 30, nullable = false, updatable = false)
    private AllocationStatus toStatus;

    @Column(name = "attempt_number", updatable = false)
    private Integer attemptNumber;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AllocationStateTransition() {
        // JPA
    }

    public AllocationStateTransition(int seq, AllocationStatus fromStatus, AllocationStatus toStatus,
                                     Integer attemptNumber, String reason, Instant now) {
        this.seq = seq;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.attemptNumber = attemptNumber;
        this.reason = reason;
        this.occurredAt = now;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public AllocationStatus getFromStatus() {
        return fromStatus;
    }

    public AllocationStatus getToStatus() {
        return toStatus;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
