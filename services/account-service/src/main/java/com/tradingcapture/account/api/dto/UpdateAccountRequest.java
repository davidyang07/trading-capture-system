package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.service.command.AccountCommands;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The only client-editable account field is its display name; type/currency/owner are fixed. */
public record UpdateAccountRequest(
        @NotBlank @Size(max = 255)
        String accountName) {

    public AccountCommands.UpdateAccount toCommand() {
        return new AccountCommands.UpdateAccount(accountName);
    }
}
