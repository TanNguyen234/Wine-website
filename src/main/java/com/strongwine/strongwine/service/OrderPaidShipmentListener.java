package com.strongwine.strongwine.service;

import com.strongwine.strongwine.event.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderPaidShipmentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidShipmentListener.class);

    private final ShipmentService shipmentService;

    public OrderPaidShipmentListener(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            shipmentService.handleOrderPaidEvent(event.orderId(), event.paymentReference());
        } catch (Exception ex) {
            log.warn("OrderPaid event failed for order {}: {}", event.orderId(), ex.getMessage());
        }
    }
}
