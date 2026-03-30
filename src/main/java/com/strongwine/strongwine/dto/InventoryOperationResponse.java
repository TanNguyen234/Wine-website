package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.InventoryOperationType;

public class InventoryOperationResponse {
    private Long wineId;
    private Long warehouseId;
    private Integer currentQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private InventoryOperationType operationType;
    private String message;

    public static InventoryOperationResponse from(Inventory inventory, InventoryOperationType operationType, String message) {
        InventoryOperationResponse response = new InventoryOperationResponse();
        response.setWineId(inventory.getWine().getId());
        response.setWarehouseId(inventory.getWarehouse().getId());
        response.setCurrentQuantity(inventory.getCurrentQuantity());
        response.setReservedQuantity(inventory.getReservedQuantity());
        response.setAvailableQuantity(inventory.getAvailableQuantity());
        response.setOperationType(operationType);
        response.setMessage(message);
        return response;
    }

    public Long getWineId() {
        return wineId;
    }

    public void setWineId(Long wineId) {
        this.wineId = wineId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(Integer currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public InventoryOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(InventoryOperationType operationType) {
        this.operationType = operationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
