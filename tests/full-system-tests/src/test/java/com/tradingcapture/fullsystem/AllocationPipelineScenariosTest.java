package com.tradingcapture.fullsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service allocation-pipeline scenarios: a FIX AllocationInstruction produced onto Kafka is
 * consumed by allocation-engine and drives the outbound domain events observed here on the
 * outbound topics. Each run uses a unique AllocID so matching is precise and re-runnable.
 *
 * <p>Ingestion + validation + exception + business-duplicate idempotency are proven end-to-end
 * here (scenarios 6-ingestion, 7, 8, 14). The full New/Replace/Cancel allocation lifecycle
 * (ExecutionsAllocated / ExecutionsReleased / SettlementRequested) additionally needs coordinated
 * cross-service data and is proven by allocation-engine's Testcontainers orchestration suite plus
 * the seeded live run documented in docs/release/system-scenarios.md.
 *
 * <p>Self-skips unless FULL_STACK=1 (declared here on the concrete class, since JUnit execution
 * conditions on an abstract superclass are not reliably applied to subclasses).
 */
@EnabledIfEnvironmentVariable(named = "FULL_STACK", matches = "1")
class AllocationPipelineScenariosTest extends FullSystemTest {

    private static final String RECEIVED = "tcs.allocation.allocation-received.v1";
    private static final String REJECTED = "tcs.allocation.allocation-rejected.v1";
    private static final String EXCEPTION = "tcs.allocation.exception-reported.v1";
    private static final Duration WAIT = Duration.ofSeconds(30);

    // --- Scenario 6 (ingestion): a well-formed New is accepted and announced -------------

    @Test
    void wellFormedNewAllocationIsReceived() {
        String allocId = "FS-NEW-" + shortId();
        stack.produceFix(allocId, FixMessages.newAllocation(
                allocId, "ORD-1", "EXCH-ORD-1", "EXEC-1", "ACC-SEED-INV1", "AAPL", 100, 101.25));

        List<Object> received = stack.awaitEvents(RECEIVED, WAIT, containsAllocId(allocId));
        assertThat(received).as("AllocationReceived for %s", allocId).isNotEmpty();
    }

    // --- Scenario 7: multiple business errors are stored and rejected --------------------

    @Test
    void allocationWithBusinessErrorsIsRejected() {
        String allocId = "FS-REJ-" + shortId();
        stack.produceFix(allocId, FixMessages.multiErrorAllocation(allocId));

        List<Object> rejected = stack.awaitEvents(REJECTED, WAIT, containsAllocId(allocId));
        assertThat(rejected).as("AllocationRejected for %s", allocId).isNotEmpty();
    }

    // --- Scenario 8: a malformed message becomes an exception, not a crash ---------------

    @Test
    void malformedMessageRaisesAnExceptionEvent() {
        String marker = "FS-BAD-" + shortId();
        stack.produceFix(marker, FixMessages.garbage() + " " + marker);

        List<Object> exceptions = stack.awaitEvents(EXCEPTION, WAIT,
                e -> e.toString().contains(marker));
        assertThat(exceptions).as("ExceptionReported carrying %s", marker).isNotEmpty();
    }

    // --- Scenario 14: a duplicate delivery is idempotent ---------------------------------

    @Test
    void duplicateBusinessDeliveryYieldsExactlyOneRejection() {
        String allocId = "FS-DUP-" + shortId();
        String fix = FixMessages.multiErrorAllocation(allocId);
        stack.produceFix(allocId, fix);
        stack.produceFix(allocId, fix); // same business key -> deduped

        // Drain the whole window: the business key (allocId, transType) must yield exactly one event.
        List<Object> rejected = stack.collectEvents(REJECTED, WAIT, containsAllocId(allocId));
        assertThat(rejected).as("exactly one AllocationRejected for %s", allocId).hasSize(1);
    }

    private static Predicate<Object> containsAllocId(String allocId) {
        return e -> e.toString().contains(allocId);
    }

    private static String shortId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
