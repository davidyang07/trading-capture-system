package com.tradingcapture.allocation.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Typed client for enrichment-service instrument resolution
 * ({@code POST /internal/v1/instruments/lookup}). Each item resolves an alternate identifier to an
 * instrument summary (exchange, currency, activation status), with explicit found/ambiguous flags;
 * one bad item never fails the batch. Genuine transport failures surface as
 * {@link DependencyCallException}. Never touches the enrichment database.
 */
@Component
public class InstrumentResolutionClient {

    private static final String DEPENDENCY = "enrichment";

    private final RestClient restClient;
    private final RemoteCallExecutor executor;
    private final String instance;

    public InstrumentResolutionClient(@Qualifier("enrichmentRestClient") RestClient restClient,
                                      RemoteCallExecutor executor,
                                      DependencyClientsProperties properties) {
        this.restClient = restClient;
        this.executor = executor;
        String configured = properties.getEnrichment().getResilienceInstance();
        this.instance = configured != null ? configured : DEPENDENCY;
    }

    public List<InstrumentResolution> resolve(List<Key> keys) {
        Response response = executor.call(DEPENDENCY, instance, "resolveInstruments", () ->
                restClient.post()
                        .uri("/internal/v1/instruments/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new Request(keys))
                        .retrieve()
                        .body(Response.class));
        return response == null || response.results() == null ? List.of() : response.results();
    }

    /** Lookup key: identifier source (CUSIP/ISIN/SEDOL/EXCHANGE_SYMBOL), value, optional exchange MIC. */
    public record Key(String source, String value, String exchangeMic) {
    }

    private record Request(List<Key> items) {
    }

    private record Response(List<InstrumentResolution> results) {
    }

    /** Per-key resolution: found/ambiguous flags plus the resolved instrument summary (null if unresolved). */
    public record InstrumentResolution(String source,
                                       String value,
                                       String exchangeMic,
                                       boolean found,
                                       boolean ambiguous,
                                       InstrumentSummary instrument) {
    }

    /** Subset of the enrichment instrument projection the engine needs (extra fields ignored). */
    public record InstrumentSummary(String instrumentId,
                                    String name,
                                    String instrumentType,
                                    String exchangeMic,
                                    String currencyCode,
                                    String status) {
    }
}
