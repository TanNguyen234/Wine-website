package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RealtimeEventListener {

    private final SseNotificationService sseService;

    public RealtimeEventListener(SseNotificationService sseService) {
        this.sseService = sseService;
    }

    @EventListener
    public void handleOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        Map<String, Object> payload = Map.of(
                "orderId", event.getOrderId(),
                "status", event.getStatus(),
                "message", event.getMessage(),
                "timestamp", System.currentTimeMillis()
        );
        sseService.broadcastToOrder(event.getOrderId(), "order_status_updated", payload);
        sseService.broadcastToAdmin("admin_order_notification", payload);
    }

    @EventListener
    public void handleShipmentLiveStatus(ShipmentLiveStatusEvent event) {
        Map<String, Object> payload = Map.of(
                "shipmentId", event.getShipmentId(),
                "orderId", event.getOrderId(),
                "status", event.getStatus(),
                "shipperName", event.getShipperName() != null ? event.getShipperName() : "",
                "shipperPhone", event.getShipperPhone() != null ? event.getShipperPhone() : "",
                "vehiclePlate", event.getVehiclePlate() != null ? event.getVehiclePlate() : "",
                "timestamp", System.currentTimeMillis()
        );
        sseService.broadcastToOrder(event.getOrderId(), "shipment_step_updated", payload);
        sseService.broadcastToAdmin("admin_shipment_notification", payload);
    }
}
