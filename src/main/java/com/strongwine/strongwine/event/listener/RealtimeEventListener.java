package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.LowStockAlertEvent;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.service.NotificationService;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RealtimeEventListener {

    private final SseNotificationService sseService;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;

    public RealtimeEventListener(SseNotificationService sseService) {
        this(sseService, null, null);
    }

    @Autowired
    public RealtimeEventListener(SseNotificationService sseService,
                                 NotificationService notificationService,
                                 OrderRepository orderRepository) {
        this.sseService = sseService;
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
    }

    @EventListener
    public void handleOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        Map<String, Object> payload = Map.of(
                "orderId", event.getOrderId(),
                "status", event.getStatus(),
                "message", event.getMessage() != null ? event.getMessage() : "",
                "timestamp", System.currentTimeMillis()
        );
        sseService.broadcastToOrder(event.getOrderId(), "order_status_updated", payload);
        sseService.broadcastToAdmin("admin_order_notification", payload);

        if (notificationService != null && orderRepository != null && event.getOrderId() != null) {
            String title = "Order #" + event.getOrderId() + " Update";
            String msg = event.getMessage() != null && !event.getMessage().isBlank()
                    ? event.getMessage()
                    : "Order status updated to " + event.getStatus();
            String severity = "CANCELLED".equalsIgnoreCase(event.getStatus())
                    || "RETURN_REQUESTED".equalsIgnoreCase(event.getStatus()) ? "WARNING" : "INFO";

            orderRepository.findById(event.getOrderId()).ifPresent(order -> {
                if (order.getUser() != null && order.getUser().getId() != null) {
                    notificationService.createUserNotification(
                            order.getUser(),
                            title,
                            msg,
                            "/orders/" + event.getOrderId(),
                            severity
                    );
                }
            });
        }
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

        if (notificationService != null && orderRepository != null && event.getOrderId() != null
                && isCustomerFacingShipmentMilestone(event.getStatus())) {
            String title = "Delivery Update - Order #" + event.getOrderId();
            String msg = "Shipment status: " + event.getStatus();
            String severity = "FAILED".equalsIgnoreCase(event.getStatus()) ? "WARNING" : "INFO";
            orderRepository.findById(event.getOrderId()).ifPresent(order -> {
                if (order.getUser() != null && order.getUser().getId() != null) {
                    notificationService.createUserNotification(
                            order.getUser(),
                            title,
                            msg,
                            "/orders/" + event.getOrderId(),
                            severity
                    );
                }
            });
        }
    }

    @EventListener
    public void handleLowStockAlert(LowStockAlertEvent event) {
        String wineName = event.getWineName() != null ? event.getWineName() : "Wine #" + event.getWineId();
        String warehouseName = event.getWarehouseName() != null ? event.getWarehouseName() : "Warehouse #" + event.getWarehouseId();
        int available = event.getAvailableQuantity() != null ? event.getAvailableQuantity() : 0;
        int reorder = event.getReorderLevel() != null ? event.getReorderLevel() : 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventoryId", event.getInventoryId());
        payload.put("wineId", event.getWineId());
        payload.put("wineName", wineName);
        payload.put("warehouseId", event.getWarehouseId());
        payload.put("warehouseName", warehouseName);
        payload.put("availableQuantity", available);
        payload.put("reorderLevel", reorder);
        payload.put("timestamp", System.currentTimeMillis());

        if (notificationService != null) {
            notificationService.createAdminNotification(
                    "Low Stock Alert: " + wineName,
                    String.format("%s in %s has %d available (reorder level: %d).",
                            wineName, warehouseName, available, reorder),
                    "/admin/inventory",
                    "WARNING"
            );
        }
        sseService.broadcastToAdmin("low_stock_alert", payload);
    }

    @EventListener
    public void handleAdminAlert(AdminAlertEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", event.getTitle() != null ? event.getTitle() : "Admin Alert");
        payload.put("message", event.getMessage() != null ? event.getMessage() : "");
        payload.put("linkUrl", event.getLinkUrl() != null ? event.getLinkUrl() : "");
        payload.put("severity", event.getSeverity() != null ? event.getSeverity() : "INFO");
        if (event.getReferenceOrderId() != null) {
            payload.put("referenceOrderId", event.getReferenceOrderId());
        }
        payload.put("timestamp", System.currentTimeMillis());

        if (notificationService != null) {
            notificationService.createAdminNotification(
                    event.getTitle(),
                    event.getMessage(),
                    event.getLinkUrl(),
                    event.getSeverity()
            );
        }
        sseService.broadcastToAdmin("admin_alert", payload);
    }

    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", event.orderId());
        payload.put("paymentReference", event.paymentReference() != null ? event.paymentReference() : "");
        payload.put("timestamp", System.currentTimeMillis());

        sseService.broadcastToOrder(event.orderId(), "order_paid", payload);
        sseService.broadcastToAdmin("admin_order_paid", payload);

        if (notificationService != null) {
            String refSuffix = event.paymentReference() != null && !event.paymentReference().isBlank()
                    ? " (Ref: " + event.paymentReference() + ")"
                    : "";
            notificationService.createAdminNotification(
                    "Order #" + event.orderId() + " Paid",
                    "Payment confirmed for order #" + event.orderId() + refSuffix,
                    "/admin/orders/" + event.orderId(),
                    "INFO"
            );

            if (orderRepository != null) {
                orderRepository.findById(event.orderId()).ifPresent(order -> {
                    if (order.getUser() != null && order.getUser().getId() != null) {
                        notificationService.createUserNotification(
                                order.getUser(),
                                "Order #" + event.orderId() + " Payment Confirmed",
                                "Payment received for order #" + event.orderId() + refSuffix,
                                "/orders/" + event.orderId(),
                                "INFO"
                        );
                    }
                });
            }
        }
    }

    private boolean isCustomerFacingShipmentMilestone(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "PICKED_UP", "DELIVERING", "COMPLETED", "FAILED" -> true;
            default -> false;
        };
    }
}
