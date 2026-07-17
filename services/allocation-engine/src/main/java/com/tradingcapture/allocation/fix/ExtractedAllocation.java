package com.tradingcapture.allocation.fix;

import java.util.List;

/**
 * Raw string view of an AllocationInstruction (35=J): every field exactly as it appeared
 * on the wire, {@code null} when absent. Extraction is deliberately untyped so the
 * collect-all validator (ADR-0012) can report format errors field by field instead of
 * failing on the first typed getter; the persistence mapper applies typed conversion later.
 *
 * @param orders     NoOrders (73) entries, wire order
 * @param executions NoExecs (124) entries, wire order
 * @param accounts   NoAllocs (78) entries, wire order
 */
public record ExtractedAllocation(
        String allocId,
        String allocTransType,
        String allocType,
        String allocNoOrdersType,
        String refAllocId,
        String side,
        String quantity,
        String avgPx,
        String currency,
        String tradeDate,
        String transactTime,
        String settlType,
        String settlDate,
        String netMoney,
        String grossTradeAmt,
        String symbol,
        String securityId,
        String securityIdSource,
        String securityExchange,
        List<Order> orders,
        List<Execution> executions,
        List<Account> accounts) {

    /** NoOrders (73) group entry. */
    public record Order(String clOrdId, String orderId, String orderQty, String orderAvgPx) {
    }

    /** NoExecs (124) group entry. */
    public record Execution(String execId, String lastQty, String lastPx, String lastCapacity) {
    }

    /** NoAllocs (78) group entry with its nested NoMiscFees (136) entries. */
    public record Account(String allocAccount, String allocQty, String allocPrice, String allocAvgPx,
                          String allocNetMoney, String allocSettlCurrency, String allocSettlCurrAmt,
                          String allocText, List<MiscFee> miscFees) {
    }

    /** NoMiscFees (136) group entry (nested inside NoAllocs in FIX 4.4). */
    public record MiscFee(String miscFeeAmt, String miscFeeCurr, String miscFeeType, String miscFeeBasis) {
    }
}
