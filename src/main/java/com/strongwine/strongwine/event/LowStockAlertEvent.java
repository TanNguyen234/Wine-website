package com.strongwine.strongwine.event;

import org.springframework.context.ApplicationEvent;

public class LowStockAlertEvent extends ApplicationEvent {

    private final Long inventoryId;
    private final Long wineId;
    private final String wineName;
    private final Long warehouseId;
    private final String warehouseName;
    private final Integer availableQuantity;
    private final Integer reorderLevel;

    public LowStockAlertEvent(
        Object source,
        Long inventoryId,
        Long wineId,
        String wineName,
        Long warehouseId,
        String warehouseName,
        Integer availableQuantity,
        Integer reorderLevel
    ) {
        super(source != null ? source : LowStockAlertEvent.class);
        this.inventoryId = inventoryId;
        this.wineId = wineId;
        this.wineName = wineName;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.availableQuantity = availableQuantity;
        this.reorderLevel = reorderLevel;
    }

    public LowStockAlertEvent(
        Long inventoryId,
        Long wineId,
        String wineName,
        Long warehouseId,
        String warehouseName,
        Integer availableQuantity,
        Integer reorderLevel
    ) {
        this(
            inventoryId != null ? inventoryId : LowStockAlertEvent.class,
            inventoryId,
            wineId,
            wineName,
            warehouseId,
            warehouseName,
            availableQuantity,
            reorderLevel
        );
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public Long getWineId() {
        return wineId;
    }

    public String getWineName() {
        return wineName;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }
}
