package com.tradingcapture.allocation.selection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic execution selection (ADR-0017): eligibility filtering, FIFO ordering with stable
 * tie-breaking, whole-execution exact-fill invariants, insufficiency and excess (never a partial
 * or split execution), malformed-input handling, and input-order independence.
 */
class ExecutionSelectorTest {

    private final ExecutionSelector selector = new ExecutionSelector();

    private static SelectionCandidate candidate(String id, String qty, String isoTime, boolean eligible) {
        return new SelectionCandidate(id, "X-" + id, "AAPL", "1", new BigDecimal(qty),
                Instant.parse(isoTime), eligible);
    }

    private SelectionRequest request(String qty, List<SelectionCandidate> candidates) {
        return new SelectionRequest(new BigDecimal(qty), "AAPL", "1", candidates);
    }

    @Test
    void singleExecutionExactFill() {
        SelectionResult result = selector.select(request("100.0000",
                List.of(candidate("E1", "100.0000", "2026-07-17T10:00:00Z", true))));

        assertThat(result).isInstanceOf(SelectionResult.Complete.class);
        SelectionResult.Complete complete = (SelectionResult.Complete) result;
        assertThat(complete.totalSelected()).isEqualByComparingTo("100.0000");
        assertThat(complete.selections()).singleElement().satisfies(s -> {
            assertThat(s.selectedQuantity()).isEqualByComparingTo("100.0000");
            assertThat(s.availableAfter()).isEqualByComparingTo("0.0000");
            assertThat(s.sequence()).isEqualTo(1);
        });
    }

    @Test
    void oneExecutionLargerThanRequiredIsNeverSplit() {
        // Trading binds whole executions: taking 60 of 100 would bind all 100 and strand 40.
        SelectionResult result = selector.select(request("60.0000",
                List.of(candidate("E1", "100.0000", "2026-07-17T10:00:00Z", true))));

        assertThat(result).isInstanceOfSatisfying(SelectionResult.ExcessQuantity.class, e -> {
            assertThat(e.availableQuantity()).isEqualByComparingTo("100.0000");
            assertThat(e.requestedQuantity()).isEqualByComparingTo("60.0000");
        });
    }

    @Test
    void executionsThatOvershootTheRequestAreExcessNotAPartialFill() {
        // 100 + 100 for an allocation of 150 cannot be met with whole executions.
        SelectionResult result = selector.select(request("150.0000", List.of(
                candidate("E1", "100.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "100.0000", "2026-07-17T11:00:00Z", true))));

        assertThat(result).isInstanceOf(SelectionResult.ExcessQuantity.class);
    }

    @Test
    void multipleWholeExecutionsAreSequencedFifo() {
        List<SelectionCandidate> candidates = List.of(
                candidate("E3", "20.0000", "2026-07-17T12:00:00Z", true),
                candidate("E1", "40.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "40.0000", "2026-07-17T11:00:00Z", true));

        SelectionResult.Complete complete = (SelectionResult.Complete) selector.select(request("100.0000", candidates));

        // FIFO: E1 (40) + E2 (40) + E3 (20) = 100, in time order regardless of input order.
        assertThat(complete.selections()).extracting(SelectionResult.SelectedExecution::execId)
                .containsExactly("X-E1", "X-E2", "X-E3");
        assertThat(complete.selections()).extracting(s -> s.selectedQuantity().toPlainString())
                .containsExactly("40.0000", "40.0000", "20.0000");
        assertThat(complete.selections()).allSatisfy(s ->
                assertThat(s.availableAfter()).isEqualByComparingTo("0.0000"));
        assertThat(complete.totalSelected()).isEqualByComparingTo("100.0000");
    }

    @Test
    void insufficientAggregateQuantityIsNotAPartialFill() {
        SelectionResult result = selector.select(request("100.0000", List.of(
                candidate("E1", "40.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "30.0000", "2026-07-17T11:00:00Z", true))));

        assertThat(result).isInstanceOfSatisfying(SelectionResult.Insufficient.class, i -> {
            assertThat(i.availableQuantity()).isEqualByComparingTo("70.0000");
            assertThat(i.requestedQuantity()).isEqualByComparingTo("100.0000");
        });
    }

    @Test
    void ineligibleAndMismatchedCandidatesAreExcluded() {
        // not eligible, wrong instrument, wrong side, zero qty, already-allocated (eligible=false)
        List<SelectionCandidate> candidates = List.of(
                candidate("E1", "100.0000", "2026-07-17T10:00:00Z", false),
                new SelectionCandidate("E2", "X-E2", "MSFT", "1", new BigDecimal("100.0000"), Instant.parse("2026-07-17T10:00:00Z"), true),
                new SelectionCandidate("E3", "X-E3", "AAPL", "2", new BigDecimal("100.0000"), Instant.parse("2026-07-17T10:00:00Z"), true),
                new SelectionCandidate("E4", "X-E4", "AAPL", "1", new BigDecimal("0.0000"), Instant.parse("2026-07-17T10:00:00Z"), true));

        assertThat(selector.select(request("100.0000", candidates)))
                .isInstanceOf(SelectionResult.NoEligibleExecutions.class);
    }

    @Test
    void timestampTieIsBrokenByStableIdentifier() {
        String sameTime = "2026-07-17T10:00:00Z";
        List<SelectionCandidate> candidates = List.of(
                candidate("E2", "60.0000", sameTime, true),
                candidate("E1", "40.0000", sameTime, true));

        SelectionResult.Complete complete = (SelectionResult.Complete) selector.select(request("100.0000", candidates));
        // Equal timestamps -> execId order: X-E1 before X-E2.
        assertThat(complete.selections()).extracting(SelectionResult.SelectedExecution::execId)
                .containsExactly("X-E1", "X-E2");
        assertThat(complete.selections()).extracting(SelectionResult.SelectedExecution::sequence)
                .containsExactly(1, 2);
    }

    @Test
    void resultIsIndependentOfInputOrder() {
        List<SelectionCandidate> base = new ArrayList<>(List.of(
                candidate("E1", "25.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "25.0000", "2026-07-17T11:00:00Z", true),
                candidate("E3", "25.0000", "2026-07-17T12:00:00Z", true),
                candidate("E4", "25.0000", "2026-07-17T13:00:00Z", true)));

        List<String> reference = execIds(selector.select(request("100.0000", base)));
        for (int i = 0; i < 6; i++) {
            Collections.shuffle(base, new java.util.Random(i));
            assertThat(execIds(selector.select(request("100.0000", new ArrayList<>(base)))))
                    .as("shuffle seed %d", i).isEqualTo(reference);
        }
    }

    @Test
    void repeatedIdenticalCallsProduceIdenticalResults() {
        SelectionRequest req = request("80.0000", List.of(
                candidate("E1", "40.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "40.0000", "2026-07-17T11:00:00Z", true)));
        assertThat(execIds(selector.select(req))).isEqualTo(execIds(selector.select(req)));
    }

    @Test
    void malformedQuantitiesAndRequestAreTypedFailures() {
        // requested non-positive
        assertThat(selector.select(request("0.0000", List.of(
                candidate("E1", "100.0000", "2026-07-17T10:00:00Z", true)))))
                .isInstanceOf(SelectionResult.InvalidCandidate.class);

        // requested over-precision
        assertThat(selector.select(new SelectionRequest(new BigDecimal("10.00001"), "AAPL", "1", List.of(
                candidate("E1", "100.0000", "2026-07-17T10:00:00Z", true)))))
                .isInstanceOf(SelectionResult.InvalidCandidate.class);

        // candidate over-precision
        assertThat(selector.select(request("10.0000", List.of(
                new SelectionCandidate("E1", "X-E1", "AAPL", "1", new BigDecimal("10.00001"),
                        Instant.parse("2026-07-17T10:00:00Z"), true)))))
                .isInstanceOf(SelectionResult.InvalidCandidate.class);

        // candidate missing ordering timestamp
        assertThat(selector.select(request("10.0000", List.of(
                new SelectionCandidate("E1", "X-E1", "AAPL", "1", new BigDecimal("10.0000"), null, true)))))
                .isInstanceOf(SelectionResult.InvalidCandidate.class);
    }

    @Test
    void nullRequestIsInvariant() {
        assertThat(selector.select(null)).isInstanceOf(SelectionResult.Invariant.class);
    }

    @Test
    void selectedQuantitiesArePositiveAndSumToRequested() {
        SelectionResult.Complete complete = (SelectionResult.Complete) selector.select(request("85.0000", List.of(
                candidate("E1", "40.0000", "2026-07-17T10:00:00Z", true),
                candidate("E2", "40.0000", "2026-07-17T11:00:00Z", true),
                candidate("E3", "5.0000", "2026-07-17T12:00:00Z", true))));

        assertThat(complete.selections()).allSatisfy(s -> {
            assertThat(s.selectedQuantity().signum()).isPositive();
            assertThat(s.selectedQuantity()).isEqualByComparingTo(s.availableBefore()); // whole
        });
        BigDecimal sum = complete.selections().stream().map(SelectionResult.SelectedExecution::selectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("85.0000");
    }

    private static List<String> execIds(SelectionResult result) {
        return ((SelectionResult.Complete) result).selections().stream()
                .map(SelectionResult.SelectedExecution::execId).toList();
    }
}
