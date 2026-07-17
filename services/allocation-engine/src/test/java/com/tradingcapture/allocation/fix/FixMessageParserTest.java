package com.tradingcapture.allocation.fix;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import quickfix.DataDictionary;

import static org.assertj.core.api.Assertions.assertThat;

class FixMessageParserTest {

    private static FixMessageParser parser;

    @BeforeAll
    static void loadDictionary() {
        DataDictionary dictionary = new FixDictionaryConfig().fixDataDictionary();
        parser = new FixMessageParser(dictionary);
    }

    @Test
    void parsesAValidAllocationInstructionWithAllRepeatingGroups() {
        FixParseResult result = parser.parse(FixTestMessages.validNewAllocation());

        assertThat(result).isInstanceOf(FixParseResult.Parsed.class);
        ExtractedAllocation extracted = ((FixParseResult.Parsed) result).extracted();

        assertThat(extracted.allocId()).isEqualTo(FixTestMessages.VALID_ALLOC_ID);
        assertThat(extracted.allocTransType()).isEqualTo("0");
        assertThat(extracted.side()).isEqualTo("1");
        assertThat(extracted.symbol()).isEqualTo("AAPL");
        assertThat(extracted.quantity()).isEqualTo("100");
        assertThat(extracted.tradeDate()).isEqualTo("20260715");
        assertThat(extracted.settlDate()).isEqualTo("20260717");

        // Multiple entries per group, in wire order.
        assertThat(extracted.orders()).hasSize(2);
        assertThat(extracted.orders().get(0).clOrdId()).isEqualTo("ORD-1");
        assertThat(extracted.orders().get(1).clOrdId()).isEqualTo("ORD-2");
        assertThat(extracted.executions()).hasSize(2);
        assertThat(extracted.executions().get(0).execId()).isEqualTo("EXEC-1");
        assertThat(extracted.executions().get(1).lastPx()).isEqualTo("101.3");
        assertThat(extracted.accounts()).hasSize(2);
        assertThat(extracted.accounts().get(0).allocAccount()).isEqualTo("ACC-INV-1");
        assertThat(extracted.accounts().get(1).allocQty()).isEqualTo("40");

        // Nested NoMiscFees inside the first NoAllocs entry.
        assertThat(extracted.accounts().get(0).miscFees()).hasSize(2);
        assertThat(extracted.accounts().get(0).miscFees().get(0).miscFeeAmt()).isEqualTo("1.25");
        assertThat(extracted.accounts().get(0).miscFees().get(1).miscFeeType()).isEqualTo("7");
        assertThat(extracted.accounts().get(1).miscFees()).isEmpty();
    }

    @Test
    void missingRequiredTagsStillParseStructurally() {
        // Missing fields are the collect-all validator's concern, not the parser's.
        FixParseResult result = parser.parse(FixTestMessages.multiErrorAllocation("ALLOC-BAD"));

        assertThat(result).isInstanceOf(FixParseResult.Parsed.class);
        ExtractedAllocation extracted = ((FixParseResult.Parsed) result).extracted();
        assertThat(extracted.allocType()).isNull();
        assertThat(extracted.quantity()).isEqualTo("not-a-number");
    }

    @Test
    void garbageIsMalformed() {
        FixParseResult result = parser.parse(FixTestMessages.garbage());

        assertThat(result).isInstanceOf(FixParseResult.Malformed.class);
        assertThat(((FixParseResult.Malformed) result).summary()).contains("MsgType");
    }

    @Test
    void garbledFieldStructureIsMalformed() {
        FixParseResult result = parser.parse(FixTestMessages.malformedFix());

        assertThat(result).isInstanceOf(FixParseResult.Malformed.class);
    }

    @Test
    void malformedSummariesStayShortAndReadable() {
        FixParseResult result = parser.parse(FixTestMessages.malformedFix());

        String summary = ((FixParseResult.Malformed) result).summary();
        assertThat(summary.length()).isLessThanOrEqualTo(250);
        assertThat(summary).doesNotContain("");
    }

    @Test
    void unsupportedMessageTypeIsClassifiedNotMalformed() {
        FixParseResult result = parser.parse(FixTestMessages.unsupportedExecutionReport());

        assertThat(result).isInstanceOf(FixParseResult.UnsupportedType.class);
        assertThat(((FixParseResult.UnsupportedType) result).msgType()).isEqualTo("8");
    }

    @Test
    void unknownOptionalFieldsAreTolerated() {
        // Inject a user-defined tag (5000) into an otherwise valid message.
        String wire = FixTestMessages.validNewAllocation();
        String withUnknown = wire.replace("55=AAPL", "55=AAPL5000=custom");
        // Rebuilding the checksum: parse leniently instead (QuickFIX/J only checks the
        // checksum during validation, which is off in the parser).

        FixParseResult result = parser.parse(withUnknown);

        assertThat(result).isInstanceOf(FixParseResult.Parsed.class);
        assertThat(((FixParseResult.Parsed) result).extracted().symbol()).isEqualTo("AAPL");
    }
}
