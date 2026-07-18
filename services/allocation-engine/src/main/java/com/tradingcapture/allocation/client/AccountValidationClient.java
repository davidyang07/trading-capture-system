package com.tradingcapture.allocation.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed client for account-service batch validation ({@code POST /internal/v1/accounts/batch}).
 * Each item yields an independent verdict (existence, lifecycle, owner, cash/security sufficiency);
 * one bad item never fails the batch. Genuine transport failures surface as
 * {@link DependencyCallException}. Never touches the account database.
 */
@Component
public class AccountValidationClient {

    private static final String DEPENDENCY = "account";

    private final RestClient restClient;
    private final RemoteCallExecutor executor;
    private final String instance;

    public AccountValidationClient(@Qualifier("accountRestClient") RestClient restClient,
                                   RemoteCallExecutor executor,
                                   DependencyClientsProperties properties) {
        this.restClient = restClient;
        this.executor = executor;
        String configured = properties.getAccount().getResilienceInstance();
        this.instance = configured != null ? configured : DEPENDENCY;
    }

    public List<AccountValidation> validate(List<Check> checks) {
        Response response = executor.call(DEPENDENCY, instance, "validateAccounts", () ->
                restClient.post()
                        .uri("/internal/v1/accounts/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new Request(checks))
                        .retrieve()
                        .body(Response.class));
        return response == null || response.results() == null ? List.of() : response.results();
    }

    /** One account check: existence + optional cash and security-position sufficiency. */
    public record Check(String accountId,
                        String requiredCurrency,
                        BigDecimal requiredCashAmount,
                        String instrumentId,
                        BigDecimal requiredQuantity) {
    }

    private record Request(List<Check> items) {
    }

    private record Response(List<AccountValidation> results) {
    }

    /** Per-account verdict (status/active from account-service, serialized by name). */
    public record AccountValidation(String accountId,
                                    boolean found,
                                    String status,
                                    boolean active,
                                    String ownerSubject,
                                    Boolean sufficientCash,
                                    Boolean sufficientSecurities,
                                    List<String> reasons) {
    }
}
