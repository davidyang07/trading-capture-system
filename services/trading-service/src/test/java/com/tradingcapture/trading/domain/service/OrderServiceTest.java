package com.tradingcapture.trading.domain.service;

import com.tradingcapture.trading.domain.exception.BusinessRuleException;
import com.tradingcapture.trading.domain.exception.ErrorCode;
import com.tradingcapture.trading.domain.exception.ResourceNotFoundException;
import com.tradingcapture.trading.domain.model.Order;
import com.tradingcapture.trading.domain.model.OrderStatus;
import com.tradingcapture.trading.domain.model.OrderType;
import com.tradingcapture.trading.domain.model.PriceType;
import com.tradingcapture.trading.domain.model.SettleType;
import com.tradingcapture.trading.domain.model.Side;
import com.tradingcapture.trading.domain.port.AccountReferencePort;
import com.tradingcapture.trading.domain.port.InstrumentReferencePort;
import com.tradingcapture.trading.domain.repository.ExecutionRepository;
import com.tradingcapture.trading.domain.repository.OrderRepository;
import com.tradingcapture.trading.domain.service.command.OrderCommands;
import com.tradingcapture.trading.observability.TradingMetrics;
import com.tradingcapture.trading.testsupport.TradingFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private ExecutionRepository executions;
    @Mock
    private InstrumentReferencePort instrumentPort;
    @Mock
    private AccountReferencePort accountPort;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, executions, instrumentPort, accountPort,
                new TradingMetrics(new SimpleMeterRegistry()),
                Clock.fixed(TradingFixtures.FIXED_NOW, ZoneOffset.UTC));
    }

    private OrderCommands.CreateOrder createCommand(String orderId) {
        return new OrderCommands.CreateOrder(orderId, "cl-1", "ACC-1", "AAPL",
                Side.BUY, OrderType.LIMIT, new BigDecimal("189.25"), PriceType.PER_UNIT,
                "USD", new BigDecimal("100"), SettleType.T_PLUS_2, LocalDate.parse("2026-07-03"));
    }

    private OrderCommands.UpdateOrder updateCommand(String quantity) {
        return new OrderCommands.UpdateOrder("cl-2", "ACC-2", "MSFT",
                Side.SELL, OrderType.MARKET, new BigDecimal("415.50"), PriceType.PER_UNIT,
                "EUR", new BigDecimal(quantity), SettleType.REGULAR, LocalDate.parse("2026-07-06"));
    }

    @Test
    void createDerivesOwnerFromAccessContextAndGeneratesId() {
        when(orders.existsById(anyString())).thenReturn(false);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.create(TradingFixtures.investor("investor1"), createCommand(null));

        assertThat(order.getOrderId()).startsWith("ORD-");
        assertThat(order.getOwnerSubject()).isEqualTo("investor1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getCumQty()).isEqualByComparingTo("0");
        assertThat(order.getLeavesQty()).isEqualByComparingTo("100");
        assertThat(order.getPrice().scale()).isEqualTo(8);
        assertThat(order.getQuantity().scale()).isEqualTo(4);
        verify(instrumentPort).validateInstrument("AAPL");
        verify(accountPort).validateAccountForTrading("ACC-1");
    }

    @Test
    void createKeepsClientSuppliedId() {
        when(orders.existsById("ORD-CLIENT-1")).thenReturn(false);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.create(TradingFixtures.admin(), createCommand("ORD-CLIENT-1"));

        assertThat(order.getOrderId()).isEqualTo("ORD-CLIENT-1");
        assertThat(order.getOwnerSubject()).isEqualTo("tcs-admin");
    }

    @Test
    void createRejectsDuplicateOrderId() {
        when(orders.existsById("ORD-DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(TradingFixtures.admin(), createCommand("ORD-DUP")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DUPLICATE_ORDER_ID));
        verify(orders, never()).save(any());
    }

    @Test
    void getMasksUnownedOrderAsNotFound() {
        Order order = TradingFixtures.order("ORD-1", "investor2", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.get(TradingFixtures.investor("investor1"), "ORD-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllowsOwnerAdminAndMachine() {
        Order order = TradingFixtures.order("ORD-1", "investor2", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThat(service.get(TradingFixtures.investor("investor2"), "ORD-1")).isSameAs(order);
        assertThat(service.get(TradingFixtures.admin(), "ORD-1")).isSameAs(order);
        assertThat(service.get(TradingFixtures.machine(), "ORD-1")).isSameAs(order);
    }

    @Test
    void updateRequiresNewStatus() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        order.applyFillState(new BigDecimal("40"), TradingFixtures.FIXED_NOW);
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(TradingFixtures.investor("investor1"), "ORD-1", updateCommand("200")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ORDER_NOT_ELIGIBLE));
    }

    @Test
    void updateRejectsOrdersWithExecutionHistoryEvenWhenNew() {
        // A fully cancelled order is NEW again; editing it would rewrite what its history describes.
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.existsByOrderOrderId("ORD-1")).thenReturn(true);

        assertThatThrownBy(() -> service.update(TradingFixtures.investor("investor1"), "ORD-1", updateCommand("200")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ORDER_HAS_EXECUTIONS));
        assertThat(order.getQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void updateAppliesFieldsWhileNew() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        Order updated = service.update(TradingFixtures.investor("investor1"), "ORD-1", updateCommand("250"));

        assertThat(updated.getQuantity()).isEqualByComparingTo("250");
        assertThat(updated.getLeavesQty()).isEqualByComparingTo("250");
        assertThat(updated.getInstrumentId()).isEqualTo("MSFT");
        verify(instrumentPort).validateInstrument("MSFT");
        verify(accountPort).validateAccountForTrading("ACC-2");
    }

    @Test
    void deleteRequiresNewStatus() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        order.applyFillState(new BigDecimal("100"), TradingFixtures.FIXED_NOW);
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.delete(TradingFixtures.investor("investor1"), "ORD-1"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ORDER_NOT_ELIGIBLE));
        verify(orders, never()).delete(any(Order.class));
    }

    @Test
    void deleteRejectsOrdersWithExecutionHistoryEvenWhenNew() {
        // A fully cancelled order is NEW again but its audit history must survive.
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.existsByOrderOrderId("ORD-1")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(TradingFixtures.investor("investor1"), "ORD-1"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ORDER_HAS_EXECUTIONS));
        verify(orders, never()).delete(any(Order.class));
    }

    @Test
    void deleteRemovesEligibleOrder() {
        Order order = TradingFixtures.order("ORD-1", "investor1", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));
        when(executions.existsByOrderOrderId("ORD-1")).thenReturn(false);

        service.delete(TradingFixtures.investor("investor1"), "ORD-1");

        verify(orders).delete(order);
    }

    @Test
    void mutationsOnUnownedOrdersAreMaskedAsNotFound() {
        Order order = TradingFixtures.order("ORD-1", "investor2", "100");
        when(orders.findById("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(TradingFixtures.investor("investor1"), "ORD-1", updateCommand("200")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(TradingFixtures.investor("investor1"), "ORD-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
