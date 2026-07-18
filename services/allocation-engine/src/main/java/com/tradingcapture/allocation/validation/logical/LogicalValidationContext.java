package com.tradingcapture.allocation.validation.logical;

import com.tradingcapture.allocation.client.AccountValidationClient.AccountValidation;
import com.tradingcapture.allocation.client.InstrumentResolutionClient.InstrumentResolution;
import com.tradingcapture.allocation.client.TradingExecutionClient.ExecutionResolution;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.AllocationStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed, framework-free input to logical validation: a projection of the accepted Phase 7
 * instruction plus the already-resolved account/instrument/execution results and (for Replace/Cancel)
 * a projection of the referenced original allocation. No HTTP responses or JPA entities cross this
 * boundary — the orchestration driver builds this from the client results and repository lookups.
 *
 * @param supportedSettlTypes optional set of FIX settlement-type codes the instrument supports;
 *                            null skips the settlement-compatibility check (enrichment settlement
 *                            wiring is completed with the orchestration flows).
 * @param existingAllocation  the referenced original (Replace/Cancel) or null.
 */
public record LogicalValidationContext(AllocationView allocation,
                                       List<AccountValidation> accountResults,
                                       List<InstrumentResolution> instrumentResults,
                                       List<ExecutionResolution> executionResults,
                                       List<String> supportedSettlTypes,
                                       ExistingAllocationView existingAllocation) {

    /** Projection of the parsed allocation instruction (never the JPA entity). */
    public record AllocationView(String allocId,
                                 AllocTransType transType,
                                 String refAllocId,
                                 String side,
                                 BigDecimal quantity,
                                 String currency,
                                 String instrumentSymbol,
                                 String securityExchange,
                                 String settlType,
                                 List<AccountLine> accounts,
                                 List<String> execIds) {
    }

    /** One NoAllocs line: account + allocated quantity (+ optional settlement money/currency). */
    public record AccountLine(String accountId,
                              BigDecimal allocQty,
                              BigDecimal netMoney,
                              String settlCurrency) {
    }

    /** Projection of a referenced original allocation for Replace/Cancel prerequisites. */
    public record ExistingAllocationView(String allocId,
                                         AllocTransType transType,
                                         AllocationStatus status,
                                         String instrumentSymbol,
                                         List<String> accountIds) {
    }
}
