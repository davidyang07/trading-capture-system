package com.tradingcapture.enrichment.domain.model;

/**
 * Enum persisted as an explicit, stable string code (ADR-0014: never {@code EnumType.ORDINAL}).
 * Reference-data enums carry no external code, so {@code code()} is their own {@code name()} —
 * but routing every enum through {@link CodeEnum} and a {@code @Convert} converter keeps the
 * persistence contract uniform and reordering-safe.
 */
public interface CodeEnum {

    String code();
}
