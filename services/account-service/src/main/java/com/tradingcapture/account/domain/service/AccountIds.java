package com.tradingcapture.account.domain.service;

import java.util.UUID;

/** Generates the service's business identifiers. */
public final class AccountIds {

    private AccountIds() {
    }

    public static String newAccountId() {
        return "ACC-" + UUID.randomUUID();
    }

    public static String newCashTransactionRef() {
        return "CT-" + UUID.randomUUID();
    }

    public static String newSecurityTransactionRef() {
        return "ST-" + UUID.randomUUID();
    }
}
