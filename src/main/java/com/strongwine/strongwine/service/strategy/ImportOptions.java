package com.strongwine.strongwine.service.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOptions {
    @Builder.Default
    private boolean dryRun = false;
    
    @Builder.Default
    private InventoryMode inventoryMode = InventoryMode.ADD;

    @Builder.Default
    private Long defaultWarehouseId = 1L;

    public enum InventoryMode {
        ADD,
        REPLACE
    }
}
