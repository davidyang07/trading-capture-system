package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationAccount;
import com.tradingcapture.allocation.domain.model.AllocationExecution;
import com.tradingcapture.allocation.domain.model.AllocationMiscFee;
import com.tradingcapture.allocation.domain.model.AllocationOrder;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.fix.ExtractedAllocation;
import com.tradingcapture.allocation.fix.FixValues;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Builds the persistent {@link Allocation} aggregate from the untyped extraction. Conversion
 * is lenient by design: for INVALID allocations the typed columns hold whatever converted
 * cleanly (the verbatim payload lives on raw_messages), while for RECEIVED allocations the
 * validator has already guaranteed every conversion below succeeds.
 */
@Component
public class ParsedAllocationMapper {

    /** Formats and scales shared with the validator (single definition in {@link FixValues}). */
    private static final int QTY_SCALE = FixValues.QTY_SCALE;
    private static final int PX_SCALE = FixValues.PX_SCALE;

    public Allocation toAllocation(ExtractedAllocation m, AllocTransType transType,
                                   AllocationStatus initialStatus, Long rawMessageId, Instant now) {
        Allocation allocation = new Allocation(m.allocId(), transType, initialStatus, rawMessageId, now);
        allocation.setInstructionFields(
                m.allocType(),
                m.allocNoOrdersType(),
                m.refAllocId(),
                m.side(),
                decimalOrNull(m.quantity(), QTY_SCALE),
                decimalOrNull(m.avgPx(), PX_SCALE),
                m.currency(),
                dateOrNull(m.tradeDate()),
                timestampOrNull(m.transactTime()),
                m.settlType(),
                dateOrNull(m.settlDate()),
                decimalOrNull(m.netMoney(), QTY_SCALE),
                decimalOrNull(m.grossTradeAmt(), QTY_SCALE));
        allocation.setInstrumentFields(m.symbol(), m.securityId(), m.securityIdSource(), m.securityExchange());

        int seq = 0;
        for (ExtractedAllocation.Order order : m.orders()) {
            allocation.addOrder(new AllocationOrder(seq++,
                    order.clOrdId(), order.orderId(),
                    decimalOrNull(order.orderQty(), QTY_SCALE),
                    decimalOrNull(order.orderAvgPx(), PX_SCALE), now));
        }

        seq = 0;
        for (ExtractedAllocation.Execution execution : m.executions()) {
            allocation.addExecution(new AllocationExecution(seq++,
                    execution.execId(),
                    decimalOrNull(execution.lastQty(), QTY_SCALE),
                    decimalOrNull(execution.lastPx(), PX_SCALE),
                    execution.lastCapacity(), now));
        }

        seq = 0;
        for (ExtractedAllocation.Account account : m.accounts()) {
            AllocationAccount entity = new AllocationAccount(seq++,
                    account.allocAccount(),
                    decimalOrNull(account.allocQty(), QTY_SCALE),
                    decimalOrNull(account.allocPrice(), PX_SCALE),
                    decimalOrNull(account.allocAvgPx(), PX_SCALE),
                    decimalOrNull(account.allocNetMoney(), QTY_SCALE),
                    account.allocSettlCurrency(),
                    decimalOrNull(account.allocSettlCurrAmt(), QTY_SCALE),
                    account.allocText(), now);
            int feeSeq = 0;
            for (ExtractedAllocation.MiscFee fee : account.miscFees()) {
                entity.addMiscFee(new AllocationMiscFee(feeSeq++,
                        decimalOrNull(fee.miscFeeAmt(), QTY_SCALE),
                        fee.miscFeeCurr(), fee.miscFeeType(), fee.miscFeeBasis(), now));
            }
            allocation.addAccount(entity);
        }

        return allocation;
    }

    /**
     * Parses to the storage scale without ever rounding a value: anything that cannot be
     * represented exactly stays null (the validator has already reported it for RECEIVED
     * candidates; for INVALID rows the verbatim payload is the source of truth).
     */
    private static BigDecimal decimalOrNull(String raw, int scale) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw).setScale(scale, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static LocalDate dateOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw, FixValues.LOCAL_MKT_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Instant timestampOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, FixValues.UTC_TIMESTAMP).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
