package com.strongwine.strongwine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic scheduler that dispatches pending shipments to available shippers.
 * Acts as a safety net to rescue orphaned PENDING_ASSIGNMENT shipments
 * that were not assigned during initial creation or event handling.
 */
@Component
public class ShipmentDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDispatchScheduler.class);

    private final ShipmentService shipmentService;

    public ShipmentDispatchScheduler(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Scheduled(fixedRateString = "${strongwine.shipment.dispatch-interval-ms:60000}")
    public void periodicShipmentDispatch() {
        try {
            int dispatched = shipmentService.dispatchAutoShipmentQueue();
            if (dispatched > 0) {
                log.info("Periodic shipment dispatch: assigned {} shipment(s)", dispatched);
            }
        } catch (Exception ex) {
            log.warn("Periodic shipment dispatch failed", ex);
        }
    }
}
