package com.strongwine.strongwine.service.strategy;

public class ImportOptions {
    private boolean dryRun = false;
    private InventoryMode inventoryMode = InventoryMode.ADD;
    private Long defaultWarehouseId = 1L;

    public enum InventoryMode {
        ADD,
        REPLACE
    }

    public ImportOptions() {
    }

    public ImportOptions(boolean dryRun, InventoryMode inventoryMode, Long defaultWarehouseId) {
        this.dryRun = dryRun;
        this.inventoryMode = inventoryMode != null ? inventoryMode : InventoryMode.ADD;
        this.defaultWarehouseId = defaultWarehouseId != null ? defaultWarehouseId : 1L;
    }

    public static ImportOptionsBuilder builder() {
        return new ImportOptionsBuilder();
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public void setInventoryMode(InventoryMode inventoryMode) {
        this.inventoryMode = inventoryMode;
    }

    public Long getDefaultWarehouseId() {
        return defaultWarehouseId;
    }

    public void setDefaultWarehouseId(Long defaultWarehouseId) {
        this.defaultWarehouseId = defaultWarehouseId;
    }

    public static class ImportOptionsBuilder {
        private boolean dryRun = false;
        private InventoryMode inventoryMode = InventoryMode.ADD;
        private Long defaultWarehouseId = 1L;

        public ImportOptionsBuilder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public ImportOptionsBuilder inventoryMode(InventoryMode inventoryMode) {
            this.inventoryMode = inventoryMode;
            return this;
        }

        public ImportOptionsBuilder defaultWarehouseId(Long defaultWarehouseId) {
            this.defaultWarehouseId = defaultWarehouseId;
            return this;
        }

        public ImportOptions build() {
            return new ImportOptions(dryRun, inventoryMode, defaultWarehouseId);
        }
    }
}
