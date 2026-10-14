package com.tradingcapture.allocation.domain.model;

import com.tradingcapture.allocation.domain.model.converter.AllocTransTypeConverter;
import com.tradingcapture.allocation.domain.model.converter.AllocationStatusConverter;
import com.tradingcapture.allocation.domain.model.converter.OrchestrationFailureCategoryConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One parsed FIX AllocationInstruction (35=J) with its repeating groups. Created only when
 * the business identity (allocId, allocTransType) parsed; the persisted {@code status}
 * column is the ADR-0006 state machine. Parsed field values are immutable after insert —
 * only the state column (and its bookkeeping pair) transitions.
 */
@Entity
@Table(name = "allocations")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "alloc_id", length = 64, nullable = false, updatable = false)
    private String allocId;

    @Convert(converter = AllocTransTypeConverter.class)
    @Column(name = "alloc_trans_type", length = 2, nullable = false, updatable = false)
    private AllocTransType allocTransType;

    @Column(name = "alloc_type", length = 3, updatable = false)
    private String allocType;

    @Column(name = "alloc_no_orders_type", length = 2, updatable = false)
    private String allocNoOrdersType;

    @Column(name = "ref_alloc_id", length = 64, updatable = false)
    private String refAllocId;

    @Convert(converter = AllocationStatusConverter.class)
    @Column(name = "status", length = 30, nullable = false)
    private AllocationStatus status;

    @Column(name = "side", length = 2, updatable = false)
    private String side;

    @Column(name = "quantity", precision = 19, scale = 4, updatable = false)
    private BigDecimal quantity;

    @Column(name = "avg_px", precision = 19, scale = 8, updatable = false)
    private BigDecimal avgPx;

    @Column(name = "currency", length = 3, updatable = false)
    private String currency;

    @Column(name = "trade_date", updatable = false)
    private LocalDate tradeDate;

    @Column(name = "transact_time", updatable = false)
    private Instant transactTime;

    @Column(name = "settl_type", length = 2, updatable = false)
    private String settlType;

    @Column(name = "settl_date", updatable = false)
    private LocalDate settlDate;

    @Column(name = "net_money", precision = 19, scale = 4, updatable = false)
    private BigDecimal netMoney;

    @Column(name = "gross_trade_amt", precision = 19, scale = 4, updatable = false)
    private BigDecimal grossTradeAmt;

    @Column(name = "symbol", length = 32, updatable = false)
    private String symbol;

    @Column(name = "security_id", length = 64, updatable = false)
    private String securityId;

    @Column(name = "security_id_source", length = 2, updatable = false)
    private String securityIdSource;

    @Column(name = "security_exchange", length = 10, updatable = false)
    private String securityExchange;

    @Column(name = "raw_message_id", nullable = false, updatable = false)
    private Long rawMessageId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationOrder> orders = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationExecution> executions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationAccount> accounts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // --- Phase 8 orchestration bookkeeping (ADR-0006: retryability is bookkeeping, not state) ---

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Convert(converter = OrchestrationFailureCategoryConverter.class)
    @Column(name = "failure_category", length = 40)
    private OrchestrationFailureCategory failureCategory;

    @Column(name = "failure_summary", length = 500)
    private String failureSummary;

    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("seq ASC")
    private List<AllocationStateTransition> transitions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("assignedAt ASC, id ASC")
    private List<AllocationAssignment> assignments = new ArrayList<>();

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    protected Allocation() {
        // JPA
    }

    public Allocation(String allocId, AllocTransType allocTransType, AllocationStatus initialStatus,
                      Long rawMessageId, Instant now) {
        if (initialStatus != AllocationStatus.RECEIVED && initialStatus != AllocationStatus.INVALID) {
            throw new IllegalArgumentException(
                    "Ingestion creates allocations only in RECEIVED or INVALID, not " + initialStatus);
        }
        this.allocId = allocId;
        this.allocTransType = allocTransType;
        this.status = initialStatus;
        this.rawMessageId = rawMessageId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- parsed-field setters used once during construction (rows are immutable after insert) ---

    public void setInstructionFields(String allocType, String allocNoOrdersType, String refAllocId,
                                     String side, BigDecimal quantity, BigDecimal avgPx, String currency,
                                     LocalDate tradeDate, Instant transactTime, String settlType,
                                     LocalDate settlDate, BigDecimal netMoney, BigDecimal grossTradeAmt) {
        this.allocType = allocType;
        this.allocNoOrdersType = allocNoOrdersType;
        this.refAllocId = refAllocId;
        this.side = side;
        this.quantity = quantity;
        this.avgPx = avgPx;
        this.currency = currency;
        this.tradeDate = tradeDate;
        this.transactTime = transactTime;
        this.settlType = settlType;
        this.settlDate = settlDate;
        this.netMoney = netMoney;
        this.grossTradeAmt = grossTradeAmt;
    }

    public void setInstrumentFields(String symbol, String securityId, String securityIdSource,
                                    String securityExchange) {
        this.symbol = symbol;
        this.securityId = securityId;
        this.securityIdSource = securityIdSource;
        this.securityExchange = securityExchange;
    }

    public void addOrder(AllocationOrder order) {
        orders.add(order);
    }

    public void addExecution(AllocationExecution execution) {
        executions.add(execution);
    }

    public void addAccount(AllocationAccount account) {
        accounts.add(account);
    }

    /**
     * Move to {@code next} if the ADR-0006 machine permits it, stamping {@code updatedAt} and
     * appending an append-only transition-history row. Phase 8 orchestration is the only caller;
     * the transition is validated against {@link AllocationStateMachine} so an illegal move
     * (including any move out of a terminal state) throws {@link IllegalAllocationTransitionException}
     * <em>before</em> any state or history is mutated.
     */
    public void transitionTo(AllocationStatus next, Instant now) {
        transitionTo(next, null, now);
    }

    /** As {@link #transitionTo(AllocationStatus, Instant)}, recording a safe {@code reason} in history. */
    public void transitionTo(AllocationStatus next, String reason, Instant now) {
        AllocationStateMachine.assertLegal(this.status, next);
        AllocationStatus from = this.status;
        this.status = next;
        this.updatedAt = now;
        this.transitions.add(new AllocationStateTransition(
                transitions.size() + 1, from, next,
                attemptCount > 0 ? attemptCount : null, truncate(reason, 255), now));
    }

    // --- attempt / retry / failure bookkeeping ------------------------------------------------

    /** Count one orchestration attempt (before the remote work of that attempt runs). */
    public void recordAttempt(Instant now) {
        this.attemptCount += 1;
        this.lastAttemptAt = now;
        this.updatedAt = now;
    }

    public boolean attemptsExhausted() {
        return attemptCount >= maxAttempts;
    }

    /**
     * A retryable dependency/infrastructure failure: keep the current working state and schedule
     * the next attempt. Never call with a non-retryable category.
     */
    public void recordRetryableFailure(OrchestrationFailureCategory category, String summary,
                                       Instant nextAttemptAt, Instant now) {
        if (category == null || !category.isRetryable()) {
            throw new IllegalArgumentException(category + " is not a retryable failure category");
        }
        this.failureCategory = category;
        this.failureSummary = truncate(summary, 500);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    /** A previously-recorded transient failure resolved on a later attempt — clear the bookkeeping. */
    public void clearTransientFailure(Instant now) {
        this.failureCategory = null;
        this.failureSummary = null;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** Terminal business rejection (never retried) — transitions to REJECTED. */
    public void reject(String summary, Instant now) {
        this.failureCategory = OrchestrationFailureCategory.BUSINESS_REJECTION;
        this.failureSummary = truncate(summary, 500);
        this.nextAttemptAt = null;
        this.completedAt = now;
        transitionTo(AllocationStatus.REJECTED, summary, now);
    }

    /** Terminal non-business failure (retries exhausted or non-retryable) — transitions to FAILED. */
    public void failTerminally(OrchestrationFailureCategory category, String summary, Instant now) {
        if (category == OrchestrationFailureCategory.BUSINESS_REJECTION) {
            throw new IllegalArgumentException("business rejections use reject(), not failTerminally()");
        }
        this.failureCategory = category;
        this.failureSummary = truncate(summary, 500);
        this.nextAttemptAt = null;
        this.completedAt = now;
        transitionTo(AllocationStatus.FAILED, summary, now);
    }

    /** Reached a successful terminal state (New/Replace settlement, or a finalized Cancel). */
    public void markCompleted(Instant now) {
        this.failureCategory = null;
        this.failureSummary = null;
        this.nextAttemptAt = null;
        this.completedAt = now;
        transitionTo(AllocationStatus.COMPLETED, now);
    }

    /**
     * Retire this live allocation because {@code successor} (a Replace or Cancel referencing it)
     * released its executions: {@code -> REPLACED} or {@code -> CANCELLED}. Called in the
     * successor's release transaction, so supersession and the release commit together.
     */
    public void markSupersededBy(Allocation successor, Instant now) {
        AllocationStatus next = successor.getAllocTransType() == AllocTransType.CANCEL
                ? AllocationStatus.CANCELLED
                : AllocationStatus.REPLACED;
        String verb = next == AllocationStatus.CANCELLED ? "cancelled by " : "replaced by ";
        transitionTo(next, verb + successor.getAllocId(), now);
        this.nextAttemptAt = null;
        if (this.completedAt == null) {
            this.completedAt = now;
        }
        releaseClaim(now);
    }

    // --- orchestration claim (durable visibility alongside the row lock, ADR-0006 recovery) ----

    public void claim(String worker, Instant now) {
        this.claimedBy = worker;
        this.claimedAt = now;
        this.updatedAt = now;
    }

    public void releaseClaim(Instant now) {
        this.claimedBy = null;
        this.claimedAt = null;
        this.updatedAt = now;
    }

    // --- execution assignments ----------------------------------------------------------------

    /**
     * Bind {@code quantity} of one trading-service execution to this allocation. An execution may
     * be assigned to a given allocation at most once (matches the DB unique key); the quantity
     * must be strictly positive.
     */
    public AllocationAssignment assignExecution(String executionId, BigDecimal quantity, Instant now) {
        boolean duplicate = assignments.stream()
                .anyMatch(a -> a.getExecutionId().equals(executionId));
        if (duplicate) {
            throw new IllegalStateException(
                    "execution " + executionId + " is already assigned to allocation " + allocId);
        }
        AllocationAssignment assignment = new AllocationAssignment(executionId, quantity, now);
        assignments.add(assignment);
        this.updatedAt = now;
        return assignment;
    }

    /** Release every still-assigned execution exactly once; returns the released execution ids. */
    public List<String> releaseAllAssignments(Instant now) {
        List<String> released = new ArrayList<>();
        for (AllocationAssignment assignment : assignments) {
            if (!assignment.isReleased()) {
                assignment.release(now);
                released.add(assignment.getExecutionId());
            }
        }
        if (!released.isEmpty()) {
            this.updatedAt = now;
        }
        return released;
    }

    /** Net quantity still allocated (assigned minus released) across all assignments. */
    public BigDecimal netAllocatedQuantity() {
        return assignments.stream()
                .filter(assignment -> !assignment.isReleased())
                .map(AllocationAssignment::getAllocatedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public Long getId() {
        return id;
    }

    public String getAllocId() {
        return allocId;
    }

    public AllocTransType getAllocTransType() {
        return allocTransType;
    }

    public String getAllocType() {
        return allocType;
    }

    public String getAllocNoOrdersType() {
        return allocNoOrdersType;
    }

    public String getRefAllocId() {
        return refAllocId;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgPx() {
        return avgPx;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public Instant getTransactTime() {
        return transactTime;
    }

    public String getSettlType() {
        return settlType;
    }

    public LocalDate getSettlDate() {
        return settlDate;
    }

    public BigDecimal getNetMoney() {
        return netMoney;
    }

    public BigDecimal getGrossTradeAmt() {
        return grossTradeAmt;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSecurityId() {
        return securityId;
    }

    public String getSecurityIdSource() {
        return securityIdSource;
    }

    public String getSecurityExchange() {
        return securityExchange;
    }

    public Long getRawMessageId() {
        return rawMessageId;
    }

    public List<AllocationOrder> getOrders() {
        return orders;
    }

    public List<AllocationExecution> getExecutions() {
        return executions;
    }

    public List<AllocationAccount> getAccounts() {
        return accounts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public OrchestrationFailureCategory getFailureCategory() {
        return failureCategory;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public List<AllocationStateTransition> getTransitions() {
        return transitions;
    }

    public List<AllocationAssignment> getAssignments() {
        return assignments;
    }
}
