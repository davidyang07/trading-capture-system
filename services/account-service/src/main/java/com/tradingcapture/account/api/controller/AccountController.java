package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.api.dto.AccountResponse;
import com.tradingcapture.account.api.dto.AccountSearchRequest;
import com.tradingcapture.account.api.dto.CreateAccountRequest;
import com.tradingcapture.account.api.dto.PageResponse;
import com.tradingcapture.account.api.dto.UpdateAccountRequest;
import com.tradingcapture.account.api.mapper.AccountMapper;
import com.tradingcapture.account.domain.model.Account;
import com.tradingcapture.account.domain.model.AccountStatus;
import com.tradingcapture.account.domain.service.AccessContext;
import com.tradingcapture.account.domain.service.AccountService;
import com.tradingcapture.account.security.AccessContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Account CRUD, search, and lifecycle. Creating an account requires the {@code account:admin}
 * scope (investors may open their own, which start PENDING); renaming and every lifecycle
 * transition additionally require the realm {@code ADMIN} role — the scope alone is carried by
 * every admin-web user token, so it never authorizes administration by itself. Reads require
 * {@code account:balance}. Ownership isolation for investors is enforced in the service layer
 * against the JWT-derived subject (unowned accounts answer 404, never 403 — existence is not
 * revealed).
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper mapper;
    private final AccessContextFactory accessContextFactory;

    public AccountController(AccountService accountService,
                            AccountMapper mapper,
                            AccessContextFactory accessContextFactory) {
        this.accountService = accountService;
        this.mapper = mapper;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_account:admin')")
    @Operation(summary = "Create an account (owner = authenticated subject; admins may nominate another owner)")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    @ApiResponse(responseCode = "409", description = "Duplicate accountId")
    public ResponseEntity<AccountResponse> create(Authentication authentication,
                                                  @Valid @RequestBody CreateAccountRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Account account = accountService.create(ctx, request.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/accounts/{accountId}").build(account.getAccountId()))
                .body(mapper.toResponse(account));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_account:balance')")
    @Operation(summary = "Search accounts (investors: own accounts only; deterministic sort with accountId tiebreak)")
    public PageResponse<AccountResponse> search(Authentication authentication,
                                                @ParameterObject @Valid AccountSearchRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Page<Account> page = accountService.search(ctx, request.toCriteria(), request.toPageable());
        return PageResponse.of(page, mapper::toResponse);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_account:balance')")
    @Operation(summary = "Account detail")
    @ApiResponse(responseCode = "200", description = "Account")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    public AccountResponse get(Authentication authentication, @PathVariable String accountId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(accountService.get(ctx, accountId));
    }

    @PutMapping("/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_account:admin') and hasRole('ADMIN')")
    @Operation(summary = "Rename an account")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    public AccountResponse update(Authentication authentication,
                                  @PathVariable String accountId,
                                  @Valid @RequestBody UpdateAccountRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(accountService.update(ctx, accountId, request.toCommand()));
    }

    @PostMapping("/{accountId}/activate")
    @PreAuthorize("hasAuthority('SCOPE_account:admin') and hasRole('ADMIN')")
    @Operation(summary = "Activate a PENDING or SUSPENDED account")
    @ApiResponse(responseCode = "200", description = "Activated")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "409", description = "Illegal lifecycle transition")
    public AccountResponse activate(Authentication authentication, @PathVariable String accountId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(accountService.changeStatus(ctx, accountId, AccountStatus.ACTIVE));
    }

    @PostMapping("/{accountId}/suspend")
    @PreAuthorize("hasAuthority('SCOPE_account:admin') and hasRole('ADMIN')")
    @Operation(summary = "Suspend an ACTIVE account — suspended accounts cannot post transactions")
    @ApiResponse(responseCode = "200", description = "Suspended")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "409", description = "Illegal lifecycle transition")
    public AccountResponse suspend(Authentication authentication, @PathVariable String accountId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(accountService.changeStatus(ctx, accountId, AccountStatus.SUSPENDED));
    }

    @PostMapping("/{accountId}/close")
    @PreAuthorize("hasAuthority('SCOPE_account:admin') and hasRole('ADMIN')")
    @Operation(summary = "Close an account (requires every ledger-derived balance to be zero)")
    @ApiResponse(responseCode = "200", description = "Closed")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "409", description = "Illegal transition or non-zero balance")
    public AccountResponse close(Authentication authentication, @PathVariable String accountId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(accountService.changeStatus(ctx, accountId, AccountStatus.CLOSED));
    }
}
