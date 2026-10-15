package com.tradingcapture.allocation.fix;

import org.springframework.stereotype.Component;
import quickfix.DataDictionary;
import quickfix.FieldMap;
import quickfix.FieldNotFound;
import quickfix.Group;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.MessageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * QuickFIX/J structural parsing of inbound FIX strings into the untyped
 * {@link ExtractedAllocation} view (ADR-0012). Parsing runs with dictionary-driven group
 * handling but with QuickFIX/J's own validation OFF: its validator throws on the first
 * error, while this pipeline must collect every field error afterwards
 * ({@code AllocationInstructionValidator}). Repeating-group extraction generalizes the
 * reference {@code ConvertUtils} pattern (reuse-matrix) from ExecutionReport to
 * AllocationInstruction (NoOrders / NoExecs / NoAllocs / nested NoMiscFees).
 *
 * <p>Encoding: FIX 4.4 payloads are ASCII; the listener consumes them as UTF-8 strings,
 * which is a superset — non-ASCII bytes surface as validation failures, never corruption.</p>
 */
@Component
public class FixMessageParser {

    /** AllocationInstruction message type (35=J) — the only type this service ingests. */
    public static final String SUPPORTED_MSG_TYPE = "J";

    // FIX tag numbers used for extraction (names resolved via the dictionary for errors).
    static final int ALLOC_ID = 70;
    static final int ALLOC_TRANS_TYPE = 71;
    static final int REF_ALLOC_ID = 72;
    static final int ALLOC_TYPE = 626;
    static final int ALLOC_NO_ORDERS_TYPE = 857;
    static final int SIDE = 54;
    static final int QUANTITY = 53;
    static final int AVG_PX = 6;
    static final int CURRENCY = 15;
    static final int TRADE_DATE = 75;
    static final int TRANSACT_TIME = 60;
    static final int SETTL_TYPE = 63;
    static final int SETTL_DATE = 64;
    static final int NET_MONEY = 118;
    static final int GROSS_TRADE_AMT = 381;
    static final int SYMBOL = 55;
    static final int SECURITY_ID = 48;
    static final int SECURITY_ID_SOURCE = 22;
    static final int SECURITY_EXCHANGE = 207;

    static final int NO_ORDERS = 73;
    static final int CL_ORD_ID = 11;
    static final int ORDER_ID = 37;
    static final int ORDER_QTY = 38;
    static final int ORDER_AVG_PX = 799;

    static final int NO_EXECS = 124;
    static final int EXEC_ID = 17;
    static final int LAST_QTY = 32;
    static final int LAST_PX = 31;
    static final int LAST_CAPACITY = 29;

    static final int NO_ALLOCS = 78;
    static final int ALLOC_ACCOUNT = 79;
    static final int ALLOC_QTY = 80;
    static final int ALLOC_PRICE = 366;
    static final int ALLOC_AVG_PX = 153;
    static final int ALLOC_NET_MONEY = 154;
    static final int ALLOC_SETTL_CURRENCY = 736;
    static final int ALLOC_SETTL_CURR_AMT = 737;
    static final int ALLOC_TEXT = 161;

    static final int NO_MISC_FEES = 136;
    static final int MISC_FEE_AMT = 137;
    static final int MISC_FEE_CURR = 138;
    static final int MISC_FEE_TYPE = 139;
    static final int MISC_FEE_BASIS = 891;

    private final DataDictionary dictionary;

    public FixMessageParser(DataDictionary fixDataDictionary) {
        this.dictionary = fixDataDictionary;
    }

    public FixParseResult parse(String rawPayload) {
        String msgType;
        try {
            msgType = MessageUtils.getMessageType(rawPayload);
        } catch (InvalidMessage e) {
            return new FixParseResult.Malformed(safeSummary("MsgType (35) unreadable", e));
        }

        if (!SUPPORTED_MSG_TYPE.equals(msgType)) {
            return new FixParseResult.UnsupportedType(msgType);
        }

        Message message = new Message();
        try {
            // Dictionary present for group structure; doValidation=false so the collect-all
            // validator sees the whole message instead of QuickFIX/J's first-error throw.
            message.fromString(rawPayload, dictionary, false);
        } catch (InvalidMessage e) {
            return new FixParseResult.Malformed(safeSummary("FIX syntax invalid", e));
        }
        if (message.getException() != null) {
            // fromString captures some parse defects (e.g. garbled field structure) here
            // instead of throwing.
            return new FixParseResult.Malformed(
                    safeSummary("FIX field structure invalid", message.getException()));
        }

        return new FixParseResult.Parsed(extract(message));
    }

    private ExtractedAllocation extract(Message message) {
        List<ExtractedAllocation.Order> orders = new ArrayList<>();
        for (Group group : message.getGroups(NO_ORDERS)) {
            orders.add(new ExtractedAllocation.Order(
                    opt(group, CL_ORD_ID),
                    opt(group, ORDER_ID),
                    opt(group, ORDER_QTY),
                    opt(group, ORDER_AVG_PX)));
        }

        List<ExtractedAllocation.Execution> executions = new ArrayList<>();
        for (Group group : message.getGroups(NO_EXECS)) {
            executions.add(new ExtractedAllocation.Execution(
                    opt(group, EXEC_ID),
                    opt(group, LAST_QTY),
                    opt(group, LAST_PX),
                    opt(group, LAST_CAPACITY)));
        }

        List<ExtractedAllocation.GroupCount> groupCounts = new ArrayList<>();
        countOf(message, NO_ORDERS, null, groupCounts);
        countOf(message, NO_EXECS, null, groupCounts);
        countOf(message, NO_ALLOCS, null, groupCounts);

        List<ExtractedAllocation.Account> accounts = new ArrayList<>();
        for (Group group : message.getGroups(NO_ALLOCS)) {
            countOf(group, NO_MISC_FEES, accounts.size(), groupCounts);
            List<ExtractedAllocation.MiscFee> fees = new ArrayList<>();
            for (Group feeGroup : group.getGroups(NO_MISC_FEES)) {
                fees.add(new ExtractedAllocation.MiscFee(
                        opt(feeGroup, MISC_FEE_AMT),
                        opt(feeGroup, MISC_FEE_CURR),
                        opt(feeGroup, MISC_FEE_TYPE),
                        opt(feeGroup, MISC_FEE_BASIS)));
            }
            accounts.add(new ExtractedAllocation.Account(
                    opt(group, ALLOC_ACCOUNT),
                    opt(group, ALLOC_QTY),
                    opt(group, ALLOC_PRICE),
                    opt(group, ALLOC_AVG_PX),
                    opt(group, ALLOC_NET_MONEY),
                    opt(group, ALLOC_SETTL_CURRENCY),
                    opt(group, ALLOC_SETTL_CURR_AMT),
                    opt(group, ALLOC_TEXT),
                    fees));
        }

        return new ExtractedAllocation(
                opt(message, ALLOC_ID),
                opt(message, ALLOC_TRANS_TYPE),
                opt(message, ALLOC_TYPE),
                opt(message, ALLOC_NO_ORDERS_TYPE),
                opt(message, REF_ALLOC_ID),
                opt(message, SIDE),
                opt(message, QUANTITY),
                opt(message, AVG_PX),
                opt(message, CURRENCY),
                opt(message, TRADE_DATE),
                opt(message, TRANSACT_TIME),
                opt(message, SETTL_TYPE),
                opt(message, SETTL_DATE),
                opt(message, NET_MONEY),
                opt(message, GROSS_TRADE_AMT),
                opt(message, SYMBOL),
                opt(message, SECURITY_ID),
                opt(message, SECURITY_ID_SOURCE),
                opt(message, SECURITY_EXCHANGE),
                orders,
                executions,
                accounts,
                groupCounts);
    }

    /** Records a NumInGroup field present on {@code fieldMap} next to its parsed entry count. */
    private static void countOf(FieldMap fieldMap, int countTag, Integer ownerEntry,
                                List<ExtractedAllocation.GroupCount> counts) {
        String declared = opt(fieldMap, countTag);
        if (declared != null) {
            counts.add(new ExtractedAllocation.GroupCount(countTag, ownerEntry, declared,
                    fieldMap.getGroups(countTag).size()));
        }
    }

    /** Raw string value of a field, or null when absent — never an exception. */
    private static String opt(FieldMap fieldMap, int tag) {
        if (!fieldMap.isSetField(tag)) {
            return null;
        }
        try {
            return fieldMap.getString(tag);
        } catch (FieldNotFound e) {
            // Unreachable after isSetField; kept explicit rather than swallowed.
            throw new IllegalStateException("Field " + tag + " vanished between isSetField and getString", e);
        }
    }

    /** Exception summaries stay short, readable, and stack-trace-free (logged and persisted). */
    private static String safeSummary(String prefix, Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        detail = detail.replace('', '|');
        if (detail.length() > 200) {
            detail = detail.substring(0, 200);
        }
        return prefix + ": " + detail;
    }
}
