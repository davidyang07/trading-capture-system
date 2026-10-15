package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.client.AccountValidationClient.Check;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRequirementsTest {

    private static AllocationView view(String side, BigDecimal avgPx, AccountLine... lines) {
        return new AllocationView("A1", AllocTransType.NEW, null, side, new BigDecimal("4000.0000"), "USD",
                "MSFT", null, null, List.of(lines), List.of("E1"), avgPx);
    }

    @Test
    void aBuyNeedsCashAtTheAveragePriceAndNoPosition() {
        List<Check> checks = AccountRequirements.checks(view("1", new BigDecimal("431.19500000"),
                new AccountLine("ACC-1", new BigDecimal("4000.0000"), null, "USD", null)));

        assertThat(checks).singleElement().satisfies(c -> {
            assertThat(c.accountId()).isEqualTo("ACC-1");
            assertThat(c.requiredCurrency()).isEqualTo("USD");
            assertThat(c.requiredCashAmount()).isEqualByComparingTo("1724780.0000");
            assertThat(c.requiredQuantity()).isNull();
        });
    }

    @Test
    void aBuyPrefersTheLinesNetMoneyThenItsOwnPrice() {
        List<Check> checks = AccountRequirements.checks(view("1", new BigDecimal("100.00000000"),
                new AccountLine("WITH-NET", new BigDecimal("10.0000"), new BigDecimal("1005.2500"), "USD",
                        new BigDecimal("99.00000000")),
                new AccountLine("WITH-PRICE", new BigDecimal("10.0000"), null, "USD", new BigDecimal("99.00000000"))));

        assertThat(checks).extracting(Check::requiredCashAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1005.2500"), new BigDecimal("990.0000"));
    }

    @Test
    void cashIsRoundedUpToTheLedgersFourDecimals() {
        List<Check> checks = AccountRequirements.checks(view("1", new BigDecimal("0.33333333"),
                new AccountLine("ACC-1", new BigDecimal("1.0000"), null, "USD", null)));

        assertThat(checks.get(0).requiredCashAmount()).isEqualByComparingTo("0.3334");
    }

    @Test
    void aSellNeedsThePositionAndNoCash() {
        List<Check> checks = AccountRequirements.checks(view("2", new BigDecimal("431.94000000"),
                new AccountLine("ACC-1", new BigDecimal("600.0000"), new BigDecimal("259164.0000"), "USD", null)));

        assertThat(checks).singleElement().satisfies(c -> {
            assertThat(c.instrumentId()).isEqualTo("MSFT");
            assertThat(c.requiredQuantity()).isEqualByComparingTo("600.0000");
            assertThat(c.requiredCashAmount()).isNull();
        });
    }

    @Test
    void aBuyWithNoMoneyAndNoPriceSkipsTheCashCheck() {
        List<Check> checks = AccountRequirements.checks(view("1", null,
                new AccountLine("ACC-1", new BigDecimal("10.0000"), null, "USD", null)));

        assertThat(checks.get(0).requiredCashAmount()).isNull();
        assertThat(checks.get(0).requiredQuantity()).isNull();
    }
}
