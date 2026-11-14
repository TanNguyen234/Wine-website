package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Cart;
import com.strongwine.strongwine.entity.CartItem;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.exception.InsufficientStockException;
import com.strongwine.strongwine.repository.CartItemRepository;
import com.strongwine.strongwine.repository.CartRepository;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private UserRepository userRepository;

    @Mock
    private WineRepository wineRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

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

    @Test
    void cancellingPendingOrderAcquiresPessimisticLockAndCascadesToShipmentPort() {
        Order order = pendingOrder();
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));

        orderService.cancelPendingOrder(42L, "admin01", "Customer requested");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(orderRepository).findByIdWithPessimisticLock(42L);
        verify(inventoryService, times(1)).releaseOrderReservation(order);
        verify(shipmentOrderLifecyclePort).cancelShipmentForOrder(42L, "admin01", "Customer requested");
        verify(adminAuditLogService).log(eq("admin01"), eq("CANCEL_ORDER"), eq("ORDER"), eq(42L), any(), any(), any());
    }

    @Test
    void paidOrderDoesNotCascadeCancelToShipmentPort() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));

        orderService.cancelPendingOrder(42L, "admin01", "Attempt cancel paid");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(inventoryService, never()).releaseOrderReservation(any());
        verify(shipmentOrderLifecyclePort, never()).cancelShipmentForOrder(any(), any(), any());
    }

    @Test
    void createPendingOrderV3OverloadPersistsPricingBreakdownVoucherAndWarehouse() {
        User customer = new User();
        customer.setId(5L);
        customer.setUsername("customer5");

        Wine wine = new Wine();
        wine.setId(10L);
        wine.setName("Pomerol Reserve");
        wine.setPrice(new BigDecimal("1000000.00"));

        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        warehouse.setName("Hanoi Vault");

        when(userRepository.findById(5L)).thenReturn(Optional.of(customer));
        when(wineRepository.findById(10L)).thenReturn(Optional.of(wine));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        Order created = orderService.createPendingOrder(
                5L,
                Map.of(10L, 2),
                "Nguyen Van A",
                "0901234567",
                "a@example.com",
                "123 Le Loi",
                10.77,
                106.70,
                "Giao buổi tối",
                PaymentMethod.COD.name(),
                new BigDecimal("2000000.00"),
                new BigDecimal("200000.00"),
                new BigDecimal("35000.00"),
                "VIP200K",
                warehouse
        );

        assertThat(created.getId()).isEqualTo(77L);
        assertThat(created.getSubtotalAmount()).isEqualByComparingTo("2000000.00");
        assertThat(created.getDiscountAmount()).isEqualByComparingTo("200000.00");
        assertThat(created.getShippingFee()).isEqualByComparingTo("35000.00");
        assertThat(created.getVoucherCode()).isEqualTo("VIP200K");
        assertThat(created.getAllocatedWarehouse()).isSameAs(warehouse);
        assertThat(created.getTotalPrice()).isEqualByComparingTo("1835000.00");
        verify(inventoryService).reserveForOrder(created);
    }

    @Test
    void reorderOrder_verifiesOwnerAddsActiveInStockItemsAndReportsSkippedWines() {
        User owner = new User();
        owner.setId(5L);
        owner.setUsername("customer5");

        Wine activeWine = new Wine();
        activeWine.setId(10L);
        activeWine.setName("Pomerol Reserve");
        activeWine.setPrice(new BigDecimal("1000000.00"));
        activeWine.setDeleted(false);

        Wine deletedWine = new Wine();
        deletedWine.setId(11L);
        deletedWine.setName("Discontinued Merlot");
        deletedWine.setPrice(new BigDecimal("800000.00"));
        deletedWine.setDeleted(true);

        Wine outOfStockWine = new Wine();
        outOfStockWine.setId(12L);
        outOfStockWine.setName("Sold Out Vintage");
        outOfStockWine.setPrice(new BigDecimal("1500000.00"));
        outOfStockWine.setDeleted(false);

        Order order = new Order();
        order.setId(88L);
        order.setUser(owner);
        order.setOrderItems(List.of(
                new OrderItem(order, activeWine, 2, new BigDecimal("1000000.00")),
                new OrderItem(order, deletedWine, 1, new BigDecimal("800000.00")),
                new OrderItem(order, outOfStockWine, 1, new BigDecimal("1500000.00"))
        ));

        Cart cart = new Cart();
        cart.setId(300L);
        cart.setUser(owner);

        when(orderRepository.findById(88L)).thenReturn(Optional.of(order));
        when(inventoryService.getAvailableStockByWineIds(List.of(10L, 12L)))
                .thenReturn(Map.of(10L, 6, 12L, 0));
        when(cartRepository.findByUserId(5L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndWineId(300L, 10L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderService.ReorderSummary summary = orderService.reorderOrder(88L, 5L);

        assertThat(summary.addedCount()).isEqualTo(1);
        assertThat(summary.addedQuantities()).containsEntry(10L, 2);
        assertThat(summary.skippedItems()).containsExactlyInAnyOrder("Discontinued Merlot", "Sold Out Vintage");
        verify(cartItemRepository).save(any(CartItem.class));

        assertThatThrownBy(() -> orderService.reorderOrder(88L, 999L))
                .isInstanceOf(SecurityException.class);
    }
}
