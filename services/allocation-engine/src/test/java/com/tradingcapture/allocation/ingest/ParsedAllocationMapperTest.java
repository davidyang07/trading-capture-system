package com.tradingcapture.allocation.ingest;

import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.fix.ExtractedAllocation;
import com.tradingcapture.allocation.fix.FixDictionaryConfig;
import com.tradingcapture.allocation.fix.FixMessageParser;
import com.tradingcapture.allocation.fix.FixParseResult;
import com.tradingcapture.allocation.fix.FixTestMessages;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedAllocationMapperTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private final ParsedAllocationMapper mapper = new ParsedAllocationMapper();

    private static ExtractedAllocation extractValid() {
        FixParseResult result = new FixMessageParser(new FixDictionaryConfig().fixDataDictionary())
                .parse(FixTestMessages.validNewAllocation());
        return ((FixParseResult.Parsed) result).extracted();
    }

    @Test
    void mapsTypedFieldsAtTheStorageScales() {
        Allocation allocation = mapper.toAllocation(
                extractValid(), AllocTransType.NEW, AllocationStatus.RECEIVED, 42L, NOW);

        assertThat(allocation.getAllocId()).isEqualTo(FixTestMessages.VALID_ALLOC_ID);
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.RECEIVED);
        assertThat(allocation.getRawMessageId()).isEqualTo(42L);
        assertThat(allocation.getQuantity()).isEqualByComparingTo("100");
        assertThat(allocation.getQuantity().scale()).isEqualTo(4);
        assertThat(allocation.getAvgPx().scale()).isEqualTo(8);
        assertThat(allocation.getTradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(allocation.getSettlDate()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(allocation.getTransactTime()).isEqualTo(Instant.parse("2026-07-15T13:45:30Z"));
        assertThat(allocation.getSymbol()).isEqualTo("AAPL");
        assertThat(allocation.getCurrency()).isEqualTo("USD");
    }

    @Test
    void preservesGroupOrderThroughSequenceNumbers() {
        Allocation allocation = mapper.toAllocation(
                extractValid(), AllocTransType.NEW, AllocationStatus.RECEIVED, 42L, NOW);

        assertThat(allocation.getOrders()).hasSize(2);
        assertThat(allocation.getOrders().get(0).getSeq()).isZero();
        assertThat(allocation.getOrders().get(0).getClOrdId()).isEqualTo("ORD-1");
        assertThat(allocation.getOrders().get(1).getSeq()).isEqualTo(1);

        assertThat(allocation.getExecutions()).hasSize(2);
        assertThat(allocation.getExecutions().get(1).getLastQty()).isEqualByComparingTo("40");

        assertThat(allocation.getAccounts()).hasSize(2);
        assertThat(allocation.getAccounts().get(0).getMiscFees()).hasSize(2);
        assertThat(allocation.getAccounts().get(0).getMiscFees().get(0).getMiscFeeAmt())
                .isEqualByComparingTo("1.25");
        assertThat(allocation.getAccounts().get(0).getMiscFees().get(1).getSeq()).isEqualTo(1);
        assertThat(allocation.getAccounts().get(1).getMiscFees()).isEmpty();
    }

    @Test
    void unconvertibleValuesBecomeNullForInvalidRows() {
        ExtractedAllocation invalid = new ExtractedAllocation(
                "ALLOC-X", "0", "1", "1", null, "1",
                "not-a-number",      // quantity
                "101.123456789",     // avgPx: scale 9 > 8, would require rounding
                "USD",
                "2026-07-15",        // wrong date layout
                "bad-timestamp",
                null, null, null, null, "AAPL", null, null, null,
                List.of(), List.of(), List.of());

        Allocation allocation = mapper.toAllocation(
                invalid, AllocTransType.NEW, AllocationStatus.INVALID, 43L, NOW);

        assertThat(allocation.getQuantity()).isNull();
        assertThat(allocation.getAvgPx()).isNull();
        assertThat(allocation.getTradeDate()).isNull();
        assertThat(allocation.getTransactTime()).isNull();
        // Values that did convert are kept.
        assertThat(allocation.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void parsesMicrosecondTransactTime() {
        ExtractedAllocation micros = new ExtractedAllocation(
                "ALLOC-US", "0", "1", "1", null, "1", "100", "101.25",
                "USD", "20260715", "20260715-13:45:30.123456", null, null, null, null,
                "AAPL", null, null, null, List.of(), List.of(), List.of());

        Allocation allocation = mapper.toAllocation(
                micros, AllocTransType.NEW, AllocationStatus.RECEIVED, 45L, NOW);

        assertThat(allocation.getTransactTime())
                .isEqualTo(Instant.parse("2026-07-15T13:45:30.123456Z"));
    }

    @Test
    void neverRoundsAFinancialValue() {
        ExtractedAllocation fine = new ExtractedAllocation(
                "ALLOC-Y", "0", "1", "1", null, "1", "100.1234", "101.25",
                "USD", "20260715", "20260715-13:45:30", null, null, null, null,
                "AAPL", null, null, null, List.of(), List.of(), List.of());

        Allocation allocation = mapper.toAllocation(
                fine, AllocTransType.NEW, AllocationStatus.RECEIVED, 44L, NOW);

        assertThat(allocation.getQuantity()).isEqualByComparingTo(new BigDecimal("100.1234"));
    }
}
