package com.strongwine.strongwine.config;

import com.strongwine.strongwine.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ShipmentReconciliationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ShipmentReconciliationCoordinator.class);

    private final ShipmentService shipmentService;
    private final ReentrantLock reconcileLock = new ReentrantLock();
    private final AtomicLong lastWebReconcileAtMillis = new AtomicLong(0L);

    @Value("${app.shipment.reconcile.web-enabled:true}")
    private boolean webReconcileEnabled;

    @Value("${app.shipment.reconcile.startup-enabled:false}")
    private boolean startupReconcileEnabled;

    @Value("${app.shipment.reconcile.web-throttle-ms:60000}")
    private long webThrottleMs;

    public ShipmentReconciliationCoordinator(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        if (!startupReconcileEnabled) {
            return;
        }
        triggerReconcile("startup");
    }

    public void tryReconcileOnWebEntry() {
        if (!webReconcileEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastRun = lastWebReconcileAtMillis.get();
        if ((now - lastRun) < webThrottleMs) {
            return;
        }

        if (!lastWebReconcileAtMillis.compareAndSet(lastRun, now)) {
            return;
        }

        triggerReconcile("web-entry");
    }

    private void triggerReconcile(String source) {
        if (!reconcileLock.tryLock()) {
            return;
        }

        try {
            int processed = shipmentService.dispatchAutoShipmentQueue();
            if (processed > 0) {
                log.info("Shipment reconciliation from {} processed {} records", source, processed);
            }
        } catch (Exception ex) {
            log.warn("Shipment reconciliation from {} failed: {}", source, ex.getMessage());
        } finally {
            reconcileLock.unlock();
        }
    }
}
