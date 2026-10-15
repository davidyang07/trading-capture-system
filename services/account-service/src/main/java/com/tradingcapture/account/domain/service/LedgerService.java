package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.exception.BusinessRuleException;
import com.tradingcapture.account.domain.exception.ErrorCode;
import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import com.tradingcapture.account.domain.repository.AccountRepository;
import com.tradingcapture.account.domain.repository.CashTransactionRepository;
import com.tradingcapture.account.domain.repository.SecurityTransactionRepository;
import com.tradingcapture.account.domain.service.balance.AccountBalances;
import com.tradingcapture.account.domain.service.balance.BalanceService;
import com.tradingcapture.account.domain.service.command.LedgerCommands;
import com.tradingcapture.account.observability.AccountMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Appends immutable cash and security ledger rows. Every mutating path:
 *
 * <ul>
 *   <li>loads the account under a row lock, so concurrent posts on one account run one after
 *       the other and each sees the earlier ones — this is what makes the sufficient-funds check
 *       race-safe;</li>
 *   <li>rejects posts to a non-ACTIVE account;</li>
 *   <li>honors an optional idempotency key: a replay with a key already seen for the account
 *       returns the original row instead of appending a duplicate;</li>
 *   <li>for a DEBIT / DELIVERY, refuses to drive the ledger-derived balance / position negative.</li>
 * </ul>
 *
 * Amounts and quantities are always strictly positive; the direction carries the sign.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final CashTransactionRepository cashTransactions;
    private final SecurityTransactionRepository securityTransactions;
    private final BalanceService balanceService;
    private final AccountMetrics metrics;
    private final Clock clock;

    public LedgerService(AccountRepository accounts,
                         CashTransactionRepository cashTransactions,
                         SecurityTransactionRepository securityTransactions,
                         BalanceService balanceService,
                         AccountMetrics metrics,
                         Clock clock) {
        this.accounts = accounts;
        this.cashTransactions = cashTransactions;
        this.securityTransactions = securityTransactions;
        this.balanceService = balanceService;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public LedgerPost<CashTransaction> postCash(AccessContext ctx, String accountId, LedgerCommands.PostCash command) {
        Account account = lockAndAuthorize(ctx, accountId);
        requireActive(account);

        if (StringUtils.hasText(command.idempotencyKey())) {
            Optional<CashTransaction> existing =
                    cashTransactions.findByAccountIdAndIdempotencyKey(accountId, command.idempotencyKey());
            if (existing.isPresent()) {
                CashTransaction original = existing.get();
                requireSamePosting(accountId, command.idempotencyKey(),
                        original.getDirection() == command.direction()
                                && original.getTransactionType() == command.transactionType()
                                && original.getAmount().compareTo(command.amount()) == 0
                                && original.getCurrency().equals(command.currency())
                                && Objects.equals(original.getExternalRef(), command.externalRef())
                                && Objects.equals(original.getDescription(), command.description()));
                metrics.idempotentReplay();
                return LedgerPost.replayed(original);
            }
        }

        BigDecimal amount = normalize(command.amount());
        if (command.direction() == CashDirection.DEBIT) {
            BigDecimal available = balanceService.cashBalance(accountId, command.currency());
            if (available.compareTo(amount) < 0) {
                metrics.insufficientFunds();
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_FUNDS,
                        "Insufficient " + command.currency() + " funds in account " + accountId
                                + ": available " + available + ", requested " + amount);
            }
        }

        Instant now = Instant.now(clock);
        CashTransaction transaction = new CashTransaction(
                AccountIds.newCashTransactionRef(),
                accountId,
                command.direction(),
                command.transactionType(),
                amount,
                command.currency(),
                command.externalRef(),
                command.idempotencyKey(),
                command.description(),
                now);
        cashTransactions.save(transaction);
        account.touch(now);
        metrics.cashPosted();
        log.info("Cash posted: accountId={} ref={} direction={} currency={}",
                accountId, transaction.getTransactionRef(), transaction.getDirection(), transaction.getCurrency());
        return LedgerPost.created(transaction);
    }

    @Transactional
    public LedgerPost<SecurityTransaction> postSecurity(AccessContext ctx, String accountId,
                                                        LedgerCommands.PostSecurity command) {
        Account account = lockAndAuthorize(ctx, accountId);
        requireActive(account);

        if (StringUtils.hasText(command.idempotencyKey())) {
            Optional<SecurityTransaction> existing =
                    securityTransactions.findByAccountIdAndIdempotencyKey(accountId, command.idempotencyKey());
            if (existing.isPresent()) {
                SecurityTransaction original = existing.get();
                requireSamePosting(accountId, command.idempotencyKey(),
                        original.getDirection() == command.direction()
                                && original.getTransactionType() == command.transactionType()
                                && original.getInstrumentId().equals(command.instrumentId())
                                && original.getQuantity().compareTo(command.quantity()) == 0
                                && Objects.equals(original.getExternalRef(), command.externalRef())
                                && Objects.equals(original.getDescription(), command.description()));
                metrics.idempotentReplay();
                return LedgerPost.replayed(original);
            }
        }

        BigDecimal quantity = normalize(command.quantity());
        if (command.direction() == SecurityDirection.DELIVERY) {
            BigDecimal available = balanceService.securityPosition(accountId, command.instrumentId());
            if (available.compareTo(quantity) < 0) {
                metrics.insufficientSecurities();
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_SECURITIES,
                        "Insufficient " + command.instrumentId() + " position in account " + accountId
                                + ": available " + available + ", requested " + quantity);
            }
        }

        Instant now = Instant.now(clock);
        SecurityTransaction transaction = new SecurityTransaction(
                AccountIds.newSecurityTransactionRef(),
                accountId,
                command.direction(),
                command.transactionType(),
                command.instrumentId(),
                quantity,
                command.externalRef(),
                command.idempotencyKey(),
                command.description(),
                now);
        securityTransactions.save(transaction);
        account.touch(now);
        metrics.securityPosted();
        log.info("Security posted: accountId={} ref={} direction={} instrumentId={}",
                accountId, transaction.getTransactionRef(), transaction.getDirection(), transaction.getInstrumentId());
        return LedgerPost.created(transaction);
    }

    @Transactional(readOnly = true)
    public Page<CashTransaction> cashHistory(AccessContext ctx, String accountId, Pageable pageable) {
        authorize(ctx, accountId);
        return cashTransactions.findByAccountId(accountId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SecurityTransaction> securityHistory(AccessContext ctx, String accountId, Pageable pageable) {
        authorize(ctx, accountId);
        return securityTransactions.findByAccountId(accountId, pageable);
    }

    /** Ownership-checked, ledger-derived balances for the account. */
    @Transactional(readOnly = true)
    public AccountBalances balances(AccessContext ctx, String accountId) {
        authorize(ctx, accountId);
        return balanceService.balances(accountId);
    }

    /** Loads the account under the row lock and enforces ownership visibility. */
    private Account lockAndAuthorize(AccessContext ctx, String accountId) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
        ctx.requireVisible(account);
        return account;
    }

    /** Read-path authorization (no lock): confirms the account exists and is visible. */
    private void authorize(AccessContext ctx, String accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
        ctx.requireVisible(account);
    }

    /**
     * A replay must repeat the original request. Reusing a key for a different posting is a client
     * bug; answering with the original row would silently drop the new posting.
     */
    private static void requireSamePosting(String accountId, String idempotencyKey, boolean samePosting) {
        if (!samePosting) {
            throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key " + idempotencyKey + " was already used for a different posting on account "
                            + accountId);
        }
    }

    private static void requireActive(Account account) {
        if (!account.isActive()) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Account " + account.getAccountId() + " is " + account.getStatus()
                            + "; only ACTIVE accounts accept transactions");
        }
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(4, RoundingMode.UNNECESSARY);
    }
}
