package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeEventListenerTest {

    @Mock private SseNotificationService sseService;

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
}
