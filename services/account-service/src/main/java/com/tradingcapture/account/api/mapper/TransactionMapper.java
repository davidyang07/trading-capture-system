package com.tradingcapture.account.api.mapper;

import com.tradingcapture.account.api.dto.BalancesResponse;
import com.tradingcapture.account.api.dto.CashTransactionResponse;
import com.tradingcapture.account.api.dto.SecurityTransactionResponse;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import com.tradingcapture.account.domain.service.balance.AccountBalances;
import org.springframework.stereotype.Component;

/** Hand-written mapping for ledger rows and ledger-derived balances. */
@Component
public class TransactionMapper {

    public CashTransactionResponse toResponse(CashTransaction transaction) {
        return new CashTransactionResponse(transaction.getId(),
                transaction.getTransactionRef(),
                transaction.getAccountId(),
                transaction.getDirection(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getExternalRef(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getPostedAt());
    }

    public SecurityTransactionResponse toResponse(SecurityTransaction transaction) {
        return new SecurityTransactionResponse(transaction.getId(),
                transaction.getTransactionRef(),
                transaction.getAccountId(),
                transaction.getDirection(),
                transaction.getTransactionType(),
                transaction.getInstrumentId(),
                transaction.getQuantity(),
                transaction.getExternalRef(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getPostedAt());
    }

    public BalancesResponse toResponse(AccountBalances balances) {
        return new BalancesResponse(balances.accountId(),
                balances.cashBalances().stream()
                        .map(cash -> new BalancesResponse.CashBalanceResponse(cash.currency(), cash.balance()))
                        .toList(),
                balances.securityPositions().stream()
                        .map(position -> new BalancesResponse.SecurityPositionResponse(
                                position.instrumentId(), position.quantity()))
                        .toList());
    }
}
