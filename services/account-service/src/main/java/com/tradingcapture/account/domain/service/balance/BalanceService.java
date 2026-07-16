package com.tradingcapture.account.domain.service.balance;

import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.repository.CashTransactionRepository;
import com.tradingcapture.account.domain.repository.SecurityTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes balances as sums over the immutable ledger rows (ADR-0002 balance semantics):
 * a stored balance is never trusted as the source of truth. All arithmetic is
 * {@link BigDecimal}; balances present at scale 4 (the ledger's money/quantity scale).
 *
 * <p>Read methods are transactional; when invoked from a ledger write flow they join that
 * flow's transaction and therefore observe a consistent snapshot for the sufficiency check.</p>
 */
@Service
public class BalanceService {

    private final CashTransactionRepository cashTransactions;
    private final SecurityTransactionRepository securityTransactions;

    public BalanceService(CashTransactionRepository cashTransactions,
                          SecurityTransactionRepository securityTransactions) {
        this.cashTransactions = cashTransactions;
        this.securityTransactions = securityTransactions;
    }

    @Transactional(readOnly = true)
    public BigDecimal cashBalance(String accountId, String currency) {
        BigDecimal credits = nz(cashTransactions.sumAmount(accountId, currency, CashDirection.CREDIT));
        BigDecimal debits = nz(cashTransactions.sumAmount(accountId, currency, CashDirection.DEBIT));
        return scale(credits.subtract(debits));
    }

    @Transactional(readOnly = true)
    public BigDecimal securityPosition(String accountId, String instrumentId) {
        BigDecimal receipts = nz(securityTransactions.sumQuantity(accountId, instrumentId, SecurityDirection.RECEIPT));
        BigDecimal deliveries = nz(securityTransactions.sumQuantity(accountId, instrumentId, SecurityDirection.DELIVERY));
        return scale(receipts.subtract(deliveries));
    }

    @Transactional(readOnly = true)
    public List<CashBalance> cashBalances(String accountId) {
        return cashTransactions.findCurrencies(accountId).stream()
                .map(currency -> new CashBalance(currency, cashBalance(accountId, currency)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SecurityPosition> securityPositions(String accountId) {
        return securityTransactions.findInstruments(accountId).stream()
                .map(instrumentId -> new SecurityPosition(instrumentId, securityPosition(accountId, instrumentId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountBalances balances(String accountId) {
        return new AccountBalances(accountId, cashBalances(accountId), securityPositions(accountId));
    }

    /** True when the account holds no non-zero cash balance and no non-zero position (closure gate). */
    @Transactional(readOnly = true)
    public boolean isEmpty(String accountId) {
        boolean cashClear = cashBalances(accountId).stream()
                .allMatch(balance -> balance.balance().signum() == 0);
        boolean positionsClear = securityPositions(accountId).stream()
                .allMatch(position -> position.quantity().signum() == 0);
        return cashClear && positionsClear;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
