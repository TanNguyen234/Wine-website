package com.strongwine.strongwine.config;

import com.strongwine.strongwine.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LowStockAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertScheduler.class);
    private final InventoryService inventoryService;

    public LowStockAlertScheduler(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString = "${strongwine.inventory.low-stock-sweep-ms:300000}", initialDelay = 60000)
    public void sweepLowStockAlerts() {
        try {
            int published = inventoryService.sweepAndPublishLowStockAlerts();
            if (published > 0) {
                log.info("Low stock alert sweep published {} alert(s)", published);
            }
        } catch (Exception ex) {
            log.warn("Low stock alert sweep skipped: {}", ex.getMessage());
        }
    }
}
