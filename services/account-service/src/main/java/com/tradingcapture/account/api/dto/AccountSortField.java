package com.tradingcapture.account.api.dto;

/** Whitelisted account sort keys — arbitrary property paths are never accepted. */
public enum AccountSortField {
    ACCOUNT_ID("accountId"),
    ACCOUNT_NAME("accountName"),
    STATUS("status"),
    CREATED_AT("createdAt");

    private final String property;

    AccountSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
