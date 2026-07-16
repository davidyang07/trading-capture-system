package com.tradingcapture.account.domain.service.validation;

import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Machine-facing, side-effect-free batch validation consumed by allocation-engine and
 * trading-service (ADR-0002). Each item is evaluated independently: one unknown or ineligible
 * account never fails the whole request — the caller gets a per-item verdict with reasons.
 *
 * <p>This is a cross-owner endpoint (gated by a service scope at the filter chain), so it does
 * not apply investor ownership masking — a machine legitimately validates any account.</p>
 */
@Service
public class AccountValidationService {

    private final AccountRepository accounts;
    private final BalanceService balanceService;

    public AccountValidationService(AccountRepository accounts, BalanceService balanceService) {
        this.accounts = accounts;
        this.balanceService = balanceService;
    }

    /** A single account check; the cash / security requirements are optional. */
    public record Check(String accountId,
                        String requiredCurrency,
                        BigDecimal requiredCashAmount,
                        String instrumentId,
                        BigDecimal requiredQuantity) {
    }

    public record Outcome(String accountId,
                          boolean found,
                          AccountStatus status,
                          boolean active,
                          String ownerSubject,
                          Boolean sufficientCash,
                          Boolean sufficientSecurities,
                          List<String> reasons) {
    }

    @Transactional(readOnly = true)
    public List<Outcome> validate(List<Check> checks) {
        List<String> ids = checks.stream().map(Check::accountId).distinct().toList();
        Map<String, Account> byId = accounts.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));
        return checks.stream().map(check -> evaluate(check, byId.get(check.accountId()))).toList();
    }

    private Outcome evaluate(Check check, Account account) {
        List<String> reasons = new ArrayList<>();
        if (account == null) {
            reasons.add("Account does not exist");
            return new Outcome(check.accountId(), false, null, false, null, null, null, reasons);
        }

        boolean active = account.isActive();
        if (!active) {
            reasons.add("Account is " + account.getStatus() + " (not ACTIVE)");
        }

        Boolean sufficientCash = null;
        if (check.requiredCashAmount() != null && check.requiredCurrency() != null) {
            BigDecimal available = balanceService.cashBalance(check.accountId(), check.requiredCurrency());
            sufficientCash = available.compareTo(check.requiredCashAmount()) >= 0;
            if (!sufficientCash) {
                reasons.add("Insufficient " + check.requiredCurrency() + " funds: available "
                        + available + ", required " + check.requiredCashAmount());
            }
        }

        Boolean sufficientSecurities = null;
        if (check.requiredQuantity() != null && check.instrumentId() != null) {
            BigDecimal available = balanceService.securityPosition(check.accountId(), check.instrumentId());
            sufficientSecurities = available.compareTo(check.requiredQuantity()) >= 0;
            if (!sufficientSecurities) {
                reasons.add("Insufficient " + check.instrumentId() + " position: available "
                        + available + ", required " + check.requiredQuantity());
            }
        }

        return new Outcome(check.accountId(), true, account.getStatus(), active,
                account.getOwnerSubject(), sufficientCash, sufficientSecurities, reasons);
    }
}
