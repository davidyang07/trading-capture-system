package com.tradingcapture.allocation.validation;

import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import com.tradingcapture.allocation.fix.ExtractedAllocation;
import com.tradingcapture.allocation.fix.FixValues;
import com.tradingcapture.allocation.validation.CollectedError.FieldRef;
import org.springframework.stereotype.Component;
import quickfix.DataDictionary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Collect-all field validation for AllocationInstruction (35=J) — ADR-0012. Accumulates
 * EVERY error instead of failing fast (QuickFIX/J's own validator throws on the first).
 * Field names resolve through the FIX 4.4 data dictionary. Scope guard: this validator
 * targets 35=J only; there is deliberately no generic multi-message framework.
 *
 * <p>Stages covered here: structural/dictionary-required presence, per-field format,
 * value-domain checks, and intra-message consistency. Logical validation against other
 * services (orders exist, accounts funded) is Phase 8 orchestration.</p>
 */
@Component
public class AllocationInstructionValidator {

    private static final Set<String> SUPPORTED_TRANS_TYPES = Set.of("0", "1", "2");

    /**
     * FIX float: digits with an optional sign and decimal point. Exponent notation ("1E+3"), which
     * {@link BigDecimal} would otherwise accept, is not a FIX float.
     */
    private static final Pattern FIX_FLOAT = Pattern.compile("-?(\\d+(\\.\\d*)?|\\.\\d+)");
    private static final Set<String> SUPPORTED_SIDES = Set.of("1", "2");

    // Money/quantity values persist as DECIMAL(19,4), prices as DECIMAL(19,8) (ADR-0014):
    // a finer scale would be silently rounded and an oversized integer part would overflow
    // the column, so both are rejected here instead of failing at INSERT time.
    private static final int QTY_SCALE = FixValues.QTY_SCALE;
    private static final int PX_SCALE = FixValues.PX_SCALE;

    private final DataDictionary dictionary;

    public AllocationInstructionValidator(DataDictionary fixDataDictionary) {
        this.dictionary = fixDataDictionary;
    }

    public List<CollectedError> validate(ExtractedAllocation m) {
        List<CollectedError> errors = new ArrayList<>();

        // --- dictionary-required message-level fields (35=J) ---
        requireField(errors, 70, m.allocId());
        requireField(errors, 71, m.allocTransType());
        requireField(errors, 626, m.allocType());
        requireField(errors, 857, m.allocNoOrdersType());
        requireField(errors, 54, m.side());
        requireField(errors, 55, m.symbol());
        requireField(errors, 53, m.quantity());
        requireField(errors, 6, m.avgPx());
        requireField(errors, 75, m.tradeDate());
        requireField(errors, 60, m.transactTime());
        // SettlDate is optional in FIX 4.4 but required here for New/Replace: those complete by
        // publishing SettlementRequested, whose settlementDate is mandatory. Without it the
        // allocation would only fail after its executions were bound in trading-service, so it is
        // rejected at intake instead (a Cancel never settles and may omit it).
        if ("0".equals(m.allocTransType()) || "1".equals(m.allocTransType())) {
            requireField(errors, 64, m.settlDate());
        }

        // --- field formats & value domains ---
        BigDecimal quantity = checkDecimal(errors, 53, m.quantity(), QTY_SCALE);
        checkPositive(errors, 53, m.quantity(), quantity);
        checkDecimal(errors, 6, m.avgPx(), PX_SCALE);
        checkDecimal(errors, 118, m.netMoney(), QTY_SCALE);
        checkDecimal(errors, 381, m.grossTradeAmt(), QTY_SCALE);
        checkInteger(errors, 626, m.allocType());
        checkInteger(errors, 857, m.allocNoOrdersType());
        checkDate(errors, 75, m.tradeDate());
        checkDate(errors, 64, m.settlDate());
        checkTimestamp(errors, 60, m.transactTime());

        // --- storage-width limits (mirror V1__allocation_schema.sql column widths so an
        //     oversized value becomes a clean INVALID, never an INSERT failure) ---
        checkMaxLength(errors, 70, m.allocId(), 64);
        checkMaxLength(errors, 72, m.refAllocId(), 64);
        checkMaxLength(errors, 626, m.allocType(), 3);
        checkMaxLength(errors, 857, m.allocNoOrdersType(), 2);
        checkMaxLength(errors, 15, m.currency(), 3);
        checkMaxLength(errors, 63, m.settlType(), 2);
        checkMaxLength(errors, 55, m.symbol(), 32);
        checkMaxLength(errors, 48, m.securityId(), 64);
        checkMaxLength(errors, 22, m.securityIdSource(), 2);
        checkMaxLength(errors, 207, m.securityExchange(), 10);

        if (m.allocTransType() != null && !SUPPORTED_TRANS_TYPES.contains(m.allocTransType())) {
            errors.add(CollectedError.of(ValidationErrorCode.VALUE_NOT_SUPPORTED,
                    fieldName(71) + " must be 0 (New), 1 (Replace) or 2 (Cancel)",
                    ref(71, m.allocTransType())));
        }
        if (m.side() != null && !SUPPORTED_SIDES.contains(m.side())) {
            errors.add(CollectedError.of(ValidationErrorCode.VALUE_NOT_SUPPORTED,
                    fieldName(54) + " must be 1 (Buy) or 2 (Sell)",
                    ref(54, m.side())));
        }

        // --- intra-message consistency ---
        boolean replaceOrCancel = "1".equals(m.allocTransType()) || "2".equals(m.allocTransType());
        if (replaceOrCancel && m.refAllocId() == null) {
            errors.add(CollectedError.of(ValidationErrorCode.CONFLICTING_FIELDS,
                    fieldName(71) + "=" + m.allocTransType() + " (Replace/Cancel) requires "
                            + fieldName(72),
                    ref(71, m.allocTransType()), ref(72, null)));
        }

        validateGroupCounts(errors, m.groupCounts());
        validateOrders(errors, m.orders());
        validateExecutions(errors, m.executions());
        BigDecimal allocQtySum = validateAccounts(errors, m.accounts());

        if (quantity != null && allocQtySum != null && !m.accounts().isEmpty()
                && quantity.compareTo(allocQtySum) != 0) {
            errors.add(CollectedError.of(ValidationErrorCode.CONFLICTING_FIELDS,
                    "Sum of " + fieldName(80) + " across " + fieldName(78) + " entries ("
                            + allocQtySum.toPlainString() + ") must equal " + fieldName(53)
                            + " (" + quantity.toPlainString() + ")",
                    ref(53, m.quantity()), ref(80, allocQtySum.toPlainString())));
        }

        return errors;
    }

    /** A NumInGroup that disagrees with the entries present means entries were lost or invented. */
    private void validateGroupCounts(List<CollectedError> errors, List<ExtractedAllocation.GroupCount> counts) {
        for (ExtractedAllocation.GroupCount count : counts) {
            if (!declares(count.declared(), count.parsed())) {
                String where = count.ownerEntry() == null ? ""
                        : " in " + fieldName(78) + " entry " + (count.ownerEntry() + 1);
                errors.add(CollectedError.of(ValidationErrorCode.CONFLICTING_FIELDS,
                        fieldName(count.countTag()) + " (" + count.countTag() + ") declares " + count.declared()
                                + " entries but the message carries " + count.parsed() + where,
                        ref(count.countTag(), count.declared())));
            }
        }
    }

    private static boolean declares(String declared, int parsed) {
        try {
            return Integer.parseInt(declared.strip()) == parsed;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void validateOrders(List<CollectedError> errors, List<ExtractedAllocation.Order> orders) {
        for (int i = 0; i < orders.size(); i++) {
            ExtractedAllocation.Order order = orders.get(i);
            requireGroupField(errors, 73, i, 11, order.clOrdId());
            requireGroupField(errors, 73, i, 37, order.orderId());
            checkMaxLength(errors, 11, order.clOrdId(), 64);
            checkMaxLength(errors, 37, order.orderId(), 64);
            checkDecimal(errors, 38, order.orderQty(), QTY_SCALE);
            checkDecimal(errors, 799, order.orderAvgPx(), PX_SCALE);
        }
    }

    private void validateExecutions(List<CollectedError> errors, List<ExtractedAllocation.Execution> executions) {
        for (int i = 0; i < executions.size(); i++) {
            ExtractedAllocation.Execution execution = executions.get(i);
            requireGroupField(errors, 124, i, 17, execution.execId());
            requireGroupField(errors, 124, i, 32, execution.lastQty());
            checkMaxLength(errors, 17, execution.execId(), 64);
            checkMaxLength(errors, 29, execution.lastCapacity(), 2);
            BigDecimal lastQty = checkDecimal(errors, 32, execution.lastQty(), QTY_SCALE);
            checkPositive(errors, 32, execution.lastQty(), lastQty);
            checkDecimal(errors, 31, execution.lastPx(), PX_SCALE);
        }
    }

    private BigDecimal validateAccounts(List<CollectedError> errors, List<ExtractedAllocation.Account> accounts) {
        BigDecimal sum = null;
        for (int i = 0; i < accounts.size(); i++) {
            ExtractedAllocation.Account account = accounts.get(i);
            requireGroupField(errors, 78, i, 79, account.allocAccount());
            requireGroupField(errors, 78, i, 80, account.allocQty());
            checkMaxLength(errors, 79, account.allocAccount(), 64);
            checkMaxLength(errors, 736, account.allocSettlCurrency(), 3);
            checkMaxLength(errors, 161, account.allocText(), 255);
            BigDecimal allocQty = checkDecimal(errors, 80, account.allocQty(), QTY_SCALE);
            checkPositive(errors, 80, account.allocQty(), allocQty);
            checkDecimal(errors, 366, account.allocPrice(), PX_SCALE);
            checkDecimal(errors, 153, account.allocAvgPx(), PX_SCALE);
            checkDecimal(errors, 154, account.allocNetMoney(), QTY_SCALE);
            checkDecimal(errors, 737, account.allocSettlCurrAmt(), QTY_SCALE);
            if (allocQty != null) {
                sum = sum == null ? allocQty : sum.add(allocQty);
            }
            for (ExtractedAllocation.MiscFee fee : account.miscFees()) {
                BigDecimal feeAmt = checkDecimal(errors, 137, fee.miscFeeAmt(), QTY_SCALE);
                checkPositive(errors, 137, fee.miscFeeAmt(), feeAmt);
                checkInteger(errors, 891, fee.miscFeeBasis());
                checkMaxLength(errors, 138, fee.miscFeeCurr(), 3);
                checkMaxLength(errors, 139, fee.miscFeeType(), 3);
                checkMaxLength(errors, 891, fee.miscFeeBasis(), 2);
            }
        }
        return sum;
    }

    // --- primitive checks -------------------------------------------------------------

    private void requireField(List<CollectedError> errors, int tag, String value) {
        if (value == null || value.isBlank()) {
            errors.add(CollectedError.of(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    fieldName(tag) + " (" + tag + ") is required", ref(tag, null)));
        }
    }

    private void requireGroupField(List<CollectedError> errors, int groupTag, int entryIndex,
                                   int tag, String value) {
        if (value == null || value.isBlank()) {
            errors.add(CollectedError.of(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    fieldName(tag) + " (" + tag + ") is required in " + fieldName(groupTag)
                            + " entry " + (entryIndex + 1), ref(tag, null)));
        }
    }

    /**
     * Parses a decimal; reports format errors, scale overflows, and integer-digit overflows
     * of the DECIMAL(19,scale) storage. Returns null when invalid/absent.
     */
    private BigDecimal checkDecimal(List<CollectedError> errors, int tag, String raw, int maxScale) {
        if (raw == null) {
            return null;
        }
        if (!FIX_FLOAT.matcher(raw).matches()) {
            errors.add(CollectedError.of(ValidationErrorCode.INVALID_VALUE_FORMAT,
                    fieldName(tag) + " is not a valid number", ref(tag, raw)));
            return null;
        }
        BigDecimal value = new BigDecimal(raw);
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > maxScale) {
            errors.add(CollectedError.of(ValidationErrorCode.INVALID_VALUE_FORMAT,
                    fieldName(tag) + " exceeds the supported scale of " + maxScale + " decimals",
                    ref(tag, raw)));
            return null;
        }
        // precision − scale: a negative scale counts as integer digits too (1E+19 has 20 of them).
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > FixValues.DECIMAL_PRECISION - maxScale) {
            errors.add(CollectedError.of(ValidationErrorCode.VALUE_OUT_OF_BOUND,
                    fieldName(tag) + " exceeds the supported magnitude of "
                            + (FixValues.DECIMAL_PRECISION - maxScale) + " integer digits",
                    ref(tag, raw)));
            return null;
        }
        return value;
    }

    private void checkMaxLength(List<CollectedError> errors, int tag, String raw, int maxLength) {
        if (raw != null && raw.length() > maxLength) {
            errors.add(CollectedError.of(ValidationErrorCode.VALUE_OUT_OF_BOUND,
                    fieldName(tag) + " exceeds the supported length of " + maxLength + " characters",
                    ref(tag, raw.substring(0, Math.min(raw.length(), 64)) + "…")));
        }
    }

    private void checkPositive(List<CollectedError> errors, int tag, String raw, BigDecimal parsed) {
        if (parsed != null && parsed.signum() <= 0) {
            errors.add(CollectedError.of(ValidationErrorCode.VALUE_OUT_OF_BOUND,
                    fieldName(tag) + " must be greater than zero", ref(tag, raw)));
        }
    }

    private void checkInteger(List<CollectedError> errors, int tag, String raw) {
        if (raw == null) {
            return;
        }
        try {
            Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add(CollectedError.of(ValidationErrorCode.INVALID_VALUE_FORMAT,
                    fieldName(tag) + " is not a valid integer", ref(tag, raw)));
        }
    }

    private void checkDate(List<CollectedError> errors, int tag, String raw) {
        if (raw == null) {
            return;
        }
        try {
            LocalDate.parse(raw, FixValues.LOCAL_MKT_DATE);
        } catch (DateTimeParseException e) {
            errors.add(CollectedError.of(ValidationErrorCode.INVALID_VALUE_FORMAT,
                    fieldName(tag) + " is not a valid date (yyyyMMdd)", ref(tag, raw)));
        }
    }

    private void checkTimestamp(List<CollectedError> errors, int tag, String raw) {
        if (raw == null) {
            return;
        }
        try {
            FixValues.UTC_TIMESTAMP.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(CollectedError.of(ValidationErrorCode.INVALID_VALUE_FORMAT,
                    fieldName(tag) + " is not a valid UTC timestamp (yyyyMMdd-HH:mm:ss[.fff])",
                    ref(tag, raw)));
        }
    }

    private FieldRef ref(int tag, String value) {
        return new FieldRef(tag, fieldName(tag), value);
    }

    private String fieldName(int tag) {
        String name = dictionary.getFieldName(tag);
        return name == null ? "Tag" + tag : name;
    }
}
