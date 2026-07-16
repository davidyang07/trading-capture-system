package com.tradingcapture.account.domain.repository;

import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.model.AccountType;
import org.springframework.data.jpa.domain.Specification;

/** Composable JPA Specifications for the account search endpoint (deterministic, indexed columns). */
public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    public static Specification<Account> ownedBy(String subject) {
        return (root, query, cb) -> cb.equal(root.get("ownerSubject"), subject);
    }

    public static Specification<Account> hasStatus(AccountStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Account> hasType(AccountType type) {
        return (root, query, cb) -> cb.equal(root.get("accountType"), type);
    }

    public static Specification<Account> hasCurrency(String currency) {
        return (root, query, cb) -> cb.equal(root.get("baseCurrency"), currency);
    }
}
