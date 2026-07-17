package com.tradingcapture.allocation.fix;

import quickfix.field.AllocAccount;
import quickfix.field.AllocAvgPx;
import quickfix.field.AllocID;
import quickfix.field.AllocNetMoney;
import quickfix.field.AllocNoOrdersType;
import quickfix.field.AllocQty;
import quickfix.field.AllocTransType;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.Currency;
import quickfix.field.ExecID;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.MiscFeeAmt;
import quickfix.field.MiscFeeBasis;
import quickfix.field.MiscFeeCurr;
import quickfix.field.MiscFeeType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.Quantity;
import quickfix.field.RefAllocID;
import quickfix.field.SettlDate;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TradeDate;
import quickfix.field.TransactTime;
import quickfix.fix44.AllocationInstruction;

import java.time.LocalDateTime;

/**
 * Wire-format FIX 4.4 test messages, built through QuickFIX/J itself so framing (body
 * length, checksum) is always correct — tests then corrupt specific aspects deliberately.
 */
public final class FixTestMessages {

    public static final String VALID_ALLOC_ID = "ALLOC-0001";

    private FixTestMessages() {
    }

    /**
     * A fully valid New allocation: 2 orders, 2 executions, 2 accounts (60 + 40 = Quantity
     * 100), the first account carrying 2 misc fees — exercises every repeating group
     * including the nested one.
     */
    public static String validNewAllocation() {
        return validNewAllocation(VALID_ALLOC_ID);
    }

    public static String validNewAllocation(String allocId) {
        AllocationInstruction message = baseInstruction(allocId, '0');

        message.addGroup(orderGroup("ORD-1", "EXCH-ORD-1", 60));
        message.addGroup(orderGroup("ORD-2", "EXCH-ORD-2", 40));

        message.addGroup(execGroup("EXEC-1", 60, 101.25));
        message.addGroup(execGroup("EXEC-2", 40, 101.30));

        AllocationInstruction.NoAllocs first = accountGroup("ACC-INV-1", 60);
        first.setField(new AllocAvgPx(101.27));
        first.setField(new AllocNetMoney(6076.20));
        AllocationInstruction.NoAllocs.NoMiscFees fee1 = new AllocationInstruction.NoAllocs.NoMiscFees();
        fee1.set(new MiscFeeAmt(1.25));
        fee1.set(new MiscFeeCurr("USD"));
        fee1.set(new MiscFeeType("4")); // Exchange fees
        fee1.set(new MiscFeeBasis(0));
        first.addGroup(fee1);
        AllocationInstruction.NoAllocs.NoMiscFees fee2 = new AllocationInstruction.NoAllocs.NoMiscFees();
        fee2.set(new MiscFeeAmt(0.75));
        fee2.set(new MiscFeeCurr("USD"));
        fee2.set(new MiscFeeType("7")); // Other
        fee2.set(new MiscFeeBasis(0));
        first.addGroup(fee2);
        message.addGroup(first);

        message.addGroup(accountGroup("ACC-INV-2", 40));

        return message.toString();
    }

    /** A Replace allocation without the RefAllocID it requires (cross-field inconsistency). */
    public static String replaceWithoutRefAllocId(String allocId) {
        AllocationInstruction message = baseInstruction(allocId, '1');
        message.addGroup(orderGroup("ORD-1", "EXCH-ORD-1", 100));
        message.addGroup(accountGroup("ACC-INV-1", 100));
        return message.toString();
    }

    /** A valid Cancel referencing a prior allocation. */
    public static String validCancel(String allocId, String refAllocId) {
        AllocationInstruction message = baseInstruction(allocId, '2');
        message.setField(new RefAllocID(refAllocId));
        message.addGroup(orderGroup("ORD-1", "EXCH-ORD-1", 100));
        message.addGroup(accountGroup("ACC-INV-1", 100));
        return message.toString();
    }

    /**
     * Parseable 35=J whose fields are invalid in several independent ways (missing required
     * fields, bad numbers, bad values) so collect-all behavior is observable.
     */
    public static String multiErrorAllocation(String allocId) {
        AllocationInstruction message = new AllocationInstruction();
        message.set(new AllocID(allocId));
        message.set(new AllocTransType('9'));                      // unsupported trans type
        message.set(new AllocNoOrdersType(1));
        // AllocType (626) missing                                  -> missing required
        message.set(new Side('5'));                                 // unsupported side
        message.set(new Symbol("AAPL"));
        message.setString(Quantity.FIELD, "not-a-number");          // invalid number
        message.set(new AvgPx(101.25));
        message.setString(TradeDate.FIELD, "2026-07-15");           // invalid FIX date
        message.set(new TransactTime(LocalDateTime.of(2026, 7, 15, 13, 45, 30)));
        message.addGroup(accountGroup("ACC-INV-1", 100));
        return message.toString();
    }

    /** Structurally valid but logically inconsistent: account quantities don't sum to Quantity. */
    public static String sumMismatchAllocation(String allocId) {
        AllocationInstruction message = baseInstruction(allocId, '0');
        message.addGroup(orderGroup("ORD-1", "EXCH-ORD-1", 100));
        message.addGroup(accountGroup("ACC-INV-1", 60));
        message.addGroup(accountGroup("ACC-INV-2", 30)); // 90 != 100
        return message.toString();
    }

    /** An ExecutionReport (35=8): parseable FIX, unsupported by this service. */
    public static String unsupportedExecutionReport() {
        quickfix.fix44.ExecutionReport report = new quickfix.fix44.ExecutionReport(
                new quickfix.field.OrderID("ORD-1"),
                new ExecID("EXEC-1"),
                new quickfix.field.ExecType(quickfix.field.ExecType.TRADE),
                new quickfix.field.OrdStatus(quickfix.field.OrdStatus.FILLED),
                new Side(Side.BUY),
                new quickfix.field.LeavesQty(0),
                new quickfix.field.CumQty(100),
                new AvgPx(101.25));
        return report.toString();
    }

    /** Not FIX at all. */
    public static String garbage() {
        return "this is not a FIX message";
    }

    /** Structurally broken FIX: framing tags without a parseable body. */
    public static String malformedFix() {
        return "8=FIX.4.4\u00019=20\u000135=J\u0001garbage-without-equals\u000110=000\u0001";
    }

    private static AllocationInstruction baseInstruction(String allocId, char transType) {
        AllocationInstruction message = new AllocationInstruction(
                new AllocID(allocId),
                new AllocTransType(transType),
                new quickfix.field.AllocType(quickfix.field.AllocType.CALCULATED),
                new AllocNoOrdersType(AllocNoOrdersType.EXPLICIT_LIST_PROVIDED),
                new Side(Side.BUY),
                new Quantity(100),
                new AvgPx(101.25),
                new TradeDate("20260715"));
        message.set(new Symbol("AAPL"));
        message.set(new Currency("USD"));
        message.set(new TransactTime(LocalDateTime.of(2026, 7, 15, 13, 45, 30)));
        message.set(new SettlDate("20260717"));
        return message;
    }

    private static AllocationInstruction.NoOrders orderGroup(String clOrdId, String orderId, double qty) {
        AllocationInstruction.NoOrders group = new AllocationInstruction.NoOrders();
        group.set(new ClOrdID(clOrdId));
        group.set(new OrderID(orderId));
        group.set(new OrderQty(qty));
        return group;
    }

    private static AllocationInstruction.NoExecs execGroup(String execId, double lastQty, double lastPx) {
        AllocationInstruction.NoExecs group = new AllocationInstruction.NoExecs();
        group.set(new ExecID(execId));
        group.set(new LastQty(lastQty));
        group.set(new LastPx(lastPx));
        return group;
    }

    private static AllocationInstruction.NoAllocs accountGroup(String account, double qty) {
        AllocationInstruction.NoAllocs group = new AllocationInstruction.NoAllocs();
        group.set(new AllocAccount(account));
        group.set(new AllocQty(qty));
        return group;
    }
}
