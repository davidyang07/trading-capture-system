package com.tradingcapture.account.api.controller;

import com.tradingcapture.account.api.dto.BalancesResponse;
import com.tradingcapture.account.api.dto.CashTransactionResponse;
import com.tradingcapture.account.api.dto.PageResponse;
import com.tradingcapture.account.api.dto.PostCashTransactionRequest;
import com.tradingcapture.account.api.dto.PostSecurityTransactionRequest;
import com.tradingcapture.account.api.dto.SecurityTransactionResponse;
import com.tradingcapture.account.api.mapper.TransactionMapper;
import com.tradingcapture.account.domain.model.CashTransaction;
import com.tradingcapture.account.domain.model.SecurityTransaction;
import com.tradingcapture.account.domain.service.AccessContext;
import com.tradingcapture.account.domain.service.LedgerPost;
import com.tradingcapture.account.domain.service.LedgerService;
import com.tradingcapture.account.security.AccessContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * Ledger posts, ledger history, and ledger-derived balances. All require {@code account:balance}
 * (held by investors, admins, and the trading-service machine client). Posting a ledger row
 * additionally requires the realm {@code ADMIN} role: a ledger post creates or moves cash and
 * securities, so investors (and machine clients) may read their ledgers and balances but never
 * write them. Ownership isolation is enforced in the service (unowned accounts answer 404).
 * Posts to a non-ACTIVE account are 409.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
@Validated
@Tag(name = "ledger")
public class AccountLedgerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerService ledgerService;
    private final TransactionMapper mapper;
    private final AccessContextFactory accessContextFactory;

    public AccountLedgerController(LedgerService ledgerService,
                                  TransactionMapper mapper,
                                  AccessContextFactory accessContextFactory) {
        this.ledgerService = ledgerService;
        this.mapper = mapper;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping("/cash-transactions")
    @PreAuthorize("hasAuthority('SCOPE_account:balance') and hasRole('ADMIN')")
    @Operation(summary = "Post a cash ledger row (immutable; sufficient-funds enforced for debits)")
    @ApiResponse(responseCode = "201", description = "Posted")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an existing row")
    @ApiResponse(responseCode = "409", description = "Insufficient funds / account not active / concurrent modification")
    public ResponseEntity<CashTransactionResponse> postCash(Authentication authentication,
                                                            @PathVariable String accountId,
                                                            @Valid @RequestBody PostCashTransactionRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        LedgerPost<CashTransaction> result = ledgerService.postCash(ctx, accountId, request.toCommand());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapper.toResponse(result.transaction()));
    }

    @GetMapping("/cash-transactions")
    @PreAuthorize("hasAuthority('SCOPE_account:balance')")
    @Operation(summary = "Cash ledger history (newest first)")
    public PageResponse<CashTransactionResponse> cashHistory(Authentication authentication,
                                                             @PathVariable String accountId,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Page<CashTransaction> history = ledgerService.cashHistory(ctx, accountId, pageable(page, size));
        return PageResponse.of(history, mapper::toResponse);
    }

    @PostMapping("/security-transactions")
    @PreAuthorize("hasAuthority('SCOPE_account:balance') and hasRole('ADMIN')")
    @Operation(summary = "Post a security ledger row (immutable; sufficient-position enforced for deliveries)")
    @ApiResponse(responseCode = "201", description = "Posted")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an existing row")
    @ApiResponse(responseCode = "409", description = "Insufficient position / account not active / concurrent modification")
    public ResponseEntity<SecurityTransactionResponse> postSecurity(Authentication authentication,
                                                                    @PathVariable String accountId,
                                                                    @Valid @RequestBody PostSecurityTransactionRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        LedgerPost<SecurityTransaction> result = ledgerService.postSecurity(ctx, accountId, request.toCommand());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapper.toResponse(result.transaction()));
    }

    @GetMapping("/security-transactions")
    @PreAuthorize("hasAuthority('SCOPE_account:balance')")
    @Operation(summary = "Security ledger history (newest first)")
    public PageResponse<SecurityTransactionResponse> securityHistory(Authentication authentication,
                                                                     @PathVariable String accountId,
                                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Page<SecurityTransaction> history = ledgerService.securityHistory(ctx, accountId, pageable(page, size));
        return PageResponse.of(history, mapper::toResponse);
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('SCOPE_account:balance')")
    @Operation(summary = "Ledger-derived cash balances (by currency) and security positions (by instrument)")
    @ApiResponse(responseCode = "200", description = "Balances")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    public BalancesResponse balances(Authentication authentication, @PathVariable String accountId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(ledgerService.balances(ctx, accountId));
    }

    private static Pageable pageable(int page, int size) {
        // Deterministic newest-first history with a stable id tiebreak.
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
