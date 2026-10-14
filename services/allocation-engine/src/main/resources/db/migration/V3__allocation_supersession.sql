-- Trading Capture System - tcs_allocation schema (Replace/Cancel supersession).
-- A Replace/Cancel that releases the allocation it references (RefAllocID) now retires that
-- original in the same transaction, so a superseded allocation can never be replaced or cancelled
-- again, and a later Replace/Cancel can reference the replacement instead:
--   (EXECUTIONS_ALLOCATED | COMPLETED) -> REPLACED | CANCELLED   (both terminal)
-- This migration only widens the status code domains; no existing row changes.

ALTER TABLE allocations DROP CHECK chk_allocations_status;
ALTER TABLE allocations ADD CONSTRAINT chk_allocations_status CHECK
    (status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                'EXECUTIONS_RELEASED','COMPLETED','FAILED','REPLACED','CANCELLED'));

ALTER TABLE allocation_state_transitions DROP CHECK chk_alloc_transitions_from;
ALTER TABLE allocation_state_transitions DROP CHECK chk_alloc_transitions_to;
ALTER TABLE allocation_state_transitions ADD CONSTRAINT chk_alloc_transitions_from CHECK
    (from_status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                     'EXECUTIONS_RELEASED','COMPLETED','FAILED','REPLACED','CANCELLED'));
ALTER TABLE allocation_state_transitions ADD CONSTRAINT chk_alloc_transitions_to CHECK
    (to_status IN ('RECEIVED','INVALID','VALIDATED','REJECTED','EXECUTIONS_ALLOCATED',
                   'EXECUTIONS_RELEASED','COMPLETED','FAILED','REPLACED','CANCELLED'));
