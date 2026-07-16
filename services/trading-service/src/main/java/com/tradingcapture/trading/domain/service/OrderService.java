package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.repository.OrderRepository;
import com.tradingcapture.trading.domain.repository.OrderSpecifications;
import com.tradingcapture.trading.domain.port.AccountReferencePort;
import com.tradingcapture.trading.domain.port.InstrumentReferencePort;
import com.tradingcapture.trading.domain.service.command.OrderCommands;
import com.tradingcapture.trading.observability.TradingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order lifecycle: create / read / search / update / delete with explicit eligibility rules.
 *
 * <ul>
 *   <li>Owner subject always comes from the {@link AccessContext} (the verified JWT).</li>
 *   <li>Non-privileged callers only ever see or touch their own orders (masked as 404).</li>
 *   <li>Update and delete require status NEW; delete additionally requires that no execution
 *       row references the order — cancelled history still blocks deletion, and the DB
 *       FK (ON DELETE RESTRICT) backs the rule up.</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ExecutionRepository executions;
    private final InstrumentReferencePort instrumentPort;
    private final AccountReferencePort accountPort;
    private final TradingMetrics metrics;
    private final Clock clock;

    public OrderService(OrderRepository orders,
                        ExecutionRepository executions,
                        InstrumentReferencePort instrumentPort,
                        AccountReferencePort accountPort,
                        TradingMetrics metrics,
                        Clock clock) {
        this.orders = orders;
        this.executions = executions;
        this.instrumentPort = instrumentPort;
        this.accountPort = accountPort;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Order create(AccessContext ctx, OrderCommands.CreateOrder command) {
        String orderId = StringUtils.hasText(command.orderId())
                ? command.orderId()
                : TradingIds.newOrderId();
        if (orders.existsById(orderId)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_ORDER_ID,
                    "Order ID already in use: " + orderId);
        }
        instrumentPort.validateInstrument(command.instrumentId());
        accountPort.validateAccountForTrading(command.accountId());

        Order order = new Order(orderId,
                command.clOrdId(),
                ctx.subject(),
                command.accountId(),
                command.instrumentId(),
                command.side(),
                command.orderType(),
                normalizePrice(command.price()),
                command.priceType(),
                command.currency(),
                normalizeQuantity(command.quantity()),
                command.settleType(),
                command.settleDate(),
                Instant.now(clock));
        orders.save(order);
        metrics.orderCreated();
        log.info("Order created: orderId={} instrumentId={} side={} status={}",
                order.getOrderId(), order.getInstrumentId(), order.getSide(), order.getStatus());
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(AccessContext ctx, String orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.order(orderId));
        ctx.requireVisible(order);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> search(AccessContext ctx, OrderSearchCriteria criteria, Pageable pageable) {
        List<Specification<Order>> parts = new ArrayList<>();
        if (!ctx.canAccessAllOrders()) {
            // Ownership isolation is enforced here, in the query itself — never client-side.
            parts.add(OrderSpecifications.ownedBy(ctx.subject()));
        } else if (StringUtils.hasText(criteria.ownerSubject())) {
            parts.add(OrderSpecifications.ownedBy(criteria.ownerSubject()));
        }
        if (criteria.status() != null) {
            parts.add(OrderSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.side() != null) {
            parts.add(OrderSpecifications.hasSide(criteria.side()));
        }
        if (StringUtils.hasText(criteria.instrumentId())) {
            parts.add(OrderSpecifications.hasInstrumentId(criteria.instrumentId()));
        }
        if (StringUtils.hasText(criteria.accountId())) {
            parts.add(OrderSpecifications.hasAccountId(criteria.accountId()));
        }
        if (criteria.settleDateFrom() != null) {
            parts.add(OrderSpecifications.settleDateFrom(criteria.settleDateFrom()));
        }
        if (criteria.settleDateTo() != null) {
            parts.add(OrderSpecifications.settleDateTo(criteria.settleDateTo()));
        }
        return orders.findAll(Specification.allOf(parts), pageable);
    }

    @Transactional
    public Order update(AccessContext ctx, String orderId, OrderCommands.UpdateOrder command) {
        Order order = get(ctx, orderId);
        if (!order.isNew()) {
            throw new BusinessRuleException(ErrorCode.ORDER_NOT_ELIGIBLE,
                    "Only NEW orders may be updated; order " + orderId + " is " + order.getStatus());
        }
        BigDecimal quantity = normalizeQuantity(command.quantity());
        if (quantity.compareTo(order.getCumQty()) < 0) {
            // Unreachable while NEW implies cumQty == 0; kept as an explicit invariant guard.
            throw new BusinessRuleException(ErrorCode.ORDER_NOT_ELIGIBLE,
                    "Quantity " + quantity + " is below the executed quantity " + order.getCumQty());
        }
        instrumentPort.validateInstrument(command.instrumentId());
        accountPort.validateAccountForTrading(command.accountId());

        order.applyUpdate(command.clOrdId(),
                command.accountId(),
                command.instrumentId(),
                command.side(),
                command.orderType(),
                normalizePrice(command.price()),
                command.priceType(),
                command.currency(),
                quantity,
                command.settleType(),
                command.settleDate(),
                Instant.now(clock));
        metrics.orderUpdated();
        log.info("Order updated: orderId={} status={}", order.getOrderId(), order.getStatus());
        return order;
    }

    @Transactional
    public void delete(AccessContext ctx, String orderId) {
        Order order = get(ctx, orderId);
        if (!order.isNew()) {
            throw new BusinessRuleException(ErrorCode.ORDER_NOT_ELIGIBLE,
                    "Only NEW orders may be deleted; order " + orderId + " is " + order.getStatus());
        }
        if (executions.existsByOrderOrderId(orderId)) {
            throw new BusinessRuleException(ErrorCode.ORDER_HAS_EXECUTIONS,
                    "Order " + orderId + " has execution history and cannot be deleted");
        }
        orders.delete(order);
        metrics.orderDeleted();
        log.info("Order deleted: orderId={}", orderId);
    }

    private static BigDecimal normalizePrice(BigDecimal price) {
        return price.setScale(8, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity.setScale(4, RoundingMode.UNNECESSARY);
    }
}
