package com.tradingcapture.account.api.dto;

import com.tradingcapture.account.domain.model.AccountType;
import com.tradingcapture.account.domain.service.command.AccountCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Account creation payload. The owner defaults to the authenticated subject; only an
 * administrator may set {@code ownerSubject} to create an account for someone else. Status is
 * always server-controlled (accounts start PENDING).
 */
public record CreateAccountRequest(
        @Schema(description = "Client-supplied business account ID; generated when omitted", example = "ACC-2026-0001")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "accountId must match ^[A-Za-z0-9._-]{1,64}$")
        String accountId,

        @Schema(description = "Administrator-only: owner subject; ignored (forced to the caller) for investors",
                example = "investor1")
        @Size(max = 255)
        String ownerSubject,

        @NotBlank @Size(max = 255)
        String accountName,

        @NotNull
        AccountType accountType,

        @Schema(description = "Account's base/reporting currency", example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "baseCurrency must be a 3-letter ISO code")
        String baseCurrency) {

    public AccountCommands.CreateAccount toCommand() {
        return new AccountCommands.CreateAccount(accountId, ownerSubject, accountName, accountType, baseCurrency);
    }
}
