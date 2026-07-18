package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.AssignmentStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A quantity of one trading-service execution (its internal execution id) bound to an allocation.
 * Created positive and {@code ASSIGNED}; a Replace/Cancel releases it exactly once (full quantity),
 * flipping it to {@code RELEASED}. Released rows are retained — release is a state change, never a
 * delete — and the released quantity can never be negative or exceed what was allocated (DB CHECKs
 * back these invariants up).
 */
@Entity
@Table(name = "allocation_assignments")
public class AllocationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "execution_id", length = 64, nullable = false, updatable = false)
    private String executionId;

    @Column(name = "allocated_qty", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal allocatedQty;

    @Column(name = "released_qty", precision = 19, scale = 4, nullable = false)
    private BigDecimal releasedQty;

    @Convert(converter = AssignmentStatusConverter.class)
    @Column(name = "status", length = 20, nullable = false)
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AllocationAssignment() {
        // JPA
    }

    public AllocationAssignment(String executionId, BigDecimal allocatedQty, Instant now) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("assignment executionId is required");
        }
        if (allocatedQty == null || allocatedQty.signum() <= 0) {
            throw new IllegalArgumentException("allocated quantity must be positive, was " + allocatedQty);
        }
        this.executionId = executionId;
        this.allocatedQty = allocatedQty;
        this.releasedQty = BigDecimal.ZERO.setScale(4);
        this.status = AssignmentStatus.ASSIGNED;
        this.assignedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Release the full allocated quantity back to trading-service. Exactly once: a second call on
     * an already-released assignment is a programming error and throws.
     */
    public void release(Instant now) {
        if (status == AssignmentStatus.RELEASED) {
            throw new IllegalStateException(
                    "assignment for execution " + executionId + " is already released");
        }
        this.releasedQty = this.allocatedQty;
        this.status = AssignmentStatus.RELEASED;
        this.releasedAt = now;
        this.updatedAt = now;
    }

    public boolean isReleased() {
        return status == AssignmentStatus.RELEASED;
    }

    public Long getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public BigDecimal getAllocatedQty() {
        return allocatedQty;
    }

    public BigDecimal getReleasedQty() {
        return releasedQty;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
