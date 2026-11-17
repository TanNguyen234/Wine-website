package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.event.LowStockAlertEvent;
import com.strongwine.strongwine.exception.InsufficientStockException;
import com.strongwine.strongwine.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryService {

    private static final double EARTH_RADIUS_KM = 6371.0d;

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

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public Warehouse getDefaultWarehouse() {
        return warehouseRepository.findFirstByIsDefaultTrueAndActiveTrue()
                .or(() -> warehouseRepository.findByName("Main Warehouse"))
                .orElseGet(() -> {
                    Warehouse warehouse = new Warehouse();
                    warehouse.setName("Main Warehouse");
                    warehouse.setLocation("HCM City");
                    warehouse.setLatitude(10.7769);
                    warehouse.setLongitude(106.7009);
                    warehouse.setIsDefault(true);
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
        Warehouse warehouse = getDefaultWarehouse();
        wineRepository.findByDeletedFalse().forEach(wine -> getOrCreateInventory(wine.getId(), warehouse.getId()));
    }

    public Map<Long, Integer> getAvailableStockByWineIds(List<Long> wineIds) {
        if (wineIds == null || wineIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> stockMap = new HashMap<>();
        for (Long wineId : wineIds) {
            if (wineId != null) {
                stockMap.put(wineId, 0);
            }
        }
        List<Inventory> activeInventories = inventoryRepository.findAllActiveByWineIdIn(wineIds);
        if (activeInventories != null) {
            for (Inventory inv : activeInventories) {
                if (inv != null && inv.getWine() != null && inv.getWine().getId() != null) {
                    stockMap.merge(inv.getWine().getId(), inv.getAvailableQuantity(), Integer::sum);
                }
            }
        }
        return stockMap;
    }

    public List<Inventory> getInventoryOverview() {
        ensureInventoryForAllWines();
        return inventoryRepository.findAllForActiveWines();
    }
    
    public org.springframework.data.domain.Page<Inventory> getInventoryOverviewPage(org.springframework.data.domain.Pageable pageable) {
        ensureInventoryForAllWines();
        return inventoryRepository.findAllForActiveWines(pageable);
    }
    
    public long countLowStockInventories() {
        return getInventoryOverview().stream()
                .filter(inv -> inv.getAvailableQuantity() <= inv.getReorderLevel())
                .count();
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
        publishLowStockAlertIfNeeded(saved);
        return saved;
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
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }
        Warehouse chosenWarehouse = selectBestWarehouseForOrder(order);
        order.setAllocatedWarehouse(chosenWarehouse);

        Long actorId = resolveOrderUserId(order);
        String actorUsername = resolveOrderUsername(order);

        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId(), chosenWarehouse.getId());
            if (inventory.getAvailableQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for wine: " + item.getWine().getName());
            }
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.ORDER, "ORDER", order.getId(), actorId, actorUsername,
                    "Reserved stock from warehouse " + chosenWarehouse.getName());
            createStockLog(saved, "Reserved " + item.getQuantity() + " from " + chosenWarehouse.getName() + " for order #" + order.getId());
            publishLowStockAlertIfNeeded(saved);
        }
    }

    public void confirmOrderPayment(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }
        Warehouse warehouse = resolveOrderWarehouse(order);
        Long actorId = resolveOrderUserId(order);
        String actorUsername = resolveOrderUsername(order);

        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId(), warehouse.getId());
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            inventory.setCurrentQuantity(Math.max(0, inventory.getCurrentQuantity() - item.getQuantity()));
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.EXPORT, "PAYMENT", order.getId(), actorId, actorUsername,
                    "Confirmed stock deduction for paid order");
            createStockLog(saved, "Deducted " + item.getQuantity() + " for paid order #" + order.getId());
            publishLowStockAlertIfNeeded(saved);
        }
    }

    public void releaseOrderReservation(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }
        Warehouse warehouse = resolveOrderWarehouse(order);
        Long actorId = resolveOrderUserId(order);
        String actorUsername = resolveOrderUsername(order);

        for (OrderItem item : order.getOrderItems()) {
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId(), warehouse.getId());
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.CANCEL, "ORDER_CANCEL", order.getId(), actorId, actorUsername,
                    "Released reserved stock");
            createStockLog(saved, "Released reservation " + item.getQuantity() + " for order #" + order.getId());
        }
    }

    public void restockForOrderReturn(Order order, String actorUsername, String note) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }
        Warehouse warehouse = resolveOrderWarehouse(order);
        String actor = (actorUsername == null || actorUsername.isBlank()) ? resolveOrderUsername(order) : actorUsername;
        Long actorId = resolveUserId(actor);
        String effectiveNote = (note == null || note.isBlank()) ? "Restocked returned order #" + order.getId() : note;

        for (OrderItem item : order.getOrderItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getWine() == null) {
                continue;
            }
            Inventory inventory = getPessimisticallyLockedInventory(item.getWine().getId(), warehouse.getId());
            inventory.setCurrentQuantity((inventory.getCurrentQuantity() != null ? inventory.getCurrentQuantity() : 0) + item.getQuantity());
            inventory.setUpdatedAt(LocalDateTime.now());
            Inventory saved = saveInventory(inventory);
            recordTransaction(saved, item.getQuantity(), InventoryOperationType.IMPORT, "ORDER_RETURN", order.getId(), actorId, actor, effectiveNote);
            createStockLog(saved, "Restocked returned +" + item.getQuantity() + " for order #" + order.getId());
        }
    }

    public int sweepAndPublishLowStockAlerts() {
        List<Inventory> inventories = inventoryRepository.findAllForActiveWines();
        if (inventories == null || inventories.isEmpty()) {
            return 0;
        }
        int publishedCount = 0;
        for (Inventory inv : inventories) {
            if (publishLowStockAlertIfNeeded(inv)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    private Warehouse selectBestWarehouseForOrder(Order order) {
        Map<Long, Integer> requiredByWine = new LinkedHashMap<>();
        Map<Long, String> wineNames = new HashMap<>();
        for (OrderItem item : order.getOrderItems()) {
            if (item.getWine() == null || item.getWine().getId() == null) {
                continue;
            }
            Long wineId = item.getWine().getId();
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            requiredByWine.merge(wineId, qty, Integer::sum);
            wineNames.putIfAbsent(wineId, item.getWine().getName());
        }
        if (requiredByWine.isEmpty()) {
            return getDefaultWarehouse();
        }

        List<Warehouse> activeWarehouses = warehouseRepository.findByActiveTrue();
        if (activeWarehouses == null || activeWarehouses.isEmpty()) {
            return getDefaultWarehouse();
        }

        List<Inventory> activeInventories = inventoryRepository.findAllActiveByWineIdIn(new ArrayList<>(requiredByWine.keySet()));
        Map<Long, Map<Long, Integer>> availableByWarehouse = new HashMap<>();
        if (activeInventories != null) {
            for (Inventory inv : activeInventories) {
                if (inv.getWarehouse() == null || inv.getWarehouse().getId() == null || inv.getWine() == null) {
                    continue;
                }
                availableByWarehouse
                        .computeIfAbsent(inv.getWarehouse().getId(), ignored -> new HashMap<>())
                        .merge(inv.getWine().getId(), inv.getAvailableQuantity(), Integer::sum);
            }
        }

        List<Warehouse> eligibleWarehouses = activeWarehouses.stream()
                .filter(wh -> wh.getId() != null)
                .filter(wh -> hasSufficientStock(availableByWarehouse.getOrDefault(wh.getId(), Collections.emptyMap()), requiredByWine))
                .toList();

        if (eligibleWarehouses.isEmpty()) {
            String missingNames = requiredByWine.keySet().stream()
                    .map(id -> wineNames.getOrDefault(id, "#" + id))
                    .collect(Collectors.joining(", "));
            throw new InsufficientStockException("Insufficient stock for wine: " + missingNames);
        }

        Double targetLat = order.getShippingLatitude();
        Double targetLng = order.getShippingLongitude();
        if (targetLat != null && targetLng != null) {
            return eligibleWarehouses.stream()
                    .min(Comparator
                            .comparingDouble((Warehouse w) -> distanceScore(w, targetLat, targetLng))
                            .thenComparing((Warehouse w) -> !Boolean.TRUE.equals(w.getIsDefault()))
                            .thenComparing(Warehouse::getId))
                    .orElse(eligibleWarehouses.get(0));
        }

        return eligibleWarehouses.stream()
                .min(Comparator
                        .comparing((Warehouse w) -> !isPreferredDefault(w))
                        .thenComparing(Warehouse::getId))
                .orElse(eligibleWarehouses.get(0));
    }

    private boolean hasSufficientStock(Map<Long, Integer> warehouseStock, Map<Long, Integer> requiredByWine) {
        for (Map.Entry<Long, Integer> entry : requiredByWine.entrySet()) {
            if (warehouseStock.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private double distanceScore(Warehouse warehouse, double targetLat, double targetLng) {
        if (warehouse.getLatitude() == null || warehouse.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        return calculateHaversineKm(warehouse.getLatitude(), warehouse.getLongitude(), targetLat, targetLng);
    }

    private boolean isPreferredDefault(Warehouse warehouse) {
        return Boolean.TRUE.equals(warehouse.getIsDefault()) || "Main Warehouse".equalsIgnoreCase(warehouse.getName());
    }

    private double calculateHaversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private Warehouse resolveOrderWarehouse(Order order) {
        return order.getAllocatedWarehouse() != null ? order.getAllocatedWarehouse() : getDefaultWarehouse();
    }

    private Long resolveOrderUserId(Order order) {
        return order.getUser() != null ? order.getUser().getId() : null;
    }

    private String resolveOrderUsername(Order order) {
        return (order.getUser() != null && order.getUser().getUsername() != null)
                ? order.getUser().getUsername()
                : "system";
    }

    private boolean publishLowStockAlertIfNeeded(Inventory inv) {
        if (inv == null || inv.getWine() == null || inv.getWine().isDeleted()) {
            return false;
        }
        int available = inv.getAvailableQuantity();
        int reorder = inv.getReorderLevel() != null ? inv.getReorderLevel() : 10;
        if (available > reorder) {
            return false;
        }
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new LowStockAlertEvent(
                    this,
                    inv.getId(),
                    inv.getWine().getId(),
                    inv.getWine().getName(),
                    inv.getWarehouse() != null ? inv.getWarehouse().getId() : null,
                    inv.getWarehouse() != null ? inv.getWarehouse().getName() : "Main Warehouse",
                    available,
                    reorder
            ));
        }
        return true;
    }

    private Inventory createEmptyInventory(Wine wine, Warehouse warehouse) {
        Inventory inventory = new Inventory();
        inventory.setWine(wine);
        inventory.setWarehouse(warehouse);
        inventory.setCurrentQuantity(0);
        inventory.setReservedQuantity(0);
        inventory.setReorderLevel(10);
        inventory.setUpdatedAt(LocalDateTime.now());
        return saveInventory(inventory);
    }

    private Inventory saveInventory(Inventory inventory) {
        if (inventory.getId() != null && inventory.getVersion() == null) {
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
        if (wineId == null) {
            throw new IllegalArgumentException("wineId is required");
        }
        Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new IllegalArgumentException("Wine not found with id: " + wineId));
        if (wine.isDeleted()) {
            throw new IllegalArgumentException("Wine is deleted");
        }
        return wine;
    }

    private Warehouse getActiveWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId is required");
        }
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
