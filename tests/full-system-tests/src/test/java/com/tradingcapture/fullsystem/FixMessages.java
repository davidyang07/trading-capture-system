package com.tradingcapture.fullsystem;

import quickfix.field.AllocAccount;
import quickfix.field.AllocID;
import quickfix.field.AllocNoOrdersType;
import quickfix.field.AllocQty;
import quickfix.field.AllocTransType;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.Currency;
import quickfix.field.ExecID;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
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
 * Wire-format FIX 4.4 AllocationInstructions built through QuickFIX/J so framing (body length,
 * checksum) is always correct. Mirrors the allocation-engine unit fixtures but is self-contained
 * for the cross-service module. Callers supply the order/execution/account identifiers that exist
 * in the running services (from the seed data or rows they created).
 */
final class FixMessages {

    private FixMessages() {
    }

    /** A New allocation: one order, one execution, one account, quantities all equal to {@code qty}. */
    static String newAllocation(String allocId, String clOrdId, String orderId, String execId,
                                String account, String symbol, int qty, double price) {
        AllocationInstruction message = base(allocId, '0', symbol, qty, price);
        message.addGroup(order(clOrdId, orderId, qty));
        message.addGroup(exec(execId, qty, price));
        message.addGroup(account(account, qty));
        return message.toString();
    }

    /** A Replace referencing a prior allocation. */
    static String replaceAllocation(String allocId, String refAllocId, String clOrdId, String orderId,
                                    String execId, String account, String symbol, int qty, double price) {
        AllocationInstruction message = base(allocId, '1', symbol, qty, price);
        message.setField(new RefAllocID(refAllocId));
        message.addGroup(order(clOrdId, orderId, qty));
        message.addGroup(exec(execId, qty, price));
        message.addGroup(account(account, qty));
        return message.toString();
    }

    /** A Cancel referencing a prior allocation. */
    static String cancelAllocation(String allocId, String refAllocId, String clOrdId, String orderId,
                                   String symbol, int qty, double price) {
        AllocationInstruction message = base(allocId, '2', symbol, qty, price);
        message.setField(new RefAllocID(refAllocId));
        message.addGroup(order(clOrdId, orderId, qty));
        message.addGroup(account("ACC-INV-1", qty));
        return message.toString();
    }

    /** Parseable 35=J with several independent field errors (collect-all observable). */
    static String multiErrorAllocation(String allocId) {
        AllocationInstruction message = new AllocationInstruction();
        message.set(new AllocID(allocId));
        message.set(new AllocTransType('9'));               // unsupported trans type
        message.set(new AllocNoOrdersType(1));
        message.set(new Side('5'));                          // unsupported side
        message.set(new Symbol("AAPL"));
        message.setString(Quantity.FIELD, "not-a-number");   // invalid number
        message.set(new AvgPx(101.25));
        message.setString(TradeDate.FIELD, "2026-07-15");    // invalid FIX date
        message.set(new TransactTime(LocalDateTime.of(2026, 7, 15, 13, 45, 30)));
        message.addGroup(account("ACC-INV-1", 100));
        return message.toString();
    }

    /** Not FIX at all — the PARSE_FAILED path. */
    static String garbage() {
        return "this is not a FIX message";
    }

    private static AllocationInstruction base(String allocId, char transType, String symbol,
                                              int qty, double price) {
        AllocationInstruction message = new AllocationInstruction(
                new AllocID(allocId),
                new AllocTransType(transType),
                new quickfix.field.AllocType(quickfix.field.AllocType.CALCULATED),
                new AllocNoOrdersType(AllocNoOrdersType.EXPLICIT_LIST_PROVIDED),
                new Side(Side.BUY),
                new Quantity(qty),
                new AvgPx(price),
                new TradeDate("20260715"));
        message.set(new Symbol(symbol));
        message.set(new Currency("USD"));
        message.set(new TransactTime(LocalDateTime.of(2026, 7, 15, 13, 45, 30)));
        message.set(new SettlDate("20260717"));
        return message;
    }

    private static AllocationInstruction.NoOrders order(String clOrdId, String orderId, int qty) {
        AllocationInstruction.NoOrders group = new AllocationInstruction.NoOrders();
        group.set(new ClOrdID(clOrdId));
        group.set(new OrderID(orderId));
        group.set(new OrderQty(qty));
        return group;
    }

    private static AllocationInstruction.NoExecs exec(String execId, int lastQty, double lastPx) {
        AllocationInstruction.NoExecs group = new AllocationInstruction.NoExecs();
        group.set(new ExecID(execId));
        group.set(new LastQty(lastQty));
        group.set(new LastPx(lastPx));
        return group;
    }

    private static AllocationInstruction.NoAllocs account(String account, int qty) {
        AllocationInstruction.NoAllocs group = new AllocationInstruction.NoAllocs();
        group.set(new AllocAccount(account));
        group.set(new AllocQty(qty));
        return group;
    }
}
