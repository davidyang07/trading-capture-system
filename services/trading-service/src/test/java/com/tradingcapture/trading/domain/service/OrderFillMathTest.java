package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.model.ExecutionType;
import com.tradingcapture.trading.domain.service.OrderFillMath.Aggregates;
import com.tradingcapture.trading.domain.service.OrderFillMath.FillEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregate math is the financial core of the service — these tests pin the chain
 * semantics: tips contribute, cancelled chains contribute nothing, avgPx is HALF_UP scale 8.
 */
class OrderFillMathTest {

    private static FillEvent trade(String id, String qty, String px) {
        return new FillEvent(id, null, ExecutionType.TRADE, new BigDecimal(qty), new BigDecimal(px));
    }

    private static FillEvent correct(String id, String ref, String qty, String px) {
        return new FillEvent(id, ref, ExecutionType.TRADE_CORRECT, new BigDecimal(qty), new BigDecimal(px));
    }

    private static FillEvent cancel(String id, String ref) {
        return new FillEvent(id, ref, ExecutionType.TRADE_CANCEL, BigDecimal.ZERO, null);
    }

    @Test
    void singleTradeContributesItsQuantityAndPrice() {
        Aggregates result = OrderFillMath.compute(List.of(trade("t1", "100", "189.20")));

        assertThat(result.cumQty()).isEqualByComparingTo("100");
        assertThat(result.avgPx()).isEqualByComparingTo("189.20");
    }

    @Test
    void multipleTradesAverageIsQuantityWeightedAtScale8HalfUp() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "80", "415.10"),
                trade("t2", "40", "415.35")));

        assertThat(result.cumQty()).isEqualByComparingTo("120");
        // (80*415.10 + 40*415.35) / 120 = 415.183333... -> scale 8 HALF_UP
        assertThat(result.avgPx()).isEqualTo(new BigDecimal("415.18333333"));
    }

    @Test
    void correctionReplacesTheTargetContribution() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "150", "240.00"),
                correct("c1", "t1", "150", "241.00")));

        assertThat(result.cumQty()).isEqualByComparingTo("150");
        assertThat(result.avgPx()).isEqualByComparingTo("241.00");
    }

    @Test
    void quantityCorrectionChangesCumQty() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "60", "10.00"),
                correct("c1", "t1", "50", "10.00")));

        assertThat(result.cumQty()).isEqualByComparingTo("50");
    }

    @Test
    void cancelledChainContributesNothing() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "100", "178.55"),
                cancel("x1", "t1")));

        assertThat(result.cumQty()).isEqualByComparingTo("0");
        assertThat(result.avgPx()).isNull();
    }

    @Test
    void multiStepChainOnlyTipCounts() {
        // T(60) -> G(50) -> G(55) -> only the last correction contributes
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "60", "10.00"),
                correct("c1", "t1", "50", "10.50"),
                correct("c2", "c1", "55", "11.00")));

        assertThat(result.cumQty()).isEqualByComparingTo("55");
        assertThat(result.avgPx()).isEqualByComparingTo("11.00");
    }

    @Test
    void cancelAfterCorrectionsVoidsTheWholeChain() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "60", "10.00"),
                correct("c1", "t1", "50", "10.50"),
                cancel("x1", "c1")));

        assertThat(result.cumQty()).isEqualByComparingTo("0");
        assertThat(result.avgPx()).isNull();
    }

    @Test
    void independentChainsAddUp() {
        Aggregates result = OrderFillMath.compute(List.of(
                trade("t1", "60", "10.00"),
                trade("t2", "40", "20.00"),
                cancel("x1", "t2"),
                trade("t3", "10", "30.00")));

        // t1 (60@10) + t3 (10@30); t2 cancelled
        assertThat(result.cumQty()).isEqualByComparingTo("70");
        // (600 + 300) / 70 = 12.857142857... -> 12.85714286
        assertThat(result.avgPx()).isEqualTo(new BigDecimal("12.85714286"));
    }

    @Test
    void zeroFillsYieldZeroCumAndNullAvgPx() {
        Aggregates result = OrderFillMath.compute(List.of());

        assertThat(result.cumQty()).isEqualByComparingTo("0");
        assertThat(result.avgPx()).isNull();
    }
}
