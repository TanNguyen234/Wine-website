package com.strongwine.strongwine.config;

import com.strongwine.strongwine.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class ShipmentAutoDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentAutoDispatchScheduler.class);

    private final ShipmentService shipmentService;
    private final ReentrantLock dispatchLock = new ReentrantLock();

    @Value("${app.shipment.dispatch.enabled:true}")
    private boolean dispatchEnabled;

    public ShipmentAutoDispatchScheduler(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Scheduled(
            initialDelayString = "${app.shipment.dispatch.initial-delay-ms:15000}",
            fixedDelayString = "${app.shipment.dispatch.fixed-delay-ms:60000}"
    )
    public void dispatchPendingShipments() {
        if (!dispatchEnabled || !dispatchLock.tryLock()) {
            return;
        }

        try {
            int processed = shipmentService.dispatchAutoShipmentQueue();
            if (processed > 0) {
                log.info("Auto dispatch assigned {} pending shipments", processed);
            }
        } catch (Exception ex) {
            log.warn("Auto dispatch execution failed: {}", ex.getMessage());
        } finally {
            dispatchLock.unlock();
        }
    }
}
