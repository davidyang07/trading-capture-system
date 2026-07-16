package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.InvalidRequestException;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.repository.AccountSpecifications;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import com.tradingcapture.account.domain.service.command.AccountCommands;
import com.tradingcapture.account.observability.AccountMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Account lifecycle: create / read / search / update / status transitions with explicit
 * eligibility rules.
 *
 * <ul>
 *   <li>Owner subject comes from the {@link AccessContext} (the verified JWT); only an admin
 *       may nominate a different owner at creation.</li>
 *   <li>Non-admin callers only ever see or touch their own accounts (masked as 404).</li>
 *   <li>Status transitions are validated against {@link AccountStatus#canTransitionTo}; closing
 *       additionally requires every ledger-derived balance to be zero.</li>
 * </ul>
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final BalanceService balanceService;
    private final AccountMetrics metrics;
    private final Clock clock;

    public AccountService(AccountRepository accounts,
                          BalanceService balanceService,
                          AccountMetrics metrics,
                          Clock clock) {
        this.accounts = accounts;
        this.balanceService = balanceService;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Account create(AccessContext ctx, AccountCommands.CreateAccount command) {
        String ownerSubject = resolveOwner(ctx, command.ownerSubject());
        String accountId = StringUtils.hasText(command.accountId())
                ? command.accountId()
                : AccountIds.newAccountId();
        if (accounts.existsById(accountId)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_ACCOUNT_ID,
                    "Account ID already in use: " + accountId);
        }
        Account account = new Account(accountId,
                ownerSubject,
                command.accountName(),
                command.accountType(),
                command.baseCurrency(),
                Instant.now(clock));
        accounts.save(account);
        metrics.accountCreated();
        log.info("Account created: accountId={} ownerSubject={} type={} status={}",
                account.getAccountId(), account.getOwnerSubject(), account.getAccountType(), account.getStatus());
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(AccessContext ctx, String accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
        ctx.requireVisible(account);
        return account;
    }

    @Transactional(readOnly = true)
    public Page<Account> search(AccessContext ctx, AccountSearchCriteria criteria, Pageable pageable) {
        List<Specification<Account>> parts = new ArrayList<>();
        if (!ctx.canAccessAllAccounts()) {
            // Ownership isolation is enforced here, in the query itself — never client-side.
            parts.add(AccountSpecifications.ownedBy(ctx.subject()));
        } else if (StringUtils.hasText(criteria.ownerSubject())) {
            parts.add(AccountSpecifications.ownedBy(criteria.ownerSubject()));
        }
        if (criteria.status() != null) {
            parts.add(AccountSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.accountType() != null) {
            parts.add(AccountSpecifications.hasType(criteria.accountType()));
        }
        if (StringUtils.hasText(criteria.currency())) {
            parts.add(AccountSpecifications.hasCurrency(criteria.currency()));
        }
        return accounts.findAll(Specification.allOf(parts), pageable);
    }

    @Transactional
    public Account update(AccessContext ctx, String accountId, AccountCommands.UpdateAccount command) {
        Account account = get(ctx, accountId);
        account.rename(command.accountName(), Instant.now(clock));
        log.info("Account renamed: accountId={}", accountId);
        return account;
    }

    /** Applies a lifecycle transition after validating eligibility (and, for CLOSED, empty balances). */
    @Transactional
    public Account changeStatus(AccessContext ctx, String accountId, AccountStatus target) {
        Account account = get(ctx, accountId);
        if (!account.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Illegal transition " + account.getStatus() + " -> " + target + " for account " + accountId);
        }
        if (target == AccountStatus.CLOSED && !balanceService.isEmpty(accountId)) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_NOT_EMPTY,
                    "Account " + accountId + " cannot be closed while it holds a non-zero balance or position");
        }
        account.applyStatus(target, Instant.now(clock));
        metrics.accountStatusChanged();
        log.info("Account status changed: accountId={} status={}", accountId, target);
        return account;
    }

    private static String resolveOwner(AccessContext ctx, String requestedOwner) {
        if (StringUtils.hasText(requestedOwner) && !requestedOwner.equals(ctx.subject())) {
            if (!ctx.admin()) {
                // A non-admin may only create accounts for themselves; silently coercing would
                // hide the caller's mistake, so reject it explicitly.
                throw new InvalidRequestException(
                        "Only an administrator may create an account for another owner");
            }
            return requestedOwner;
        }
        return ctx.subject();
    }
}
