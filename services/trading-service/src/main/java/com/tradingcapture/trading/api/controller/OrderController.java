package com.tradingcapture.trading.api.controller;

import com.tradingcapture.trading.api.dto.CreateOrderRequest;
import com.tradingcapture.trading.api.dto.OrderDetailResponse;
import com.tradingcapture.trading.api.dto.OrderResponse;
import com.tradingcapture.trading.api.dto.OrderSearchRequest;
import com.tradingcapture.trading.api.dto.PageResponse;
import com.tradingcapture.trading.api.dto.UpdateOrderRequest;
import com.tradingcapture.trading.api.mapper.OrderMapper;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.service.AccessContext;
import com.tradingcapture.trading.domain.service.ExecutionQueryService;
import com.tradingcapture.trading.domain.service.OrderService;
import com.tradingcapture.trading.security.AccessContextFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Order CRUD/search. Scope requirements are declared per endpoint; ownership isolation for
 * investors is enforced in the service layer against the JWT-derived subject (unowned
 * resources answer 404, never 403 — existence is not revealed).
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "orders")
public class OrderController {

    private final OrderService orderService;
    private final ExecutionQueryService executionQueryService;
    private final OrderMapper mapper;
    private final AccessContextFactory accessContextFactory;

    public OrderController(OrderService orderService,
                           ExecutionQueryService executionQueryService,
                           OrderMapper mapper,
                           AccessContextFactory accessContextFactory) {
        this.orderService = orderService;
        this.executionQueryService = executionQueryService;
        this.mapper = mapper;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_order:create')")
    @Operation(summary = "Create an order (owner = authenticated subject; orderId generated when omitted)")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    @ApiResponse(responseCode = "409", description = "Duplicate orderId")
    public ResponseEntity<OrderResponse> create(Authentication authentication,
                                                @Valid @RequestBody CreateOrderRequest request,
                                                UriComponentsBuilder uriBuilder) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Order order = orderService.create(ctx, request.toCommand());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/orders/{orderId}").build(order.getOrderId()))
                .body(mapper.toResponse(order));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_order:read')")
    @Operation(summary = "Search orders (investors: own orders only; deterministic sort with orderId tiebreak)")
    public PageResponse<OrderResponse> search(Authentication authentication,
                                              @ParameterObject @Valid OrderSearchRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Page<Order> page = orderService.search(ctx, request.toCriteria(), request.toPageable());
        return PageResponse.of(page, mapper::toResponse);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_order:read')")
    @Operation(summary = "Order detail with its complete execution history")
    @ApiResponse(responseCode = "200", description = "Order")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    public OrderDetailResponse get(Authentication authentication, @PathVariable String orderId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        Order order = orderService.get(ctx, orderId);
        return mapper.toDetailResponse(order, executionQueryService.findByOrder(order));
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_order:update')")
    @Operation(summary = "Update an order (status NEW only; executed-quantity invariants enforced)")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    @ApiResponse(responseCode = "409", description = "Not eligible (not NEW)")
    public OrderResponse update(Authentication authentication,
                                @PathVariable String orderId,
                                @Valid @RequestBody UpdateOrderRequest request) {
        AccessContext ctx = accessContextFactory.from(authentication);
        return mapper.toResponse(orderService.update(ctx, orderId, request.toCommand()));
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_order:delete')")
    @Operation(summary = "Delete an order (status NEW and no execution history)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Unknown or not owned")
    @ApiResponse(responseCode = "409", description = "Not eligible (not NEW, or has executions)")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable String orderId) {
        AccessContext ctx = accessContextFactory.from(authentication);
        orderService.delete(ctx, orderId);
        return ResponseEntity.noContent().build();
    }
}
