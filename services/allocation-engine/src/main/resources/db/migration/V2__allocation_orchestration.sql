-- Trading Capture System - tcs_allocation schema (Phase 8: allocation orchestration).
-- Adds orchestration bookkeeping to `allocations` and two child tables. Flyway owns the
-- schema (ddl-auto=validate); this migration initializes cleanly on the V1 schema.
--
-- Type standards (ADR-0014): money & quantities DECIMAL(19,4); timestamps DATETIME(6) UTC;
-- enums as explicit string codes (JPA converters own the mapping).
--
-- Code domains added here (converters own the mapping):
--   allocations.failure_category (OrchestrationFailureCategory — Phase 8, distinct from the
--       raw_messages ingestion taxonomy):
--       BUSINESS_REJECTION DEPENDENCY_UNAVAILABLE TRANSIENT_INFRASTRUCTURE
--       TERMINAL_ORCHESTRATION INVARIANT_VIOLATION
--   allocation_assignments.status : ASSIGNED RELEASED
--
-- ADR-0006 decision restated: retryable-vs-terminal failure is bookkeeping (attempt_count,
-- next_attempt_at, failure_category), NOT new allocation states. The transition table is
-- unchanged; state history is captured in allocation_state_transitions.

-- Orchestration bookkeeping on the allocation aggregate.
ALTER TABLE allocations
    ADD COLUMN attempt_count    INT          NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts     INT          NOT NULL DEFAULT 5,
    ADD COLUMN last_attempt_at  DATETIME(6)  NULL,
    ADD COLUMN next_attempt_at  DATETIME(6)  NULL,
    ADD COLUMN completed_at     DATETIME(6)  NULL,
    ADD COLUMN failure_category VARCHAR(40)  NULL,
    ADD COLUMN failure_summary  VARCHAR(500) NULL,
    ADD COLUMN claimed_by       VARCHAR(64)  NULL,
    ADD COLUMN claimed_at       DATETIME(6)  NULL,
    ADD CONSTRAINT chk_allocations_attempt_count CHECK (attempt_count >= 0),
    ADD CONSTRAINT chk_allocations_max_attempts CHECK (max_attempts >= 1),
    ADD CONSTRAINT chk_allocations_failure_category CHECK
        (failure_category IN ('BUSINESS_REJECTION','DEPENDENCY_UNAVAILABLE',
                              'TRANSIENT_INFRASTRUCTURE','TERMINAL_ORCHESTRATION',
                              'INVARIANT_VIOLATION'));

-- Recovery-driver scan: eligible non-terminal work due for its next attempt
-- (WHERE status IN (working states) AND (next_attempt_at IS NULL OR next_attempt_at <= now)).
CREATE INDEX idx_allocations_next_attempt ON allocations (status, next_attempt_at);

-- Append-only ADR-0006 state-transition history. One row per accepted transition, ordered by
-- seq within an allocation; never updated or deleted (crash/retry audit trail).
CREATE TABLE allocation_state_transitions (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    allocation_id  BIGINT       NOT NULL,
    seq            INT          NOT NULL,
    from_status    VARCHAR(30)  NOT NULL,
    to_status      VARCHAR(30)  NOT NULL,
    attempt_number INT          NULL,
    reason         VARCHAR(255) NULL,
    occurred_at    DATETIME(6)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_alloc_transitions_seq UNIQUE (allocation_id, seq),
    CONSTRAINT fk_alloc_transitions_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id),
    CONSTRAINT chk_alloc_transitions_from CHECK
        (from_status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                         'EXECUTIONS_RELEASED','COMPLETED','FAILED')),
    CONSTRAINT chk_alloc_transitions_to CHECK
        (to_status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                       'EXECUTIONS_RELEASED','COMPLETED','FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_alloc_transitions_allocation ON allocation_state_transitions (allocation_id);

-- Allocation-to-execution assignments keyed by trading-service internal execution id. One row
-- per (allocation, execution); a Replace/Cancel releases the row exactly once (released rows are
-- retained, never deleted). The CHECKs back the domain invariants: allocated quantity strictly
-- positive, released quantity within [0, allocated].
CREATE TABLE allocation_assignments (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    allocation_id BIGINT        NOT NULL,
    execution_id  VARCHAR(64)   NOT NULL,
    allocated_qty DECIMAL(19,4) NOT NULL,
    released_qty  DECIMAL(19,4) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL,
    assigned_at   DATETIME(6)   NOT NULL,
    released_at   DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_alloc_assignments_execution UNIQUE (allocation_id, execution_id),
    CONSTRAINT fk_alloc_assignments_allocation
        FOREIGN KEY (allocation_id) REFERENCES allocations (id),
    CONSTRAINT chk_alloc_assignments_status CHECK (status IN ('ASSIGNED','RELEASED')),
    CONSTRAINT chk_alloc_assignments_allocated_qty CHECK (allocated_qty > 0),
    CONSTRAINT chk_alloc_assignments_released_qty CHECK
        (released_qty >= 0 AND released_qty <= allocated_qty)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_alloc_assignments_allocation ON allocation_assignments (allocation_id);
CREATE INDEX idx_alloc_assignments_execution  ON allocation_assignments (execution_id);
