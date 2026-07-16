package com.tradingcapture.account.domain.service;

import com.tradingcapture.account.domain.exception.ResourceNotFoundException;
import com.tradingcapture.account.domain.model.Account;

/**
 * Authenticated caller identity as the domain sees it, derived exclusively from the verified
 * JWT by the security layer — never from request content.
 *
 * @param subject owner subject: the token's {@code preferred_username} claim when present
 *                (matches the deterministic Keycloak dev-realm usernames and seed data),
 *                otherwise the {@code sub} claim
 * @param admin   caller holds realm role ADMIN (may see and administer every account)
 */
public record AccessContext(String subject, boolean admin) {

    public boolean canAccessAllAccounts() {
        return admin;
    }

    /** True when this caller may see the given account (owner or admin). */
    public boolean canSee(Account account) {
        return admin || account.isOwnedBy(subject);
    }

    /**
     * Ownership gate: non-admin callers may see only their own accounts. Denials raise the
     * same not-found error as a missing account so existence of another investor's account is
     * never revealed (404 masking).
     */
    public void requireVisible(Account account) {
        if (!canSee(account)) {
            throw ResourceNotFoundException.account(account.getAccountId());
        }
    }
}
