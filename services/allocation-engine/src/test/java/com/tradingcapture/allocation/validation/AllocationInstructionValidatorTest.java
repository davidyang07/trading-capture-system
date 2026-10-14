package com.tradingcapture.allocation.validation;

import com.tradingcapture.allocation.domain.model.ValidationErrorCode;
import com.tradingcapture.allocation.fix.ExtractedAllocation;
import com.tradingcapture.allocation.fix.FixDictionaryConfig;
import com.tradingcapture.allocation.fix.FixMessageParser;
import com.tradingcapture.allocation.fix.FixParseResult;
import com.tradingcapture.allocation.fix.FixTestMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import quickfix.DataDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Collect-all behavior (ADR-0012): every check below asserts on the COMPLETE error list of
 * a message, proving errors accumulate instead of failing fast.
 */
class AllocationInstructionValidatorTest {

    private static AllocationInstructionValidator validator;
    private static FixMessageParser parser;

    @BeforeAll
    static void setUp() {
        DataDictionary dictionary = new FixDictionaryConfig().fixDataDictionary();
        validator = new AllocationInstructionValidator(dictionary);
        parser = new FixMessageParser(dictionary);
    }

    private static ExtractedAllocation extract(String wire) {
        FixParseResult result = parser.parse(wire);
        assertThat(result).isInstanceOf(FixParseResult.Parsed.class);
        return ((FixParseResult.Parsed) result).extracted();
    }

    @Test
    void validMessageProducesNoErrors() {
        assertThat(validator.validate(extract(FixTestMessages.validNewAllocation()))).isEmpty();
    }

    @Test
    void validCancelWithRefAllocIdProducesNoErrors() {
        assertThat(validator.validate(extract(
                FixTestMessages.validCancel("ALLOC-C1", "ALLOC-0001")))).isEmpty();
    }

    @Test
    void multipleIndependentErrorsAreAllCollected() {
        List<CollectedError> errors = validator.validate(
                extract(FixTestMessages.multiErrorAllocation("ALLOC-BAD")));

        // One message, four independent defects — all reported at once.
        assertThat(errors).extracting(CollectedError::code).contains(
                ValidationErrorCode.MISSING_REQUIRED_FIELD,   // AllocType (626) absent
                ValidationErrorCode.VALUE_NOT_SUPPORTED,      // AllocTransType 9 and Side 5
                ValidationErrorCode.INVALID_VALUE_FORMAT);    // Quantity + TradeDate unparseable
        assertThat(errors.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void newAndReplaceWithoutSettlDateAreMissingARequiredField() {
        for (char transType : new char[] {'0', '1'}) {
            List<CollectedError> errors = validator.validate(
                    extract(FixTestMessages.withoutSettlDate("ALLOC-NOSD-" + transType, transType)));

            assertThat(errors).singleElement().satisfies(error -> {
                assertThat(error.code()).isEqualTo(ValidationErrorCode.MISSING_REQUIRED_FIELD);
                assertThat(error.fields()).extracting(CollectedError.FieldRef::fieldId).containsExactly(64);
            });
        }
    }

    @Test
    void cancelWithoutSettlDateIsValid() {
        assertThat(validator.validate(extract(FixTestMessages.withoutSettlDate("ALLOC-NOSD-C", '2')))).isEmpty();
    }

    @Test
    void everyDictionaryRequiredFieldIsDemanded() {
        ExtractedAllocation empty = new ExtractedAllocation(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of());

        List<CollectedError> errors = validator.validate(empty);

        List<Integer> missingTags = errors.stream()
                .filter(e -> e.code() == ValidationErrorCode.MISSING_REQUIRED_FIELD)
                .flatMap(e -> e.fields().stream())
                .map(CollectedError.FieldRef::fieldId)
                .toList();
        assertThat(missingTags).containsExactlyInAnyOrder(70, 71, 626, 857, 54, 55, 53, 6, 75, 60);
    }

    @Test
    void errorFieldsCarryDictionaryResolvedNames() {
        ExtractedAllocation empty = new ExtractedAllocation(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of());

        List<CollectedError> errors = validator.validate(empty);

        assertThat(errors.stream().flatMap(e -> e.fields().stream()))
                .anyMatch(f -> f.fieldId() == 70 && "AllocID".equals(f.fieldName()))
                .anyMatch(f -> f.fieldId() == 71 && "AllocTransType".equals(f.fieldName()));
    }

    @Test
    void invalidNumbersDatesAndTimestampsAreFormatErrors() {
        ExtractedAllocation bad = withDefaults()
                .quantity("12,5").tradeDate("15-07-2026").transactTime("2026-07-15T13:45:30Z")
                .build();

        List<CollectedError> errors = validator.validate(bad);

        assertThat(errors).extracting(CollectedError::code)
                .containsOnly(ValidationErrorCode.INVALID_VALUE_FORMAT);
        assertThat(errors).hasSize(3);
    }

    @Test
    void microsecondTimestampsAreValid() {
        // Regression (backend review): [.SSS][.SSSSSS] optional patterns don't backtrack,
        // so microsecond TransactTime values were wrongly rejected.
        assertThat(validator.validate(
                withDefaults().transactTime("20260715-13:45:30.123456").build())).isEmpty();
        assertThat(validator.validate(
                withDefaults().transactTime("20260715-13:45:30.123").build())).isEmpty();
    }

    @Test
    void integerDigitOverflowIsOutOfBoundNotATransientFailure() {
        // Regression (backend review): DECIMAL(19,4) holds 15 integer digits; a longer value
        // must be a clean INVALID, never an INSERT failure misrouted to the retry path/DLT.
        List<CollectedError> errors = validator.validate(
                withDefaults().quantity("1234567890123456").build());

        assertThat(errors).extracting(CollectedError::code)
                .containsExactly(ValidationErrorCode.VALUE_OUT_OF_BOUND);
        assertThat(errors.get(0).message()).contains("magnitude");
    }

    @Test
    void identifiersLongerThanTheirStorageColumnsAreOutOfBound() {
        // Regression (backend review): alloc_id is VARCHAR(64); longer values previously
        // overflowed at INSERT time and burned retries as a fake transient failure.
        ExtractedAllocation bad = new ExtractedAllocation(
                "A".repeat(65), "0", "1", "1", null, "1", "100", "101.25",
                "USD", "20260715", "20260715-13:45:30", "0", "20260717", null, null,
                "AAPL", null, null, null, List.of(), List.of(), List.of());

        List<CollectedError> errors = validator.validate(bad);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo(ValidationErrorCode.VALUE_OUT_OF_BOUND);
        assertThat(errors.get(0).fields())
                .extracting(CollectedError.FieldRef::fieldId).containsExactly(70);
    }

    @Test
    void nonPositiveQuantityIsOutOfBound() {
        List<CollectedError> errors = validator.validate(withDefaults().quantity("-10").build());

        assertThat(errors).extracting(CollectedError::code)
                .containsExactly(ValidationErrorCode.VALUE_OUT_OF_BOUND);
    }

    @Test
    void quantityScaleFinerThanStorageIsRejectedNotRounded() {
        List<CollectedError> errors = validator.validate(withDefaults().quantity("100.12345").build());

        assertThat(errors).extracting(CollectedError::code)
                .containsExactly(ValidationErrorCode.INVALID_VALUE_FORMAT);
        assertThat(errors.get(0).message()).contains("scale");
    }

    @Test
    void unsupportedTransTypeAndSideAreValueNotSupported() {
        List<CollectedError> errors = validator.validate(
                withDefaults().allocTransType("7").side("8").build());

        assertThat(errors).extracting(CollectedError::code)
                .containsExactly(ValidationErrorCode.VALUE_NOT_SUPPORTED,
                        ValidationErrorCode.VALUE_NOT_SUPPORTED);
    }

    @Test
    void replaceWithoutRefAllocIdIsAConflict() {
        List<CollectedError> errors = validator.validate(
                extract(FixTestMessages.replaceWithoutRefAllocId("ALLOC-R1")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo(ValidationErrorCode.CONFLICTING_FIELDS);
        assertThat(errors.get(0).fields())
                .extracting(CollectedError.FieldRef::fieldId).contains(71, 72);
    }

    @Test
    void accountQuantitiesMustSumToTheMessageQuantity() {
        List<CollectedError> errors = validator.validate(
                extract(FixTestMessages.sumMismatchAllocation("ALLOC-SUM")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).code()).isEqualTo(ValidationErrorCode.CONFLICTING_FIELDS);
        assertThat(errors.get(0).message()).contains("90").contains("100");
    }

    @Test
    void groupEntriesReportMissingRequiredFieldsWithTheirPosition() {
        ExtractedAllocation bad = withDefaults()
                .orders(List.of(new ExtractedAllocation.Order(null, null, "100", null)))
                .executions(List.of(new ExtractedAllocation.Execution(null, null, "101.25", null)))
                .build();

        List<CollectedError> errors = validator.validate(bad);

        assertThat(errors).extracting(CollectedError::code)
                .containsOnly(ValidationErrorCode.MISSING_REQUIRED_FIELD);
        // ClOrdID + OrderID for the order entry; ExecID + LastQty for the exec entry.
        assertThat(errors).hasSize(4);
        assertThat(errors)
                .anyMatch(e -> e.message().contains("NoOrders entry 1"))
                .anyMatch(e -> e.message().contains("NoExecs entry 1"));
    }

    @Test
    void negativeMiscFeeAmountIsOutOfBound() {
        ExtractedAllocation bad = withDefaults()
                .accounts(List.of(new ExtractedAllocation.Account(
                        "ACC-INV-1", "100", null, null, null, null, null, null,
                        List.of(new ExtractedAllocation.MiscFee("-1.25", "USD", "4", "0")))))
                .build();

        List<CollectedError> errors = validator.validate(bad);

        assertThat(errors).extracting(CollectedError::code)
                .containsExactly(ValidationErrorCode.VALUE_OUT_OF_BOUND);
    }

    // --- builder over a fully valid baseline so each test corrupts exactly one aspect ---

    private static Builder withDefaults() {
        return new Builder();
    }

    private static final class Builder {
        private String allocTransType = "0";
        private String side = "1";
        private String quantity = "100";
        private String tradeDate = "20260715";
        private String transactTime = "20260715-13:45:30";
        private List<ExtractedAllocation.Order> orders = List.of();
        private List<ExtractedAllocation.Execution> executions = List.of();
        private List<ExtractedAllocation.Account> accounts = List.of();

        Builder allocTransType(String v) {
            this.allocTransType = v;
            return this;
        }

        Builder side(String v) {
            this.side = v;
            return this;
        }

        Builder quantity(String v) {
            this.quantity = v;
            return this;
        }

        Builder tradeDate(String v) {
            this.tradeDate = v;
            return this;
        }

        Builder transactTime(String v) {
            this.transactTime = v;
            return this;
        }

        Builder orders(List<ExtractedAllocation.Order> v) {
            this.orders = v;
            return this;
        }

        Builder executions(List<ExtractedAllocation.Execution> v) {
            this.executions = v;
            return this;
        }

        Builder accounts(List<ExtractedAllocation.Account> v) {
            this.accounts = v;
            return this;
        }

        ExtractedAllocation build() {
            return new ExtractedAllocation(
                    "ALLOC-T1", allocTransType, "1", "1", null, side, quantity, "101.25",
                    "USD", tradeDate, transactTime, "0", "20260717", null, null,
                    "AAPL", null, null, null, orders, executions, accounts);
        }
    }
}
