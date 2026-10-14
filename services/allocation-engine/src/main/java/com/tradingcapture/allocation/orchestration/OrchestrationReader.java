package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.AllocationAccount;
import com.tradingcapture.allocation.domain.model.AllocationExecution;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.ExistingAllocationView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only loader that materializes an allocation (and, for Replace/Cancel, its referenced
 * original) into an immutable, lazy-free {@link Snapshot} inside a short read transaction. The
 * orchestrator then makes its remote calls and runs validation/selection against the snapshot —
 * outside any database transaction — so nothing triggers a lazy load on a detached entity.
 */
@Component
public class OrchestrationReader {

    /** Everything the orchestration steps need, with collections copied to plain lists. */
    public record Snapshot(Long id,
                           AllocationStatus status,
                           AllocTransType transType,
                           String allocId,
                           String refAllocId,
                           Long rawMessageId,
                           int attemptCount,
                           int maxAttempts,
                           AllocationView view) {

        public boolean attemptsExhausted() {
            return attemptCount >= maxAttempts;
        }
    }

    private final AllocationRepository allocations;

    public OrchestrationReader(AllocationRepository allocations) {
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> load(Long id) {
        return allocations.findById(id).map(a -> {
            List<AccountLine> accounts = a.getAccounts().stream()
                    .map(acct -> new AccountLine(acct.getAllocAccount(), acct.getAllocQty(),
                            acct.getAllocNetMoney(), acct.getAllocSettlCurrency()))
                    .toList();
            List<String> execIds = a.getExecutions().stream()
                    .map(AllocationExecution::getExecId).toList();
            AllocationView view = new AllocationView(a.getAllocId(), a.getAllocTransType(),
                    a.getRefAllocId(), a.getSide(), a.getQuantity(), a.getCurrency(), a.getSymbol(),
                    a.getSecurityExchange(), a.getSettlType(), accounts, execIds);
            return new Snapshot(a.getId(), a.getStatus(), a.getAllocTransType(), a.getAllocId(),
                    a.getRefAllocId(), a.getRawMessageId(), a.getAttemptCount(), a.getMaxAttempts(), view);
        });
    }

    /** The allocation {@code refAllocId} refers to (New or Replace, live preferred); see the repository. */
    @Transactional(readOnly = true)
    public Optional<ExistingAllocationView> existingOriginal(String refAllocId, Long referencingId) {
        return allocations.findReferencedOriginal(refAllocId, referencingId).map(orig -> {
            List<String> accountIds = orig.getAccounts().stream()
                    .map(AllocationAccount::getAllocAccount).toList();
            return new ExistingAllocationView(orig.getAllocId(), orig.getAllocTransType(),
                    orig.getStatus(), orig.getSymbol(), accountIds);
        });
    }
}
