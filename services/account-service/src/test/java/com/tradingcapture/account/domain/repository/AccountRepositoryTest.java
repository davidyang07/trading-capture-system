package com.tradingcapture.account.domain.repository;

import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.model.CashDirection;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.CashTransactionType;
import com.tradingcapture.account.domain.model.SecurityDirection;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import com.tradingcapture.account.domain.model.SecurityTransactionType;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MySQL repository semantics: Flyway-created schema, enum string codes, DECIMAL scales,
 * the ledger sum queries, the uniqueness of idempotency keys, and the optimistic
 * force-increment lock used to serialize concurrent ledger posts.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration")
@Tag("integration")
class AccountRepositoryTest extends MySqlContainerSupport {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CashTransactionRepository cashTransactions;
    @Autowired
    private SecurityTransactionRepository securityTransactions;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Account persistActive(String id, String owner) {
        Account account = new Account(id, owner, "Cash", AccountType.CASH, "USD", NOW);
        account.applyStatus(AccountStatus.ACTIVE, NOW);
        return accounts.saveAndFlush(account);
    }

    @Test
    void enumsAndDecimalScalesSurviveTheRoundTrip() {
        persistActive("ACC-RA", "investor1");
        cashTransactions.saveAndFlush(new CashTransaction("CT-RA", "ACC-RA", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.0000"), "USD", null, null, null, NOW));

        Map<String, Object> accountRow = jdbcTemplate.queryForMap(
                "SELECT account_type, status FROM accounts WHERE account_id = ?", "ACC-RA");
        assertThat(accountRow.get("account_type")).isEqualTo("CASH");
        assertThat(accountRow.get("status")).isEqualTo("ACTIVE");

        Map<String, Object> cashRow = jdbcTemplate.queryForMap(
                "SELECT direction, transaction_type FROM cash_transactions WHERE transaction_ref = ?", "CT-RA");
        assertThat(cashRow.get("direction")).isEqualTo("CREDIT");
        assertThat(cashRow.get("transaction_type")).isEqualTo("DEPOSIT");

        entityManager.clear();
        CashTransaction reloaded = cashTransactions.findAll().stream()
                .filter(t -> t.getTransactionRef().equals("CT-RA")).findFirst().orElseThrow();
        assertThat(reloaded.getAmount().scale()).isEqualTo(4);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void ledgerSumQueriesComputeBalances() {
        persistActive("ACC-RB", "investor1");
        cashTransactions.saveAndFlush(new CashTransaction("CT-RB1", "ACC-RB", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("1000.0000"), "USD", null, null, null, NOW));
        cashTransactions.saveAndFlush(new CashTransaction("CT-RB2", "ACC-RB", CashDirection.DEBIT,
                CashTransactionType.WITHDRAWAL, new BigDecimal("250.0000"), "USD", null, null, null, NOW));
        securityTransactions.saveAndFlush(new SecurityTransaction("ST-RB1", "ACC-RB", SecurityDirection.RECEIPT,
                SecurityTransactionType.TRADE_SETTLEMENT, "AAPL", new BigDecimal("500.0000"), null, null, null, NOW));

        assertThat(cashTransactions.sumAmount("ACC-RB", "USD", CashDirection.CREDIT)).isEqualByComparingTo("1000");
        assertThat(cashTransactions.sumAmount("ACC-RB", "USD", CashDirection.DEBIT)).isEqualByComparingTo("250");
        assertThat(cashTransactions.findCurrencies("ACC-RB")).containsExactly("USD");
        assertThat(securityTransactions.sumQuantity("ACC-RB", "AAPL", SecurityDirection.RECEIPT)).isEqualByComparingTo("500");
        assertThat(securityTransactions.findInstruments("ACC-RB")).containsExactly("AAPL");
    }

    @Test
    void idempotencyKeyIsUniquePerAccount() {
        persistActive("ACC-RC", "investor1");
        cashTransactions.saveAndFlush(new CashTransaction("CT-RC1", "ACC-RC", CashDirection.CREDIT,
                CashTransactionType.DEPOSIT, new BigDecimal("10.0000"), "USD", null, "idem-1", null, NOW));

        assertThatThrownBy(() -> cashTransactions.saveAndFlush(new CashTransaction("CT-RC2", "ACC-RC",
                CashDirection.CREDIT, CashTransactionType.DEPOSIT, new BigDecimal("10.0000"), "USD", null, "idem-1", null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraintRejectsNonPositiveAmountAtTheDatabaseLevel() {
        persistActive("ACC-RD", "investor1");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO cash_transactions
                    (transaction_ref, account_id, direction, transaction_type, amount, currency, posted_at)
                VALUES ('CT-BAD', 'ACC-RD', 'CREDIT', 'DEPOSIT', 0.0000, 'USD', '2026-07-16 00:00:00.000000')
                """))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void forUpdateLockLoadsTheAccountForMutation() {
        persistActive("ACC-RE", "investor1");
        entityManager.clear();

        assertThat(accounts.findByIdForUpdate("ACC-RE")).isPresent();
        assertThat(accounts.findByIdForUpdate("ACC-MISSING")).isEmpty();
    }

    @Test
    void optimisticLockRejectsStaleAccountWrites() {
        Account stale = persistActive("ACC-RF", "investor1"); // managed instance carrying version 0
        entityManager.clear();                                // detach it (still version 0 in memory)

        Account reloaded = accounts.findById("ACC-RF").orElseThrow();
        reloaded.touch(NOW.plusSeconds(1));
        accounts.saveAndFlush(reloaded);                      // version -> 1
        entityManager.clear();

        // The stale copy still believes it is version 0.
        stale.touch(NOW.plusSeconds(2));
        assertThatThrownBy(() -> accounts.saveAndFlush(stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }
}
