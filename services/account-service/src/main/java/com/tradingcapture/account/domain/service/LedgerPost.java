package com.tradingcapture.account.domain.service;

/**
 * Outcome of a ledger append. {@code replayed} is true when an idempotency key matched an
 * existing row, so the caller can answer 200 (replay) rather than 201 (created).
 */
public record LedgerPost<T>(T transaction, boolean replayed) {

    public static <T> LedgerPost<T> created(T transaction) {
        return new LedgerPost<>(transaction, false);
    }

    public static <T> LedgerPost<T> replayed(T transaction) {
        return new LedgerPost<>(transaction, true);
    }
}
