package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.LowStockAlertEvent;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.service.NotificationService;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeEventListenerTest {

    @Mock private SseNotificationService sseService;
    @Mock private NotificationService notificationService;
    @Mock private OrderRepository orderRepository;

    @Test
    void testOnOrderStatusUpdatedEventBroadcastsToOrder() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService);
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(this, 101L, "PAID", "Đã thanh toán thành công");

        listener.handleOrderStatusUpdated(event);
        verify(sseService, times(1)).broadcastToOrder(eq(101L), eq("order_status_updated"), any());
        verify(sseService, times(1)).broadcastToAdmin(eq("admin_order_notification"), any());
    }

    @Test
    void testOnShipmentLiveStatusEventBroadcasts() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService);
        ShipmentLiveStatusEvent event = new ShipmentLiveStatusEvent(this, 202L, 101L, "DELIVERING", "Nguyễn Văn Shipper", "0901234567", "59A-12345");

        listener.handleShipmentLiveStatus(event);
        verify(sseService, times(1)).broadcastToOrder(eq(101L), eq("shipment_step_updated"), any());
    }

    @Test
    void lowStockAlertPersistsAdminNotificationAndBroadcastsSse() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService, notificationService, orderRepository);
        LowStockAlertEvent event = new LowStockAlertEvent(this, 10L, 5L, "Château Margaux", 1L, "Main Warehouse", 2, 10);

        listener.handleLowStockAlert(event);

        verify(notificationService, times(1)).createAdminNotification(
                contains("Low Stock"),
                contains("Château Margaux"),
                eq("/admin/inventory"),
                eq("WARNING"));
        verify(sseService, times(1)).broadcastToAdmin(eq("low_stock_alert"), any());
    }

    @Test
    void adminAlertPersistsAdminNotificationAndBroadcastsSse() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService, notificationService, orderRepository);
        AdminAlertEvent event = new AdminAlertEvent(this, "Return Requested", "Order #99 requested refund", "/admin/orders/99", "WARNING", 99L);

        listener.handleAdminAlert(event);

        verify(notificationService, times(1)).createAdminNotification(
                eq("Return Requested"),
                eq("Order #99 requested refund"),
                eq("/admin/orders/99"),
                eq("WARNING"));
        verify(sseService, times(1)).broadcastToAdmin(eq("admin_alert"), any());
    }

    @Test
    void orderPaidPersistsUserAndAdminNotificationsAndBroadcasts() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService, notificationService, orderRepository);
        User buyer = new User();
        buyer.setId(15L);
        Order order = new Order();
        order.setId(88L);
        order.setUser(buyer);
        when(orderRepository.findById(88L)).thenReturn(Optional.of(order));

        listener.handleOrderPaid(new OrderPaidEvent(88L, "VNPAY-123"));

        verify(notificationService, times(1)).createUserNotification(
                eq(buyer),
                anyString(),
                contains("88"),
                eq("/orders/88"),
                eq("INFO"));
        verify(notificationService, times(1)).createAdminNotification(
                anyString(),
                contains("88"),
                eq("/admin/orders/88"),
                eq("INFO"));
        verify(sseService, times(1)).broadcastToOrder(eq(88L), eq("order_paid"), any());
        verify(sseService, times(1)).broadcastToAdmin(eq("admin_order_paid"), any());
    }

    @Test
    void orderStatusUpdatedAlsoCreatesUserNotificationWhenOrderFound() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService, notificationService, orderRepository);
        User buyer = new User();
        buyer.setId(19L);
        Order order = new Order();
        order.setId(101L);
        order.setUser(buyer);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));

        listener.handleOrderStatusUpdated(new OrderStatusUpdatedEvent(this, 101L, "PAID", "Payment verified"));

        verify(sseService).broadcastToOrder(eq(101L), eq("order_status_updated"), any());
        verify(notificationService).createUserNotification(
                eq(buyer),
                contains("101"),
                contains("Payment verified"),
                eq("/orders/101"),
                eq("INFO"));
    }
}
