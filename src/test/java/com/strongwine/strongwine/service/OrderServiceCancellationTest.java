package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.InsufficientStockException;
import com.strongwine.strongwine.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceCancellationTest {

    private static final String TXN_REF = "PAY-0123456789ABCDEF";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Order pendingOrder() {
        User customer = new User();
        customer.setId(5L);
        customer.setUsername("customer5");

        Order order = new Order();
        order.setId(42L);
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderItems(new ArrayList<>());
        return order;
    }

    @Test
    void cancellingTheSameOrderTwiceReleasesItsReservationOnlyOnce() {
        Order order = pendingOrder();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        // VNPay sends a failure on the browser return and again on the IPN.
        orderService.cancelPendingOrder(42L);
        orderService.cancelPendingOrder(42L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService, times(1)).releaseOrderReservation(order);
    }

    @Test
    void paidOrderCannotBeCancelled() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        orderService.cancelPendingOrder(42L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(inventoryService, never()).releaseOrderReservation(any());
    }

    @Test
    void paymentArrivingAfterCancellationReservesStockAgainBeforeConfirming() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        orderService.markOrderPaid(42L, TXN_REF);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        InOrder inOrder = inOrder(inventoryService);
        inOrder.verify(inventoryService).reserveForOrder(order);
        inOrder.verify(inventoryService).confirmOrderPayment(order);
    }

    @Test
    void paymentArrivingAfterCancellationFailsWhenStockIsGone() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        doThrow(new InsufficientStockException("Insufficient stock for wine: test"))
                .when(inventoryService).reserveForOrder(order);

        assertThatThrownBy(() -> orderService.markOrderPaid(42L, TXN_REF))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService, never()).confirmOrderPayment(any());
    }
}
