package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.Inventory;

import java.time.LocalDateTime;

/**
 * Safe inventory API representation that excludes entity relationships.
 */
public record AdminInventoryDto(
        Long id,
        Long wineId,
        String wineName,
        Long warehouseId,
        String warehouseName,
        Integer currentQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Integer reorderLevel,
        Long version,
        LocalDateTime updatedAt) {

    public static AdminInventoryDto from(Inventory inventory) {
        return new AdminInventoryDto(
                inventory.getId(),
                inventory.getWine() == null ? null : inventory.getWine().getId(),
                inventory.getWine() == null ? null : inventory.getWine().getName(),
                inventory.getWarehouse() == null ? null : inventory.getWarehouse().getId(),
                inventory.getWarehouse() == null ? null : inventory.getWarehouse().getName(),
                inventory.getCurrentQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getReorderLevel(),
                inventory.getVersion(),
                inventory.getUpdatedAt());
    }
}
