package com.tradingcapture.allocation.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Typed client for trading-service execution resolution ({@code POST /internal/v1/executions/resolve}).
 * Resolves external FIX ExecIDs to internal executions + allocation eligibility so the engine can
 * bind by internal id. Returns per-item results (a bad/unknown id is a NOT_FOUND item, not a call
 * failure); genuine transport failures surface as {@link DependencyCallException}. Never touches the
 * trading database.
 */
@Component
public class TradingExecutionClient {

    private static final String DEPENDENCY = "trading";

    private final RestClient restClient;
    private final RemoteCallExecutor executor;
    private final String instance;

    public TradingExecutionClient(@Qualifier("tradingRestClient") RestClient restClient,
                                  RemoteCallExecutor executor,
                                  DependencyClientsProperties properties) {
        this.restClient = restClient;
        this.executor = executor;
        String configured = properties.getTrading().getResilienceInstance();
        this.instance = configured != null ? configured : DEPENDENCY;
    }

    public List<ExecutionResolution> resolveExecutions(List<String> execIds) {
        Response response = executor.call(DEPENDENCY, instance, "resolveExecutions", () ->
                restClient.post()
                        .uri("/internal/v1/executions/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new Request(execIds))
                        .retrieve()
                        .body(Response.class));
        return response == null || response.results() == null ? List.of() : response.results();
    }

    /**
     * Idempotently bind executions to {@code allocationId} in trading-service (ALLOCATE). Re-applying
     * the same allocationId is a no-op (appliedCount 0); binding to a different allocation is a 409
     * conflict surfaced as a non-retryable {@link DependencyCallException} (BAD_REQUEST).
     */
    public int allocate(String allocationId, List<String> executionInternalIds) {
        return applyState(allocationId, "ALLOCATE", executionInternalIds, "allocateExecutions");
    }

    /** Idempotently release executions bound to {@code allocationId} (RELEASE all when ids are null). */
    public int release(String allocationId, List<String> executionInternalIds) {
        return applyState(allocationId, "RELEASE", executionInternalIds, "releaseExecutions");
    }

    private int applyState(String allocationId, String action, List<String> ids, String operation) {
        AllocationStateResponse response = executor.call(DEPENDENCY, instance, operation, () ->
                restClient.put()
                        .uri("/internal/v1/executions/allocation-state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new AllocationStateRequest(allocationId, action, ids))
                        .retrieve()
                        .body(AllocationStateResponse.class));
        return response == null ? 0 : response.appliedCount();
    }

    private record Request(List<String> execIds) {
    }

    private record Response(List<ExecutionResolution> results) {
    }

    private record AllocationStateRequest(String allocationId, String action,
                                          List<String> executionInternalIds) {
    }

    private record AllocationStateResponse(String allocationId, String action, int appliedCount) {
    }

    /** Minimal per-item resolution the engine's selection and logical validation consume. */
    public record ExecutionResolution(String execId,
                                      boolean found,
                                      String internalId,
                                      String orderId,
                                      String instrumentId,
                                      String accountId,
                                      String side,
                                      BigDecimal executedQuantity,
                                      BigDecimal allocatableQuantity,
                                      String currency,
                                      LocalDate tradeDate,
                                      String settleType,
                                      LocalDate settleDate,
                                      Instant transactTime,
                                      String allocationState,
                                      String allocationId,
                                      boolean eligible,
                                      String reason) {
    }
}
