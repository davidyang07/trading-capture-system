package com.tradingcapture.allocation.orchestration;

import com.tradingcapture.allocation.client.AccountValidationClient;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AccountLine;
import com.tradingcapture.allocation.validation.logical.LogicalValidationContext.AllocationView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What each allocation account must hold before the allocation may proceed: a buy needs the cash to
 * pay for it, a sell needs the securities to deliver. Each check asks account-service for exactly one
 * of the two (it skips the other when its amount is null).
 *
 * <p>The cash a buy line needs is its NetMoney (tag 154) when the instruction carries it, otherwise
 * its quantity at the line's price (AllocAvgPx/AllocPrice) or the instruction's AvgPx, rounded up to
 * the ledger's four decimals. With no money and no price at all the cash check is skipped, as before.
 */
final class AccountRequirements {

    static final String SIDE_BUY = "1";
    static final String SIDE_SELL = "2";
    private static final int CASH_SCALE = 4;

    private AccountRequirements() {
    }

    static List<AccountValidationClient.Check> checks(AllocationView view) {
        return view.accounts().stream().map(line -> check(view, line)).toList();
    }

    private static AccountValidationClient.Check check(AllocationView view, AccountLine line) {
        if (SIDE_SELL.equals(view.side())) {
            return new AccountValidationClient.Check(line.accountId(), view.currency(), null,
                    view.instrumentSymbol(), line.allocQty());
        }
        if (SIDE_BUY.equals(view.side())) {
            return new AccountValidationClient.Check(line.accountId(), view.currency(), cashFor(view, line),
                    view.instrumentSymbol(), null);
        }
        // Intake accepts only buy and sell; anything else gets the existence/status checks alone.
        return new AccountValidationClient.Check(line.accountId(), view.currency(), null,
                view.instrumentSymbol(), null);
    }

    private static BigDecimal cashFor(AllocationView view, AccountLine line) {
        if (line.netMoney() != null) {
            return line.netMoney();
        }
        BigDecimal price = line.price() != null ? line.price() : view.avgPx();
        if (price == null || line.allocQty() == null) {
            return null;
        }
        return line.allocQty().multiply(price).setScale(CASH_SCALE, RoundingMode.UP);
    }
}
