package com.tradingcapture.allocation.validation.logical;

import com.tradingcapture.allocation.client.AccountValidationClient.AccountValidation;
import com.tradingcapture.allocation.client.DependencyCallException;
import com.tradingcapture.allocation.client.DependencyFailureType;
import com.tradingcapture.allocation.client.InstrumentResolutionClient.InstrumentResolution;
import com.tradingcapture.allocation.client.InstrumentResolutionClient.InstrumentSummary;
import com.tradingcapture.allocation.client.TradingExecutionClient.ExecutionResolution;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.model.OrchestrationFailureCategory;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.ExistingAllocationView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Phase 8 logical validator: each stage's business rules, multi-error collection with
 * deterministic ordering and de-duplication, and the separation of dependency/invariant failures
 * from business rejections.
 */
class LogicalAllocationValidatorTest {

    private final LogicalAllocationValidator validator = new LogicalAllocationValidator();

    // --- baseline valid NEW --------------------------------------------------------------------

    private static AccountValidation account(String id, boolean found, String status, boolean active,
                                             String owner, Boolean cash, Boolean securities) {
        return new AccountValidation(id, found, status, active, owner, cash, securities, List.of());
    }

    private static InstrumentResolution instrument(String value, boolean found, boolean ambiguous,
                                                   String mic, String ccy, String status) {
        InstrumentSummary summary = found
                ? new InstrumentSummary(value, value + " Inc.", "EQUITY", mic, ccy, status)
                : null;
        return new InstrumentResolution("EXCHANGE_SYMBOL", value, mic, found, ambiguous, summary);
    }

    private static ExecutionResolution execution(String execId, String instrumentId, String side,
                                                 String allocatable, boolean eligible, String reason,
                                                 String allocationId) {
        return new ExecutionResolution(execId, true, "exec_" + execId, "ORD-1", instrumentId, "ACC-1",
                side, new BigDecimal(allocatable), new BigDecimal(allocatable), "USD",
                LocalDate.parse("2026-07-17"), "0", LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-17T14:00:00Z"), "UNALLOCATED", allocationId, eligible, reason);
    }

    private static AllocationView newAllocation(List<AccountLine> accounts, List<String> execIds,
                                                BigDecimal quantity) {
        return new AllocationView("A1", AllocTransType.NEW, null, "1", quantity, "USD",
                "AAPL", "XNAS", "0", accounts, execIds);
    }

    private LogicalValidationContext validNew() {
        return new LogicalValidationContext(
                newAllocation(List.of(new AccountLine("ACC-1", new BigDecimal("100.0000"), null, "USD")),
                        List.of("E1"), new BigDecimal("100.0000")),
                List.of(account("ACC-1", true, "ACTIVE", true, "investor1", true, true)),
                List.of(instrument("AAPL", true, false, "XNAS", "USD", "ACTIVE")),
                List.of(execution("E1", "AAPL", "1", "100.0000", true, "ELIGIBLE", null)),
                List.of("0"),
                null);
    }

    private static List<LogicalValidationCode> codes(LogicalValidationResult result) {
        assertThat(result).isInstanceOf(LogicalValidationResult.BusinessRejected.class);
        return ((LogicalValidationResult.BusinessRejected) result).errors().stream()
                .map(LogicalValidationError::code).toList();
    }

    @Test
    void aFullyValidNewInstructionPasses() {
        assertThat(validator.validate(validNew()).isValid()).isTrue();
    }

    // --- account rules -------------------------------------------------------------------------

    @Test
    void missingInactiveAndInsufficientAccountsAreRejected() {
        LogicalValidationContext missing = withAccount(validNew(), account("ACC-1", false, null, false, null, null, null));
        assertThat(codes(validator.validate(missing))).contains(LogicalValidationCode.ACCOUNT_NOT_FOUND);

        LogicalValidationContext inactive = withAccount(validNew(), account("ACC-1", true, "SUSPENDED", false, "investor1", true, true));
        assertThat(codes(validator.validate(inactive))).contains(LogicalValidationCode.ACCOUNT_INACTIVE);

        LogicalValidationContext noCash = withAccount(validNew(), account("ACC-1", true, "ACTIVE", true, "investor1", false, true));
        assertThat(codes(validator.validate(noCash))).contains(LogicalValidationCode.INSUFFICIENT_CASH);

        LogicalValidationContext noSec = withAccount(validNew(), account("ACC-1", true, "ACTIVE", true, "investor1", true, false));
        assertThat(codes(validator.validate(noSec))).contains(LogicalValidationCode.INSUFFICIENT_SECURITIES);

        LogicalValidationContext unauthorized = withAccount(validNew(), account("ACC-1", true, "ACTIVE", true, "  ", true, true));
        assertThat(codes(validator.validate(unauthorized))).contains(LogicalValidationCode.ACCOUNT_UNAUTHORIZED);
    }

    // --- instrument rules ----------------------------------------------------------------------

    @Test
    void instrumentNotFoundAmbiguousInactiveExchangeAndCurrencyAreRejected() {
        assertThat(codes(validator.validate(withInstrument(validNew(),
                instrument("AAPL", false, false, "XNAS", "USD", "ACTIVE")))))
                .contains(LogicalValidationCode.INSTRUMENT_NOT_FOUND);

        assertThat(codes(validator.validate(withInstrument(validNew(),
                new InstrumentResolution("EXCHANGE_SYMBOL", "AAPL", null, false, true, null)))))
                .contains(LogicalValidationCode.INSTRUMENT_AMBIGUOUS);

        assertThat(codes(validator.validate(withInstrument(validNew(),
                instrument("AAPL", true, false, "XNAS", "USD", "INACTIVE")))))
                .contains(LogicalValidationCode.INSTRUMENT_INACTIVE);

        assertThat(codes(validator.validate(withInstrument(validNew(),
                instrument("AAPL", true, false, "XLON", "USD", "ACTIVE")))))
                .contains(LogicalValidationCode.EXCHANGE_MISMATCH);

        assertThat(codes(validator.validate(withInstrument(validNew(),
                instrument("AAPL", true, false, "XNAS", "GBP", "ACTIVE")))))
                .contains(LogicalValidationCode.CURRENCY_UNSUPPORTED);
    }

    @Test
    void unsupportedSettlementTypeIsRejected() {
        LogicalValidationContext base = validNew();
        LogicalValidationContext ctx = new LogicalValidationContext(base.allocation(), base.accountResults(),
                base.instrumentResults(), base.executionResults(), List.of("3"), null); // "0" not supported
        assertThat(codes(validator.validate(ctx))).contains(LogicalValidationCode.SETTLEMENT_INCOMPATIBLE);
    }

    // --- execution rules -----------------------------------------------------------------------

    @Test
    void executionNotFoundCancelledSupersededAlreadyAllocatedAreRejected() {
        assertThat(codes(validator.validate(withExecution(validNew(),
                new ExecutionResolution("E1", false, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, false, "NOT_FOUND")))))
                .contains(LogicalValidationCode.EXECUTION_NOT_FOUND);

        assertThat(codes(validator.validate(withExecution(validNew(),
                execution("E1", "AAPL", "1", "100.0000", false, "CANCELLED", null)))))
                .contains(LogicalValidationCode.EXECUTION_CANCELLED);

        assertThat(codes(validator.validate(withExecution(validNew(),
                execution("E1", "AAPL", "1", "100.0000", false, "SUPERSEDED", null)))))
                .contains(LogicalValidationCode.EXECUTION_SUPERSEDED);

        assertThat(codes(validator.validate(withExecution(validNew(),
                execution("E1", "AAPL", "1", "0", false, "ALREADY_ALLOCATED", "OTHER")))))
                .contains(LogicalValidationCode.EXECUTION_ALREADY_ALLOCATED);
    }

    @Test
    void executionInstrumentAndSideMismatchAreRejected() {
        assertThat(codes(validator.validate(withExecution(validNew(),
                execution("E1", "MSFT", "1", "100.0000", true, "ELIGIBLE", null)))))
                .contains(LogicalValidationCode.EXECUTION_INSTRUMENT_MISMATCH);

        assertThat(codes(validator.validate(withExecution(validNew(),
                execution("E1", "AAPL", "2", "100.0000", true, "ELIGIBLE", null)))))
                .contains(LogicalValidationCode.EXECUTION_SIDE_MISMATCH);
    }

    @Test
    void duplicateExecutionReferenceIsRejected() {
        LogicalValidationContext base = validNew();
        AllocationView allocation = new AllocationView("A1", AllocTransType.NEW, null, "1",
                new BigDecimal("100.0000"), "USD", "AAPL", "XNAS", "0",
                base.allocation().accounts(), List.of("E1", "E1"));
        LogicalValidationContext ctx = new LogicalValidationContext(allocation, base.accountResults(),
                base.instrumentResults(), base.executionResults(), base.supportedSettlTypes(), null);
        assertThat(codes(validator.validate(ctx))).contains(LogicalValidationCode.DUPLICATE_EXECUTION_REF);
    }

    // --- quantity rules ------------------------------------------------------------------------

    @Test
    void quantityConsistencyRulesAreEnforced() {
        // account total (100) != allocation quantity (120)
        LogicalValidationContext mismatch = withQuantity(validNew(), new BigDecimal("120.0000"));
        assertThat(codes(validator.validate(mismatch)))
                .contains(LogicalValidationCode.QUANTITY_MISMATCH,
                        LogicalValidationCode.INSUFFICIENT_EXECUTION_QUANTITY);

        // non-positive allocation quantity
        LogicalValidationContext base = validNew();
        AllocationView zero = new AllocationView("A1", AllocTransType.NEW, null, "1", new BigDecimal("0.0000"),
                "USD", "AAPL", "XNAS", "0",
                List.of(new AccountLine("ACC-1", new BigDecimal("0.0000"), null, "USD")), List.of("E1"));
        LogicalValidationContext zeroCtx = new LogicalValidationContext(zero, base.accountResults(),
                base.instrumentResults(), base.executionResults(), base.supportedSettlTypes(), null);
        assertThat(codes(validator.validate(zeroCtx))).contains(LogicalValidationCode.QUANTITY_NOT_POSITIVE);
    }

    @Test
    void insufficientAggregateExecutionQuantityIsRejected() {
        LogicalValidationContext ctx = withExecution(validNew(),
                execution("E1", "AAPL", "1", "40.0000", true, "ELIGIBLE", null));
        assertThat(codes(validator.validate(ctx)))
                .contains(LogicalValidationCode.INSUFFICIENT_EXECUTION_QUANTITY);
    }

    @Test
    void executionsExceedingTheAllocationQuantityAreRejectedNotSplit() {
        // Trading binds whole executions; allocating 100 of a 150 fill would bind all 150.
        LogicalValidationContext ctx = withExecution(validNew(),
                execution("E1", "AAPL", "1", "150.0000", true, "ELIGIBLE", null));
        assertThat(codes(validator.validate(ctx)))
                .containsExactly(LogicalValidationCode.EXCESS_EXECUTION_QUANTITY);
    }

    // --- transaction-type rules ----------------------------------------------------------------

    @Test
    void replacePrerequisitesAreEnforced() {
        // original missing
        LogicalValidationContext missing = replace(null);
        assertThat(codes(validator.validate(missing))).contains(LogicalValidationCode.REPLACE_ORIGINAL_NOT_FOUND);

        // original ineligible state
        LogicalValidationContext ineligible = replace(new ExistingAllocationView("ORIG", AllocTransType.NEW,
                AllocationStatus.RECEIVED, "AAPL", List.of("ACC-1")));
        assertThat(codes(validator.validate(ineligible))).contains(LogicalValidationCode.REPLACE_ORIGINAL_INELIGIBLE);

        // instrument mismatch
        LogicalValidationContext instrumentMismatch = replace(new ExistingAllocationView("ORIG",
                AllocTransType.NEW, AllocationStatus.COMPLETED, "MSFT", List.of("ACC-1")));
        assertThat(codes(validator.validate(instrumentMismatch))).contains(LogicalValidationCode.REPLACE_INSTRUMENT_MISMATCH);

        // valid replace
        LogicalValidationContext valid = replace(new ExistingAllocationView("ORIG", AllocTransType.NEW,
                AllocationStatus.COMPLETED, "AAPL", List.of("ACC-1")));
        assertThat(validator.validate(valid).isValid()).isTrue();
    }

    @Test
    void cancelPrerequisitesAreEnforcedIncludingRepeatedCancel() {
        LogicalValidationContext missing = cancel(null);
        assertThat(codes(validator.validate(missing))).contains(LogicalValidationCode.CANCEL_ORIGINAL_NOT_FOUND);

        // already-released original models a repeated cancel -> ineligible
        LogicalValidationContext repeated = cancel(new ExistingAllocationView("ORIG", AllocTransType.NEW,
                AllocationStatus.EXECUTIONS_RELEASED, "AAPL", List.of("ACC-1")));
        assertThat(codes(validator.validate(repeated))).contains(LogicalValidationCode.CANCEL_ORIGINAL_INELIGIBLE);

        LogicalValidationContext valid = cancel(new ExistingAllocationView("ORIG", AllocTransType.NEW,
                AllocationStatus.COMPLETED, "AAPL", List.of("ACC-1")));
        assertThat(validator.validate(valid).isValid()).isTrue();
    }

    @Test
    void replaceMayRelistTheOriginalsExecutionsCountedAtTheirFullQuantity() {
        // Still bound to ORIG, so trading reports 0 allocatable — the release step frees all 100.
        LogicalValidationContext ctx = withExecution(replace(live("ORIG", AllocationStatus.COMPLETED)),
                boundTo("E1", "ORIG", "100.0000"));
        assertThat(validator.validate(ctx).isValid()).isTrue();

        LogicalValidationContext tooMuch = withExecution(withQuantity(
                replace(live("ORIG", AllocationStatus.COMPLETED)), new BigDecimal("60.0000")),
                boundTo("E1", "ORIG", "100.0000"));
        assertThat(codes(validator.validate(tooMuch))).contains(LogicalValidationCode.EXCESS_EXECUTION_QUANTITY);
    }

    @Test
    void replaceCannotTakeExecutionsBoundToAnotherAllocation() {
        LogicalValidationContext ctx = withExecution(replace(live("ORIG", AllocationStatus.COMPLETED)),
                boundTo("E1", "OTHER", "100.0000"));
        assertThat(codes(validator.validate(ctx))).contains(LogicalValidationCode.EXECUTION_ALREADY_ALLOCATED);
    }

    @Test
    void cancelIsValidatedAgainstItsOriginalOnlyEvenWhenItRelistsTheOriginalsExecutions() {
        // The orchestrator makes no account/instrument/execution lookups for a Cancel.
        LogicalValidationContext base = cancel(live("ORIG", AllocationStatus.COMPLETED));
        LogicalValidationContext ctx = new LogicalValidationContext(base.allocation(), List.of(), List.of(),
                List.of(boundTo("E1", "ORIG", "100.0000")), null, base.existingAllocation());
        assertThat(validator.validate(ctx).isValid()).isTrue();
    }

    @Test
    void anAlreadyReplacedOrCancelledOriginalCannotBeReplacedOrCancelledAgain() {
        for (AllocationStatus superseded : List.of(AllocationStatus.REPLACED, AllocationStatus.CANCELLED)) {
            assertThat(codes(validator.validate(replace(live("ORIG", superseded)))))
                    .contains(LogicalValidationCode.REPLACE_ORIGINAL_INELIGIBLE);
            assertThat(codes(validator.validate(cancel(live("ORIG", superseded)))))
                    .contains(LogicalValidationCode.CANCEL_ORIGINAL_INELIGIBLE);
        }
    }

    private static ExistingAllocationView live(String allocId, AllocationStatus status) {
        return new ExistingAllocationView(allocId, AllocTransType.NEW, status, "AAPL", List.of("ACC-1"));
    }

    /** An execution currently bound to {@code allocationId}: trading reports nothing allocatable. */
    private static ExecutionResolution boundTo(String execId, String allocationId, String executed) {
        return new ExecutionResolution(execId, true, "exec_" + execId, "ORD-1", "AAPL", "ACC-1", "1",
                new BigDecimal(executed), new BigDecimal("0.0000"), "USD",
                LocalDate.parse("2026-07-17"), "0", LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-17T14:00:00Z"), "ALLOCATED", allocationId, false, "ALREADY_ALLOCATED");
    }

    @Test
    void conflictingNewIsRejected() {
        LogicalValidationContext base = validNew();
        LogicalValidationContext ctx = new LogicalValidationContext(base.allocation(), base.accountResults(),
                base.instrumentResults(), base.executionResults(), base.supportedSettlTypes(),
                new ExistingAllocationView("A1", AllocTransType.NEW, AllocationStatus.EXECUTIONS_ALLOCATED,
                        "AAPL", List.of("ACC-1")));
        assertThat(codes(validator.validate(ctx))).contains(LogicalValidationCode.NEW_CONFLICTING_ALLOCATION);
    }

    // --- multi-error, ordering, dependency separation ------------------------------------------

    @Test
    void multipleIndependentErrorsAreCollectedDeterministicallyOrderedAndDeduplicated() {
        LogicalValidationContext base = validNew();
        LogicalValidationContext ctx = new LogicalValidationContext(
                newAllocation(List.of(new AccountLine("ACC-1", new BigDecimal("100.0000"), null, "USD")),
                        List.of("E1"), new BigDecimal("100.0000")),
                List.of(account("ACC-1", true, "SUSPENDED", false, "investor1", true, true)),   // ACCOUNT_INACTIVE
                List.of(instrument("AAPL", true, false, "XLON", "USD", "ACTIVE")),               // EXCHANGE_MISMATCH
                List.of(execution("E1", "AAPL", "2", "100.0000", true, "ELIGIBLE", null)),       // SIDE_MISMATCH
                base.supportedSettlTypes(), null);

        LogicalValidationResult result = validator.validate(ctx);
        List<LogicalValidationError> errors = ((LogicalValidationResult.BusinessRejected) result).errors();

        // Ordered by stage: ACCOUNT < INSTRUMENT < EXECUTION.
        assertThat(errors).extracting(e -> e.code().stage())
                .containsExactly(ValidationStage.ACCOUNT, ValidationStage.INSTRUMENT, ValidationStage.EXECUTION);
        assertThat(errors).extracting(LogicalValidationError::code)
                .containsExactly(LogicalValidationCode.ACCOUNT_INACTIVE,
                        LogicalValidationCode.EXCHANGE_MISMATCH,
                        LogicalValidationCode.EXECUTION_SIDE_MISMATCH);
        // No duplicates.
        assertThat(errors).doesNotHaveDuplicates();
    }

    @Test
    void dependencyFailuresAreClassifiedSeparatelyFromBusinessRejections() {
        assertThat(LogicalValidationResult.fromDependencyFailure(
                new DependencyCallException(DependencyFailureType.TIMEOUT, "trading", "read timeout")))
                .isInstanceOfSatisfying(LogicalValidationResult.DependencyUnavailable.class,
                        d -> assertThat(d.category()).isEqualTo(OrchestrationFailureCategory.TRANSIENT_INFRASTRUCTURE));

        assertThat(LogicalValidationResult.fromDependencyFailure(
                new DependencyCallException(DependencyFailureType.UNAVAILABLE, "account", "circuit open")))
                .isInstanceOfSatisfying(LogicalValidationResult.DependencyUnavailable.class,
                        d -> assertThat(d.category()).isEqualTo(OrchestrationFailureCategory.DEPENDENCY_UNAVAILABLE));

        assertThat(LogicalValidationResult.fromDependencyFailure(
                new DependencyCallException(DependencyFailureType.UNAUTHORIZED, "enrichment", "HTTP 403")))
                .isInstanceOfSatisfying(LogicalValidationResult.DependencyTerminal.class,
                        d -> assertThat(d.category()).isEqualTo(OrchestrationFailureCategory.TERMINAL_ORCHESTRATION));

        assertThat(LogicalValidationResult.fromDependencyFailure(
                new DependencyCallException(DependencyFailureType.MALFORMED_RESPONSE, "trading", "unreadable")))
                .isInstanceOf(LogicalValidationResult.DependencyTerminal.class);
    }

    @Test
    void anIncompleteContextIsAnInvariantNotABusinessRejection() {
        assertThat(validator.validate(null)).isInstanceOf(LogicalValidationResult.Invariant.class);
    }

    // --- mutation helpers ----------------------------------------------------------------------

    private LogicalValidationContext withAccount(LogicalValidationContext base, AccountValidation account) {
        return new LogicalValidationContext(base.allocation(), List.of(account), base.instrumentResults(),
                base.executionResults(), base.supportedSettlTypes(), base.existingAllocation());
    }

    private LogicalValidationContext withInstrument(LogicalValidationContext base, InstrumentResolution instrument) {
        return new LogicalValidationContext(base.allocation(), base.accountResults(), List.of(instrument),
                base.executionResults(), base.supportedSettlTypes(), base.existingAllocation());
    }

    private LogicalValidationContext withExecution(LogicalValidationContext base, ExecutionResolution execution) {
        return new LogicalValidationContext(base.allocation(), base.accountResults(), base.instrumentResults(),
                List.of(execution), base.supportedSettlTypes(), base.existingAllocation());
    }

    private LogicalValidationContext withQuantity(LogicalValidationContext base, BigDecimal quantity) {
        AllocationView a = base.allocation();
        AllocationView allocation = new AllocationView(a.allocId(), a.transType(), a.refAllocId(), a.side(),
                quantity, a.currency(), a.instrumentSymbol(), a.securityExchange(), a.settlType(),
                a.accounts(), a.execIds());
        return new LogicalValidationContext(allocation, base.accountResults(), base.instrumentResults(),
                base.executionResults(), base.supportedSettlTypes(), base.existingAllocation());
    }

    private LogicalValidationContext replace(ExistingAllocationView existing) {
        return transactionType(AllocTransType.REPLACE, existing);
    }

    private LogicalValidationContext cancel(ExistingAllocationView existing) {
        return transactionType(AllocTransType.CANCEL, existing);
    }

    private LogicalValidationContext transactionType(AllocTransType type, ExistingAllocationView existing) {
        LogicalValidationContext base = validNew();
        AllocationView allocation = new AllocationView("A2", type, "ORIG", "1", new BigDecimal("100.0000"),
                "USD", "AAPL", "XNAS", "0", base.allocation().accounts(), List.of("E1"));
        return new LogicalValidationContext(allocation, base.accountResults(), base.instrumentResults(),
                base.executionResults(), base.supportedSettlTypes(), existing);
    }
}
