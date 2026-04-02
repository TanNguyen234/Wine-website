package com.strongwine.strongwine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PaidOrderShipmentBackfillInitializer {

    private static final Logger log = LoggerFactory.getLogger(PaidOrderShipmentBackfillInitializer.class);

    private final ShipmentService shipmentService;
    private final AtomicBoolean executed = new AtomicBoolean(false);

    @Value("${strongwine.shipment.backfill-on-startup:true}")
    private boolean backfillOnStartup;

    public PaidOrderShipmentBackfillInitializer(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runBackfillOnceOnStartup() {
        if (!backfillOnStartup || !executed.compareAndSet(false, true)) {
            return;
        }

        ShipmentService.BackfillSummary summary = shipmentService.backfillMissingShipmentsForPaidOrders();
        log.info("Paid-order shipment backfill completed: created={}, existed={}, skipped={}, failed={}",
                summary.created(), summary.existed(), summary.skipped(), summary.failed());
    }
}
