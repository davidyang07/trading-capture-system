package com.tradingcapture.allocation.orchestration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tradingcapture.allocation.domain.model.AllocTransType;
import com.tradingcapture.allocation.domain.model.Allocation;
import com.tradingcapture.allocation.domain.model.AllocationAccount;
import com.tradingcapture.allocation.domain.model.AllocationExecution;
import com.tradingcapture.allocation.domain.model.AllocationOrder;
import com.tradingcapture.allocation.domain.model.AllocationStatus;
import com.tradingcapture.allocation.domain.repository.AllocationRepository;
import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.RedisTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Full-context orchestration integration base: real MySQL (Flyway V1+V2) and Redis containers, the
 * production-wired clients (with a machine OAuth2 token acquired from a stubbed token endpoint), and
 * a single WireMock standing in for account/enrichment/trading. The scheduled driver and outbox
 * poller are off; tests call {@link OrchestrationDriver#runOnce()} (or the orchestrator) directly and
 * assert persisted state + outbox rows — deterministic, no sleeps, no Kafka.
 */
@SpringBootTest
@Tag("integration")
abstract class OrchestrationIntegrationTestSupport extends MySqlContainerSupport {

    protected static WireMockServer wireMock;

    protected static final Instant NOW = Instant.parse("2026-07-17T14:00:00Z");

    @Autowired
    protected AllocationRepository allocations;
    @Autowired
    protected OrchestrationDriver driver;
    @Autowired
    protected AllocationOrchestrator orchestrator;
    @Autowired
    protected AllocationDedupFilter dedupFilter;
    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (wireMock == null) {
            wireMock = new WireMockServer(options().dynamicPort());
            wireMock.start();
        }
        String base = wireMock.baseUrl();
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
        registry.add("tcs.clients.account.base-url", () -> base);
        registry.add("tcs.clients.enrichment.base-url", () -> base);
        registry.add("tcs.clients.trading.base-url", () -> base);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> base + "/token");
        registry.add("tcs.outbox.enabled", () -> "false");
        registry.add("tcs.orchestration.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("tcs.kafka.schema-registry-url", () -> "mock://orchestration-it");
        // Fast, bounded resilience for tests.
        for (String dep : List.of("account", "enrichment", "trading")) {
            registry.add("resilience4j.retry.instances." + dep + ".max-attempts", () -> "2");
            registry.add("resilience4j.retry.instances." + dep + ".wait-duration", () -> "5ms");
        }
    }

    @BeforeEach
    void resetStubsAndData() {
        wireMock.resetAll();
        // Machine token endpoint (client-credentials) — every downstream call carries this bearer.
        wireMock.stubFor(post(urlEqualTo("/token")).willReturn(okJson("""
                { "access_token": "test-token", "token_type": "Bearer", "expires_in": 300 }
                """)));
        // FK-safe wipe: the singleton MySQL container is shared with the Phase 7 suites, whose
        // rows (e.g. allocation_misc_fees) would otherwise block deletes by referential order.
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("allocation_assignments", "allocation_state_transitions",
                "outbox_event", "allocation_misc_fees", "allocation_accounts", "allocation_executions",
                "allocation_orders", "validation_error_fields", "validation_errors", "allocations",
                "raw_messages")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    // --- WireMock stubs ------------------------------------------------------------------------

    protected void stubAccountsActive(String... accountIds) {
        StringBuilder results = new StringBuilder();
        for (String id : accountIds) {
            if (results.length() > 0) {
                results.append(",");
            }
            results.append("""
                    { "accountId": "%s", "found": true, "status": "ACTIVE", "active": true,
                      "ownerSubject": "investor1", "sufficientCash": true, "sufficientSecurities": true,
                      "reasons": [] }""".formatted(id));
        }
        wireMock.stubFor(post(urlEqualTo("/internal/v1/accounts/batch"))
                .willReturn(okJson("{ \"results\": [" + results + "] }")));
    }

    protected void stubAccountsResponse(String json) {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/accounts/batch")).willReturn(okJson(json)));
    }

    protected void stubAccounts5xx() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/accounts/batch")).willReturn(aResponse().withStatus(500)));
    }

    protected void stubInstrumentActive(String symbol) {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/instruments/lookup")).willReturn(okJson("""
                { "results": [ { "source": "EXCHANGE_SYMBOL", "value": "%s", "exchangeMic": "XNAS",
                  "found": true, "ambiguous": false,
                  "instrument": { "instrumentId": "%s", "name": "%s", "instrumentType": "EQUITY",
                    "exchangeMic": "XNAS", "currencyCode": "USD", "status": "ACTIVE" } } ] }
                """.formatted(symbol, symbol, symbol))));
    }

    protected void stubExecutionEligible(String execId, String internalId, String qty, String isoTime) {
        stubResolve(List.of("""
                { "execId": "%s", "found": true, "internalId": "%s", "orderId": "ORD-1",
                  "instrumentId": "AAPL", "accountId": "ACC-1", "side": "1",
                  "executedQuantity": %s, "allocatableQuantity": %s, "currency": "USD",
                  "tradeDate": "2026-07-17", "settleType": "0", "settleDate": "2026-07-20",
                  "transactTime": "%s", "allocationState": "UNALLOCATED", "allocationId": null,
                  "eligible": true, "reason": "ELIGIBLE" }""".formatted(execId, internalId, qty, qty, isoTime)));
    }

    protected void stubResolve(List<String> resultJsons) {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/executions/resolve"))
                .willReturn(okJson("{ \"results\": [" + String.join(",", resultJsons) + "] }")));
    }

    protected void stubResolve5xx() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/executions/resolve")).willReturn(aResponse().withStatus(500)));
    }

    protected void stubAllocationStateOk() {
        wireMock.stubFor(put(urlEqualTo("/internal/v1/executions/allocation-state")).willReturn(okJson("""
                { "allocationId": "x", "action": "ALLOCATE", "appliedCount": 1, "executions": [] }
                """)));
    }

    /** RELEASE succeeds; ALLOCATE returns a 409 conflict — a Replace fails only after the release commits. */
    protected void stubReleaseOkAllocateConflict() {
        wireMock.stubFor(put(urlEqualTo("/internal/v1/executions/allocation-state"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("RELEASE"))
                .willReturn(okJson("""
                        { "allocationId": "x", "action": "RELEASE", "appliedCount": 1, "executions": [] }
                        """)));
        wireMock.stubFor(put(urlEqualTo("/internal/v1/executions/allocation-state"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("ALLOCATE"))
                .willReturn(aResponse().withStatus(409)));
    }

    protected void stubAllocateConflict() {
        wireMock.stubFor(put(urlEqualTo("/internal/v1/executions/allocation-state"))
                .willReturn(aResponse().withStatus(409)));
    }

    protected void stubAccountsStatus(int status) {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/accounts/batch")).willReturn(aResponse().withStatus(status)));
    }

    // --- data fixtures -------------------------------------------------------------------------

    protected record AccountQty(String accountId, String qty) {
    }

    @Transactional
    protected Long persistAllocation(String allocId, AllocTransType type, String refAllocId,
                                     String symbol, String side, String quantity,
                                     List<AccountQty> accounts, List<String> execIds) {
        jdbc.update("""
                INSERT INTO raw_messages (topic, kafka_partition, kafka_offset, payload, correlation_id,
                    status, delivery_count, received_at, created_at, updated_at)
                VALUES ('t', 0, ?, 'p', ?, 'PROCESSED', 1, NOW(6), NOW(6), NOW(6))
                """, allocId.hashCode() & 0x7fffffffL, "cid-" + allocId);
        Long rawId = jdbc.queryForObject("SELECT id FROM raw_messages WHERE correlation_id = ?",
                Long.class, "cid-" + allocId);

        Allocation a = new Allocation(allocId, type, AllocationStatus.RECEIVED, rawId, NOW);
        a.setInstructionFields(null, null, refAllocId, side, new BigDecimal(quantity), new BigDecimal("10.00000000"),
                "USD", java.time.LocalDate.parse("2026-07-17"), NOW, "0",
                java.time.LocalDate.parse("2026-07-20"), null, null);
        a.setInstrumentFields(symbol, null, null, "XNAS");
        a.addOrder(new AllocationOrder(1, "CLORD-1", "ORD-1", new BigDecimal(quantity), null, NOW));
        int seq = 1;
        for (String execId : execIds) {
            a.addExecution(new AllocationExecution(seq++, execId, new BigDecimal(quantity), null, null, NOW));
        }
        seq = 1;
        for (AccountQty acct : accounts) {
            a.addAccount(new AllocationAccount(seq++, acct.accountId(), new BigDecimal(acct.qty()),
                    null, null, null, "USD", null, null, NOW));
        }
        return allocations.save(a).getId();
    }

    protected Allocation reload(Long id) {
        return allocations.findById(id).orElseThrow();
    }

    protected java.util.List<java.util.Map<String, Object>> assignmentsOf(String allocId) {
        return jdbc.queryForList("""
                SELECT aa.execution_id, aa.allocated_qty, aa.released_qty, aa.status
                FROM allocation_assignments aa JOIN allocations a ON a.id = aa.allocation_id
                WHERE a.alloc_id = ? ORDER BY aa.id
                """, allocId);
    }

    protected int outboxCount(String eventType, String allocId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND message_key = ?",
                Integer.class, eventType, allocId);
        return n == null ? 0 : n;
    }
}
