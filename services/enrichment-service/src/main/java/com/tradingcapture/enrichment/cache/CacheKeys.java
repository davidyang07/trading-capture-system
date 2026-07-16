package com.tradingcapture.enrichment.cache;

import com.tradingcapture.enrichment.domain.model.IdentifierSource;

/**
 * Normalized Redis key builders. Identifier values and codes are upper-cased so the key space
 * is case-insensitive and deterministic; a null dimension is rendered as {@code *}.
 */
public final class CacheKeys {

    private static final String PREFIX = "enrichment:";

    private CacheKeys() {
    }

    public static String instrument(String instrumentId) {
        return PREFIX + "instrument:" + instrumentId;
    }

    public static String identifier(IdentifierSource source, String value, String exchangeMic) {
        return PREFIX + "altid:" + source.name() + ":" + norm(value) + ":" + norm(exchangeMic);
    }

    public static String exchange(String mic) {
        return PREFIX + "exchange:" + norm(mic);
    }

    public static String currency(String code) {
        return PREFIX + "currency:" + norm(code);
    }

    /**
     * Single key for the whole active fee-rule set. The set is filtered by dimension in memory,
     * so one eviction invalidates every fee-rule lookup on any administrative mutation.
     */
    public static String feeRulesActive() {
        return PREFIX + "feerules:active";
    }

    private static String norm(String value) {
        return value == null ? "*" : value.trim().toUpperCase();
    }
}
