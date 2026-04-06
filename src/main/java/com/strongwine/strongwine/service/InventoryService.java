package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.exception.InsufficientStockException;
import com.strongwine.strongwine.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private StockLogRepository stockLogRepository;

    @Autowired
    private WineRepository wineRepository;

    @Autowired
    private UserRepository userRepository;

    public Warehouse getDefaultWarehouse() {
        return warehouseRepository.findByName("Main Warehouse")
                .orElseGet(() -> {
                    Warehouse warehouse = new Warehouse();
                    warehouse.setName("Main Warehouse");
                    warehouse.setLocation("HCM City");
                    warehouse.setActive(true);
                    return warehouseRepository.save(warehouse);
                });
    }

    public Inventory getOrCreateInventory(Long wineId) {
        Warehouse warehouse = getDefaultWarehouse();
        return getOrCreateInventory(wineId, warehouse.getId());
    }

    public Inventory getOrCreateInventory(Long wineId, Long warehouseId) {
        Wine wine = getActiveWine(wineId);
        Warehouse warehouse = getActiveWarehouse(warehouseId);

        return inventoryRepository.findByWineIdAndWarehouseId(wineId, warehouseId)
                .orElseGet(() -> createEmptyInventory(wine, warehouse));
    }

    public void ensureInventoryForAllWines() {
        List<Warehouse> activeWarehouses = warehouseRepository.findByActiveTrue();
        if (activeWarehouses.isEmpty()) {
            return;
        }

        List<Wine> wines = wineRepository.findByDeletedFalse();
        for (Warehouse warehouse : activeWarehouses) {
            for (Wine wine : wines) {
                getOrCreateInventory(wine.getId(), warehouse.getId());
            }
        }
    }

    public Map<Long, Integer> getAvailableStockByWineIds(List<Long> wineIds) {
        Map<Long, Integer> stockMap = new HashMap<>();
        for (Long wineId : wineIds) {
            Inventory inventory = getOrCreateInventory(wineId);
            stockMap.put(wineId, inventory.getAvailableQuantity());
        }
        return stockMap;
    }

    public List<Inventory> getInventoryOverview() {
        ensureInventoryForAllWines();
        return inventoryRepository.findAll();
    }
    
    public org.springframework.data.domain.Page<Inventory> getInventoryOverviewPage(org.springframework.data.domain.Pageable pageable) {
        ensureInventoryForAllWines();
        return inventoryRepository.findAll(pageable);
    }
    
    public long countLowStockInventories() {
        return getInventoryOverview().stream()
                .filter(inv -> inv.getAvailableQuantity() <= inv.getReorderLevel())
                .count();
    }

    public long countInventories() {
        return getInventoryOverview().size();
    }

    public List<Inventory> getLowStockInventories() {
        return getInventoryOverview().stream()
                .filter(inv -> inv.getAvailableQuantity() <= inv.getReorderLevel())
                .collect(Collectors.toList());
    }

    public List<InventoryTransaction> getRecentTransactions() {
        return inventoryTransactionRepository.findTop100ByOrderByCreatedAtDesc();
    }
    
    public org.springframework.data.domain.Page<InventoryTransaction> getTransactionsPage(org.springframework.data.domain.Pageable pageable) {
        return inventoryTransactionRepository.findAll(pageable);
    }

    public Inventory importStock(Long wineId, Long warehouseId, Integer quantity, String createdBy, String note) {
        validateQuantity(quantity);

        Wine wine = getActiveWine(wineId);
        Warehouse warehouse = getActiveWarehouse(warehouseId);

        Inventory inventory = inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(wineId, warehouseId)
                .orElseGet(() -> createEmptyInventory(wine, warehouse));

        inventory.setCurrentQuantity(inventory.getCurrentQuantity() + quantity);
        inventory.setUpdatedAt(LocalDateTime.now());

        Inventory saved = saveInventory(inventory);
        Long userId = resolveUserId(createdBy);
        recordTransaction(saved, quantity, InventoryOperationType.IMPORT, "MANUAL_IMPORT", null, userId, createdBy, note);
        createStockLog(saved, "Stock import +" + quantity);
        return saved;
    }

    public Inventory exportStock(Long wineId, Long warehouseId, Integer quantity, String createdBy, String note) {
        validateQuantity(quantity);

        getActiveWine(wineId);
        getActiveWarehouse(warehouseId);

        Inventory inventory = inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(wineId, warehouseId)
                .orElseThrow(() -> new InsufficientStockException("No inventory for selected wine and warehouse"));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock. Available=" + inventory.getAvailableQuantity() + ", requested=" + quantity);
        }

        int newCurrentQuantity = inventory.getCurrentQuantity() - quantity;
        if (newCurrentQuantity < 0) {
            throw new InsufficientStockException("Inventory cannot be negative");
        }

        inventory.setCurrentQuantity(newCurrentQuantity);
        inventory.setUpdatedAt(LocalDateTime.now());

        Inventory saved = saveInventory(inventory);
        Long userId = resolveUserId(createdBy);
        recordTransaction(saved, quantity, InventoryOperationType.EXPORT, "MANUAL_EXPORT", null, userId, createdBy, note);
        createStockLog(saved, "Stock export -" + quantity);
        return saved;
    }

    public Inventory adjustStock(Long wineId, Long warehouseId, Integer targetQuantity, String createdBy, String note) {
        if (targetQuantity == null || targetQuantity < 0) {
            throw new IllegalArgumentException("Target quantity must be greater than or equal to 0");
        }

        Wine wine = getActiveWine(wineId);
        Warehouse warehouse = getActiveWarehouse(warehouseId);

        Inventory inventory = inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(wineId, warehouseId)
                .orElseGet(() -> createEmptyInventory(wine, warehouse));

        if (targetQuantity < inventory.getReservedQuantity()) {
            throw new IllegalArgumentException("Target quantity cannot be lower than reserved quantity");
        }

        int before = inventory.getCurrentQuantity();
        int delta = targetQuantity - before;
        inventory.setCurrentQuantity(targetQuantity);
        inventory.setUpdatedAt(LocalDateTime.now());

        Inventory saved = saveInventory(inventory);
        Long userId = resolveUserId(createdBy);
        String resolvedNote = (note == null || note.isBlank())
                ? ("Adjusted stock from " + before + " to " + targetQuantity)
                : note;
        recordTransaction(saved, delta, InventoryOperationType.ADJUSTMENT, "MANUAL_ADJUST", null, userId, createdBy, resolvedNote);
        createStockLog(saved, "Stock adjusted from " + before + " to " + targetQuantity);
        return saved;
    }

    public Inventory updateReorderLevel(Long inventoryId, Integer reorderLevel) {
        if (reorderLevel == null || reorderLevel < 0) {
            throw new IllegalArgumentException("Reorder level must be greater than or equal to 0");
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found with id: " + inventoryId));
        inventory.setReorderLevel(reorderLevel);
        inventory.setUpdatedAt(LocalDateTime.now());
        return saveInventory(inventory);
    }

    public Inventory importStock(Long wineId, Integer quantity, Long userId, String note) {
        Warehouse warehouse = getDefaultWarehouse();
        String createdBy = resolveUsername(userId);
        return importStock(wineId, warehouse.getId(), quantity, createdBy, note);
    }

    public Inventory exportStock(Long wineId, Integer quantity, Long userId, String note) {
        Warehouse warehouse = getDefaultWarehouse();
        String createdBy = resolveUsername(userId);
        return exportStock(wineId, warehouse.getId(), quantity, createdBy, note);
    }

    public Inventory getPessimisticallyLockedInventory(Long wineId) {
        Warehouse warehouse = getDefaultWarehouse();
        return getPessimisticallyLockedInventory(wineId, warehouse.getId());
    }

    public Inventory getPessimisticallyLockedInventory(Long wineId, Long warehouseId) {
        return inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(wineId, warehouseId)
                .orElseGet(() -> getOrCreateInventory(wineId, warehouseId));
    }

    public void reserveForOrder(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId());
            if (inventory.getAvailableQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for wine: " + item.getWine().getName());
            }
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.ORDER, "ORDER", order.getId(), order.getUser().getId(), order.getUser().getUsername(), "Reserved stock for order");
            createStockLog(saved, "Reserved " + item.getQuantity() + " for order #" + order.getId());
        }
    }

    public void confirmOrderPayment(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId());
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            inventory.setCurrentQuantity(Math.max(0, inventory.getCurrentQuantity() - item.getQuantity()));
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.EXPORT, "PAYMENT", order.getId(), order.getUser().getId(), order.getUser().getUsername(), "Confirmed stock deduction for paid order");
            createStockLog(saved, "Deducted " + item.getQuantity() + " for paid order #" + order.getId());
        }
    }

    public void releaseOrderReservation(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId());
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.CANCEL, "ORDER_CANCEL", order.getId(), order.getUser().getId(), order.getUser().getUsername(), "Released reserved stock");
            createStockLog(saved, "Released reservation " + item.getQuantity() + " for order #" + order.getId());
        }
    }

    private Inventory createEmptyInventory(Wine wine, Warehouse warehouse) {
        Inventory inventory = new Inventory();
        inventory.setWine(wine);
        inventory.setWarehouse(warehouse);
        inventory.setCurrentQuantity(0);
        inventory.setReservedQuantity(0);
        inventory.setReorderLevel(10);
        inventory.setUpdatedAt(LocalDateTime.now());
        if (inventory.getVersion() == null) {
            inventory.setVersion(0L);
        }
        return saveInventory(inventory);
    }

    private Inventory saveInventory(Inventory inventory) {
        if (inventory.getVersion() == null) {
            inventory.setVersion(0L);
        }
        return inventoryRepository.save(inventory);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }

    private Wine getActiveWine(Long wineId) {
        Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new IllegalArgumentException("Wine not found with id: " + wineId));
        if (wine.isDeleted()) {
            throw new IllegalArgumentException("Wine is deleted");
        }
        return wine;
    }

    private Warehouse getActiveWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found with id: " + warehouseId));
        if (Boolean.FALSE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Warehouse is inactive");
        }
        return warehouse;
    }

    private Long resolveUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "system";
        }
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("system");
    }

    private void recordTransaction(Inventory inventory,
                                   Integer quantity,
                                   InventoryOperationType operationType,
                                   String referenceType,
                                   Long referenceId,
                                   Long userId,
                                   String createdBy,
                                   String note) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setInventory(inventory);
        transaction.setWine(inventory.getWine());
        transaction.setWarehouse(inventory.getWarehouse());
        transaction.setQuantity(quantity);
        transaction.setOperationType(operationType);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setUserId(userId);
        transaction.setCreatedBy((createdBy == null || createdBy.isBlank()) ? "system" : createdBy);
        transaction.setNote(note);
        inventoryTransactionRepository.save(transaction);
    }

    private void createStockLog(Inventory inventory, String message) {
        StockLog log = new StockLog();
        log.setInventory(inventory);
        log.setAvailableQuantity(inventory.getAvailableQuantity());
        log.setMessage(message);
        stockLogRepository.save(log);
    }
}
