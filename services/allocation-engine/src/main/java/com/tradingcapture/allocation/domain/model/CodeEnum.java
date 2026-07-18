package com.tradingcapture.allocation.domain.model;

/**
 * Enum persisted as an explicit, stable string code (ADR-0014: never {@code EnumType.ORDINAL}).
 * FIX-sourced enums carry their FIX wire code; internal state enums use their own name.
 */
public interface CodeEnum {

    String code();
}
