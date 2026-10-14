package com.tradingcapture.fullsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service REST scenarios against the live stack: authN/authZ (2), order capture (1),
 * account ledger (4), and reference-data enrichment (5). Each mints a REAL Keycloak token and
 * calls the separately-deployed services over HTTP.
 *
 * <p>Self-skips unless FULL_STACK=1 (the stack must be up — scripts/run-scenarios.ps1). The gate is
 * declared here on the concrete class (not only on the base) because JUnit execution conditions on
 * an abstract superclass are not reliably applied to subclasses.
 */
@EnabledIfEnvironmentVariable(named = "FULL_STACK", matches = "1")
class RestScenariosTest extends FullSystemTest {

    // --- Scenario 2: authentication & authorization --------------------------------------

    @Test
    void anonymousRequestIsUnauthorized() {
        FullSystemHarness.Response r = stack.get(FullSystemHarness.TRADING + "/api/v1/orders", null);
        assertThat(r.status()).isEqualTo(401);
    }

    @Test
    void investorCannotReadAnotherOwnersOrderAndItIsMaskedAsNotFound() {
        String investor1 = stack.userToken("investor1");
        String investor2 = stack.userToken("investor2");

        String createdId = createOrder(investor1).at("/orderId").asText();
        assertThat(createdId).isNotBlank();

        // investor2 sees a 404 (masked), never a 403 that would confirm existence.
        FullSystemHarness.Response cross =
                stack.get(FullSystemHarness.TRADING + "/api/v1/orders/" + createdId, investor2);
        assertThat(cross.status()).isEqualTo(404);
    }

    @Test
    void investorLacksAdminRoleForReferenceDataMutation() {
        String investor1 = stack.userToken("investor1");
        // Enrichment reference-data mutation requires scope + ADMIN role; an investor is 403.
        // The body must be valid: request-body validation runs before method security, so an
        // invalid body would be answered 400 without ever reaching the authorization check.
        FullSystemHarness.Response r = stack.post(
                FullSystemHarness.ENRICHMENT + "/api/v1/instruments", investor1,
                Map.of("instrumentId", "ZZZ-" + UUID.randomUUID(), "name", "nope",
                        "instrumentType", "EQUITY", "exchangeMic", "XNAS", "currencyCode", "USD"));
        // Authenticated investor, valid scope, lacks ADMIN role -> 403 (not 401, which would mean
        // authentication itself failed and would mask an authz regression).
        assertThat(r.status()).isEqualTo(403);
    }

    // --- Scenario 1: order capture --------------------------------------------------------

    @Test
    void investorCapturesANewOrderThenReadsItBack() {
        String investor1 = stack.userToken("investor1");
        FullSystemHarness.Response created = createOrder(investor1);
        assertThat(created.status()).isEqualTo(201);
        String orderId = created.at("/orderId").asText();

        FullSystemHarness.Response detail =
                stack.get(FullSystemHarness.TRADING + "/api/v1/orders/" + orderId, investor1);
        assertThat(detail.status()).isEqualTo(200);
        // OrderDetailResponse wraps the order next to its execution history.
        assertThat(detail.at("/order/orderId").asText()).isEqualTo(orderId);
        assertThat(detail.at("/order/status").asText()).isEqualTo("NEW");
    }

    private FullSystemHarness.Response createOrder(String token) {
        return stack.post(FullSystemHarness.TRADING + "/api/v1/orders", token, Map.ofEntries(
                Map.entry("accountId", "ACC-SEED-INV1"),
                Map.entry("instrumentId", "AAPL"),
                Map.entry("side", "BUY"),
                Map.entry("orderType", "LIMIT"),
                Map.entry("price", "189.25"),
                Map.entry("priceType", "PER_UNIT"),
                Map.entry("currency", "USD"),
                Map.entry("quantity", "100"),
                Map.entry("settleType", "REGULAR"),
                Map.entry("settleDate", "2026-07-22")));
    }

    // --- Scenario 4: account + immutable ledger ------------------------------------------

    @Test
    void accountAcceptsADepositAndRejectsAnOverdraft() {
        String investor1 = stack.userToken("investor1");
        String admin = stack.userToken("tcs-admin");
        String accountId = stack.post(FullSystemHarness.ACCOUNT + "/api/v1/accounts", investor1,
                Map.of("accountName", "FS-" + UUID.randomUUID(),
                        "accountType", "CASH", "baseCurrency", "USD"))
                .at("/accountId").asText();
        assertThat(accountId).isNotBlank();

        // Lifecycle transitions and ledger posts are ADMIN-only: the owner cannot activate the
        // account or mint cash into it.
        assertThat(stack.post(FullSystemHarness.ACCOUNT + "/api/v1/accounts/" + accountId + "/activate",
                investor1, Map.of()).status()).isEqualTo(403);
        assertThat(stack.post(FullSystemHarness.ACCOUNT + "/api/v1/accounts/" + accountId + "/activate",
                admin, Map.of()).status()).isEqualTo(200);
        assertThat(stack.post(
                FullSystemHarness.ACCOUNT + "/api/v1/accounts/" + accountId + "/cash-transactions",
                investor1, Map.of("direction", "CREDIT", "transactionType", "DEPOSIT",
                        "amount", "1000.00", "currency", "USD")).status()).isEqualTo(403);

        FullSystemHarness.Response deposit = stack.post(
                FullSystemHarness.ACCOUNT + "/api/v1/accounts/" + accountId + "/cash-transactions",
                admin, Map.of("direction", "CREDIT", "transactionType", "DEPOSIT",
                        "amount", "1000.00", "currency", "USD"));
        assertThat(deposit.status()).isIn(200, 201);

        FullSystemHarness.Response overdraft = stack.post(
                FullSystemHarness.ACCOUNT + "/api/v1/accounts/" + accountId + "/cash-transactions",
                admin, Map.of("direction", "DEBIT", "transactionType", "WITHDRAWAL",
                        "amount", "5000.00", "currency", "USD"));
        assertThat(overdraft.status()).isEqualTo(409);
        assertThat(overdraft.at("/errorCode").asText()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    // --- Scenario 5: reference-data enrichment -------------------------------------------

    @Test
    void referenceDataLookupsReturnSeededInstrumentsAndCurrencies() {
        String investor1 = stack.userToken("investor1");
        assertThat(stack.get(FullSystemHarness.ENRICHMENT + "/api/v1/currencies/USD", investor1)
                .status()).isEqualTo(200);
        // List the seeded instruments (deterministic seed has several) rather than guessing an
        // internal id form — a strict 200 with a non-empty result proves reference data is served.
        FullSystemHarness.Response instruments = stack.get(
                FullSystemHarness.ENRICHMENT + "/api/v1/instruments", investor1);
        assertThat(instruments.status()).isEqualTo(200);
        assertThat(instruments.body().isEmpty()).isFalse();
    }
}
