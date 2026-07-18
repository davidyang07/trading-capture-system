package com.tradingcapture.allocation.validation.logical;

import com.tradingcapture.allocation.client.AccountValidationClient.AccountValidation;
import com.tradingcapture.allocation.client.InstrumentResolutionClient.InstrumentResolution;
import com.tradingcapture.allocation.client.TradingExecutionClient.ExecutionResolution;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.ExistingAllocationView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 8 logical (cross-service) validation, run after remote resolution and separately from the
 * Phase 7 FIX field validation. It consumes a framework-free {@link LogicalValidationContext} and
 * emits a typed {@link LogicalValidationResult}: {@code Valid}, or a {@code BusinessRejected} with a
 * deterministically ordered, de-duplicated list of business errors. Dependency and invariant
 * failures are represented by their own result variants — never mixed into the business-error list.
 *
 * <p>Stages run in {@link ValidationStage} order (account → instrument → execution → quantity →
 * transaction-type) and all independent business errors are collected, not short-circuited.</p>
 */
@Component
public class LogicalAllocationValidator {

    private static final int MAX_QUANTITY_SCALE = 4;

    /** Original-allocation states from which a Replace/Cancel may proceed. */
    private static final Set<AllocationStatus> REPLACEABLE_OR_CANCELLABLE =
            EnumSet.of(AllocationStatus.EXECUTIONS_ALLOCATED, AllocationStatus.COMPLETED);

    /** Existing-allocation states that make a fresh New for the same id a conflict. */
    private static final Set<AllocationStatus> NEW_CONFLICT_STATES =
            EnumSet.of(AllocationStatus.VALIDATED, AllocationStatus.EXECUTIONS_ALLOCATED,
                    AllocationStatus.EXECUTIONS_RELEASED, AllocationStatus.COMPLETED);

    public LogicalValidationResult validate(LogicalValidationContext context) {
        if (context == null || context.allocation() == null || context.allocation().transType() == null) {
            return new LogicalValidationResult.Invariant("logical validation context is incomplete");
        }

        List<LogicalValidationError> errors = new ArrayList<>();
        AllocationView allocation = context.allocation();

        validateAccounts(allocation, context.accountResults(), errors);
        validateInstrument(allocation, context.instrumentResults(), context.supportedSettlTypes(), errors);
        validateExecutions(allocation, context.executionResults(), errors);
        validateQuantities(allocation, context.executionResults(), errors);
        validateTransactionType(allocation, context.existingAllocation(), errors);

        if (errors.isEmpty()) {
            return LogicalValidationResult.valid();
        }
        List<LogicalValidationError> ordered = errors.stream()
                .distinct()
                .sorted(LogicalValidationError.DETERMINISTIC_ORDER)
                .toList();
        return LogicalValidationResult.rejected(ordered);
    }

    // --- account stage -------------------------------------------------------------------------

    private void validateAccounts(AllocationView allocation, List<AccountValidation> results,
                                  List<LogicalValidationError> errors) {
        for (AccountLine line : allocation.accounts()) {
            AccountValidation result = find(results, r -> r.accountId().equals(line.accountId()));
            if (result == null || !result.found()) {
                errors.add(error(LogicalValidationCode.ACCOUNT_NOT_FOUND, line.accountId(),
                        "account " + line.accountId() + " does not exist"));
                continue;
            }
            if (!result.active()) {
                errors.add(error(LogicalValidationCode.ACCOUNT_INACTIVE, line.accountId(),
                        "account " + line.accountId() + " is not active (" + result.status() + ")"));
                continue;
            }
            if (result.ownerSubject() == null || result.ownerSubject().isBlank()) {
                errors.add(error(LogicalValidationCode.ACCOUNT_UNAUTHORIZED, line.accountId(),
                        "account " + line.accountId() + " has no established owner relationship"));
            }
            if (Boolean.FALSE.equals(result.sufficientCash())) {
                errors.add(error(LogicalValidationCode.INSUFFICIENT_CASH, line.accountId(),
                        "account " + line.accountId() + " has insufficient cash"));
            }
            if (Boolean.FALSE.equals(result.sufficientSecurities())) {
                errors.add(error(LogicalValidationCode.INSUFFICIENT_SECURITIES, line.accountId(),
                        "account " + line.accountId() + " has insufficient security position"));
            }
        }
    }

    // --- instrument stage ----------------------------------------------------------------------

    private void validateInstrument(AllocationView allocation, List<InstrumentResolution> results,
                                    List<String> supportedSettlTypes, List<LogicalValidationError> errors) {
        InstrumentResolution resolution = find(results,
                r -> r.value() != null && r.value().equals(allocation.instrumentSymbol()));
        if (resolution == null && !results.isEmpty()) {
            resolution = results.get(0);
        }
        if (resolution == null || !resolution.found() || resolution.instrument() == null) {
            if (resolution != null && resolution.ambiguous()) {
                errors.add(error(LogicalValidationCode.INSTRUMENT_AMBIGUOUS, allocation.instrumentSymbol(),
                        "instrument " + allocation.instrumentSymbol() + " is ambiguous"));
            } else {
                errors.add(error(LogicalValidationCode.INSTRUMENT_NOT_FOUND, allocation.instrumentSymbol(),
                        "instrument " + allocation.instrumentSymbol() + " could not be resolved"));
            }
            return;
        }
        if (resolution.ambiguous()) {
            errors.add(error(LogicalValidationCode.INSTRUMENT_AMBIGUOUS, allocation.instrumentSymbol(),
                    "instrument " + allocation.instrumentSymbol() + " is ambiguous"));
            return;
        }
        var instrument = resolution.instrument();
        if (instrument.status() != null && !"ACTIVE".equals(instrument.status())) {
            errors.add(error(LogicalValidationCode.INSTRUMENT_INACTIVE, allocation.instrumentSymbol(),
                    "instrument " + allocation.instrumentSymbol() + " is not active"));
        }
        if (mismatch(allocation.securityExchange(), instrument.exchangeMic())) {
            errors.add(error(LogicalValidationCode.EXCHANGE_MISMATCH, allocation.instrumentSymbol(),
                    "exchange " + allocation.securityExchange() + " != " + instrument.exchangeMic()));
        }
        if (mismatch(allocation.currency(), instrument.currencyCode())) {
            errors.add(error(LogicalValidationCode.CURRENCY_UNSUPPORTED, allocation.instrumentSymbol(),
                    "currency " + allocation.currency() + " not supported for the instrument"));
        }
        if (supportedSettlTypes != null && allocation.settlType() != null
                && !supportedSettlTypes.contains(allocation.settlType())) {
            errors.add(error(LogicalValidationCode.SETTLEMENT_INCOMPATIBLE, allocation.instrumentSymbol(),
                    "settlement type " + allocation.settlType() + " is not supported"));
        }
    }

    // --- execution stage -----------------------------------------------------------------------

    private void validateExecutions(AllocationView allocation, List<ExecutionResolution> results,
                                    List<LogicalValidationError> errors) {
        Set<String> seen = new HashSet<>();
        for (String execId : allocation.execIds()) {
            if (!seen.add(execId)) {
                errors.add(error(LogicalValidationCode.DUPLICATE_EXECUTION_REF, execId,
                        "execution " + execId + " is referenced more than once"));
                continue;
            }
            ExecutionResolution result = find(results, r -> execId.equals(r.execId()));
            if (result == null || !result.found()) {
                errors.add(error(LogicalValidationCode.EXECUTION_NOT_FOUND, execId,
                        "execution " + execId + " could not be resolved"));
                continue;
            }
            if ("CANCELLED".equals(result.reason())) {
                errors.add(error(LogicalValidationCode.EXECUTION_CANCELLED, execId,
                        "execution " + execId + " is cancelled"));
                continue;
            }
            if ("SUPERSEDED".equals(result.reason())) {
                errors.add(error(LogicalValidationCode.EXECUTION_SUPERSEDED, execId,
                        "execution " + execId + " has been superseded"));
                continue;
            }
            if ("ALREADY_ALLOCATED".equals(result.reason()) && !retainedByReplace(allocation, result)) {
                errors.add(error(LogicalValidationCode.EXECUTION_ALREADY_ALLOCATED, execId,
                        "execution " + execId + " is already allocated to " + result.allocationId()));
                continue;
            }
            if (mismatch(allocation.instrumentSymbol(), result.instrumentId())) {
                errors.add(error(LogicalValidationCode.EXECUTION_INSTRUMENT_MISMATCH, execId,
                        "execution " + execId + " instrument " + result.instrumentId()
                                + " != " + allocation.instrumentSymbol()));
            }
            if (mismatch(allocation.side(), result.side())) {
                errors.add(error(LogicalValidationCode.EXECUTION_SIDE_MISMATCH, execId,
                        "execution " + execId + " side " + result.side() + " != " + allocation.side()));
            }
            if (result.allocatableQuantity() == null || result.allocatableQuantity().signum() <= 0) {
                errors.add(error(LogicalValidationCode.EXECUTION_NO_QUANTITY, execId,
                        "execution " + execId + " has no allocatable quantity"));
            }
        }
    }

    /** A Replace may retain executions already allocated to the very allocation it replaces. */
    private static boolean retainedByReplace(AllocationView allocation, ExecutionResolution result) {
        return allocation.transType() == com.tradingcapture.allocation.domain.model.AllocTransType.REPLACE
                && allocation.refAllocId() != null
                && allocation.refAllocId().equals(result.allocationId());
    }

    // --- quantity stage ------------------------------------------------------------------------

    private void validateQuantities(AllocationView allocation, List<ExecutionResolution> results,
                                    List<LogicalValidationError> errors) {
        BigDecimal quantity = allocation.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            errors.add(error(LogicalValidationCode.QUANTITY_NOT_POSITIVE, allocation.allocId(),
                    "allocation quantity must be positive"));
        } else if (quantity.scale() > MAX_QUANTITY_SCALE) {
            errors.add(error(LogicalValidationCode.QUANTITY_PRECISION, allocation.allocId(),
                    "allocation quantity exceeds " + MAX_QUANTITY_SCALE + " decimal places"));
        }

        BigDecimal accountTotal = BigDecimal.ZERO;
        for (AccountLine line : allocation.accounts()) {
            BigDecimal qty = line.allocQty();
            if (qty == null || qty.signum() <= 0) {
                errors.add(error(LogicalValidationCode.QUANTITY_NOT_POSITIVE, line.accountId(),
                        "account " + line.accountId() + " allocation quantity must be positive"));
                continue;
            }
            if (qty.scale() > MAX_QUANTITY_SCALE) {
                errors.add(error(LogicalValidationCode.QUANTITY_PRECISION, line.accountId(),
                        "account " + line.accountId() + " quantity exceeds precision"));
            }
            accountTotal = accountTotal.add(qty);
        }
        if (quantity != null && quantity.signum() > 0 && accountTotal.compareTo(quantity) != 0) {
            errors.add(error(LogicalValidationCode.QUANTITY_MISMATCH, allocation.allocId(),
                    "account quantities " + accountTotal.stripTrailingZeros().toPlainString()
                            + " != allocation quantity " + quantity.stripTrailingZeros().toPlainString()));
        }

        if (quantity != null && quantity.signum() > 0 && !allocation.execIds().isEmpty()) {
            BigDecimal allocatable = allocatableTotal(allocation, results);
            if (allocatable.compareTo(quantity) < 0) {
                errors.add(error(LogicalValidationCode.INSUFFICIENT_EXECUTION_QUANTITY, allocation.allocId(),
                        "eligible execution quantity " + allocatable.stripTrailingZeros().toPlainString()
                                + " < allocation quantity " + quantity.stripTrailingZeros().toPlainString()));
            }
        }
    }

    private BigDecimal allocatableTotal(AllocationView allocation, List<ExecutionResolution> results) {
        BigDecimal total = BigDecimal.ZERO;
        for (String execId : new HashSet<>(allocation.execIds())) {
            ExecutionResolution result = find(results, r -> execId.equals(r.execId()));
            if (result != null && result.allocatableQuantity() != null
                    && (result.eligible() || retainedByReplace(allocation, result))) {
                total = total.add(result.allocatableQuantity());
            }
        }
        return total;
    }

    // --- transaction-type stage ----------------------------------------------------------------

    private void validateTransactionType(AllocationView allocation, ExistingAllocationView existing,
                                         List<LogicalValidationError> errors) {
        switch (allocation.transType()) {
            case NEW -> {
                if (existing != null && NEW_CONFLICT_STATES.contains(existing.status())) {
                    errors.add(error(LogicalValidationCode.NEW_CONFLICTING_ALLOCATION, allocation.allocId(),
                            "a non-terminal allocation already exists for " + allocation.allocId()));
                }
            }
            case REPLACE -> {
                if (existing == null) {
                    errors.add(error(LogicalValidationCode.REPLACE_ORIGINAL_NOT_FOUND, allocation.refAllocId(),
                            "original allocation " + allocation.refAllocId() + " was not found"));
                    return;
                }
                if (!REPLACEABLE_OR_CANCELLABLE.contains(existing.status())) {
                    errors.add(error(LogicalValidationCode.REPLACE_ORIGINAL_INELIGIBLE, allocation.refAllocId(),
                            "original allocation is in state " + existing.status()
                                    + " and cannot be replaced"));
                }
                if (mismatch(allocation.instrumentSymbol(), existing.instrumentSymbol())) {
                    errors.add(error(LogicalValidationCode.REPLACE_INSTRUMENT_MISMATCH, allocation.refAllocId(),
                            "replacement instrument differs from the original"));
                }
                if (disjointAccounts(allocation, existing)) {
                    errors.add(error(LogicalValidationCode.REPLACE_ACCOUNT_MISMATCH, allocation.refAllocId(),
                            "replacement shares no account with the original"));
                }
            }
            case CANCEL -> {
                if (existing == null) {
                    errors.add(error(LogicalValidationCode.CANCEL_ORIGINAL_NOT_FOUND, allocation.refAllocId(),
                            "original allocation " + allocation.refAllocId() + " was not found"));
                    return;
                }
                if (!REPLACEABLE_OR_CANCELLABLE.contains(existing.status())) {
                    errors.add(error(LogicalValidationCode.CANCEL_ORIGINAL_INELIGIBLE, allocation.refAllocId(),
                            "original allocation is in state " + existing.status()
                                    + " and cannot be cancelled"));
                }
            }
            default -> errors.add(error(LogicalValidationCode.NEW_CONFLICTING_ALLOCATION, allocation.allocId(),
                    "unsupported transaction type"));
        }
    }

    private static boolean disjointAccounts(AllocationView allocation, ExistingAllocationView existing) {
        if (existing.accountIds() == null || existing.accountIds().isEmpty()) {
            return false;
        }
        Set<String> original = new HashSet<>(existing.accountIds());
        return allocation.accounts().stream().noneMatch(line -> original.contains(line.accountId()));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static <T> T find(List<T> items, java.util.function.Predicate<T> predicate) {
        if (items == null) {
            return null;
        }
        return items.stream().filter(predicate).findFirst().orElse(null);
    }

    private static boolean mismatch(String expected, String actual) {
        return expected != null && actual != null && !expected.equals(actual);
    }

    private static LogicalValidationError error(LogicalValidationCode code, String identifier, String message) {
        return new LogicalValidationError(code, identifier, message);
    }
}
