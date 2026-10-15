-- 1. Releasing executions of failed allocations.
--    An allocation that ends REJECTED or FAILED after trading-service may already have bound its
--    executions (for example, the ALLOCATE response was lost and the retries ran out) is flagged here.
--    The release driver keeps asking trading-service to release everything bound to its allocId,
--    which is idempotent, until the release succeeds.
ALTER TABLE allocations
    ADD COLUMN executions_release_pending BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN release_attempt_count      INT          NOT NULL DEFAULT 0,
    ADD COLUMN next_release_at            DATETIME(6)  NULL,
    ADD COLUMN last_release_error         VARCHAR(500) NULL;

CREATE INDEX idx_allocations_release_pending ON allocations (executions_release_pending, next_release_at);

-- 2. Outbox rows that can never be published (an oversized record, an undecodable payload, or a
--    schema the registry refuses) are parked instead of blocking every later event behind them.
ALTER TABLE outbox_event
    ADD COLUMN parked_at DATETIME(6) NULL;

DROP INDEX idx_outbox_event_unpublished ON outbox_event;
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (published_at, parked_at, id);
