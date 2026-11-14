package com.strongwine.strongwine.service;

import com.strongwine.strongwine.config.LowStockAlertScheduler;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.event.LowStockAlertEvent;
import com.strongwine.strongwine.exception.InsufficientStockException;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.InventoryTransactionRepository;
import com.strongwine.strongwine.repository.StockLogRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceMultiWarehouseTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private StockLogRepository stockLogRepository;

    @Mock
    private WineRepository wineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    private Warehouse warehouse(Long id, String name, double lat, double lng, boolean isDefault, boolean active) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setName(name);
        w.setLatitude(lat);
        w.setLongitude(lng);
        w.setIsDefault(isDefault);
        w.setActive(active);
        return w;
    }

    private Wine wine(Long id, String name) {
        Wine w = new Wine();
        w.setId(id);
        w.setName(name);
        w.setType("Red");
        w.setPrice(new BigDecimal("500000"));
        return w;
    }

    private Inventory inventory(Long id, Wine wine, Warehouse warehouse, int current, int reserved, int reorder) {
        Inventory inv = new Inventory();
        inv.setId(id);
        inv.setWine(wine);
        inv.setWarehouse(warehouse);
        inv.setCurrentQuantity(current);
        inv.setReservedQuantity(reserved);
        inv.setReorderLevel(reorder);
        inv.setVersion(0L);
        return inv;
    }

    private Order orderWithCoordinates(Long id, Double lat, Double lng, OrderItem... items) {
        User user = new User();
        user.setId(100L);
        user.setUsername("buyer1");
        user.setRole("USER");

        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setShippingLatitude(lat);
        order.setShippingLongitude(lng);
        order.setOrderItems(List.of(items));
        return order;
    }

    private OrderItem item(Order order, Wine wine, int qty) {
        OrderItem oi = new OrderItem();
        oi.setOrder(order);
        oi.setWine(wine);
        oi.setQuantity(qty);
        oi.setPrice(wine.getPrice());
        return oi;
    }

    @Test
    void getAvailableStockByWineIdsSumsAcrossActiveWarehouses() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Warehouse hanoi = warehouse(2L, "Kho Ha Noi", 21.0285, 105.8542, false, true);

        Inventory inv1 = inventory(11L, wine1, hcm, 15, 3, 5); // 12 available
        Inventory inv2 = inventory(12L, wine1, hanoi, 10, 2, 5); // 8 available

        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L, 20L))).thenReturn(List.of(inv1, inv2));

        Map<Long, Integer> result = inventoryService.getAvailableStockByWineIds(List.of(10L, 20L));

        assertEquals(20, result.get(10L));
        assertEquals(0, result.get(20L));
    }

    @Test
    void reserveForOrderSelectsClosestWarehouseByHaversineAndPublishesLowStockAlert() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Warehouse hanoi = warehouse(2L, "Kho Ha Noi", 21.0285, 105.8542, false, true);

        Inventory invHcm = inventory(11L, wine1, hcm, 50, 0, 5);
        Inventory invHanoi = inventory(12L, wine1, hanoi, 15, 0, 10);

        Order order = orderWithCoordinates(501L, 21.0278, 105.8342);
        order.setOrderItems(List.of(item(order, wine1, 8)));

        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(hcm, hanoi));
        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L))).thenReturn(List.of(invHcm, invHanoi));
        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(10L, 2L))
                .thenReturn(Optional.of(invHanoi));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveForOrder(order);

        assertSame(hanoi, order.getAllocatedWarehouse());
        assertEquals(8, invHanoi.getReservedQuantity());
        assertEquals(7, invHanoi.getAvailableQuantity());
        assertEquals(0, invHcm.getReservedQuantity());

        ArgumentCaptor<LowStockAlertEvent> captor = ArgumentCaptor.forClass(LowStockAlertEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LowStockAlertEvent event = captor.getValue();
        assertEquals(10L, event.getWineId());
        assertEquals(2L, event.getWarehouseId());
        assertEquals(7, event.getAvailableQuantity());
        assertEquals(10, event.getReorderLevel());
    }

    @Test
    void reserveForOrderSkipsNearestWarehouseWithInsufficientStock() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Warehouse danang = warehouse(2L, "Kho Da Nang", 16.0544, 108.2022, false, true);
        Warehouse hanoi = warehouse(3L, "Kho Ha Noi", 21.0285, 105.8542, false, true);

        Inventory invHanoi = inventory(13L, wine1, hanoi, 2, 0, 5); // too low
        Inventory invDanang = inventory(12L, wine1, danang, 12, 0, 3);
        Inventory invHcm = inventory(11L, wine1, hcm, 50, 0, 5);

        Order order = orderWithCoordinates(502L, 21.0278, 105.8342);
        order.setOrderItems(List.of(item(order, wine1, 8)));

        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(hcm, danang, hanoi));
        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L))).thenReturn(List.of(invHanoi, invDanang, invHcm));
        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(10L, 2L))
                .thenReturn(Optional.of(invDanang));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveForOrder(order);

        assertSame(danang, order.getAllocatedWarehouse());
        assertEquals(8, invDanang.getReservedQuantity());
        assertEquals(0, invHanoi.getReservedQuantity());
    }

    @Test
    void reserveForOrderRequiresOneWarehouseToFulfillAllOrderItems() {
        Wine w1 = wine(10L, "Margaux");
        Wine w2 = wine(20L, "Ruou Mo Yen Tu");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Warehouse hanoi = warehouse(2L, "Kho Ha Noi", 21.0285, 105.8542, false, true);

        Inventory hanoiW1 = inventory(11L, w1, hanoi, 20, 0, 5);
        Inventory hanoiW2 = inventory(12L, w2, hanoi, 1, 0, 5); // insufficient for qty 5
        Inventory hcmW1 = inventory(21L, w1, hcm, 20, 0, 5);
        Inventory hcmW2 = inventory(22L, w2, hcm, 20, 0, 5);

        Order order = orderWithCoordinates(503L, 21.0278, 105.8342);
        order.setOrderItems(List.of(item(order, w1, 5), item(order, w2, 5)));

        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(hcm, hanoi));
        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L, 20L)))
                .thenReturn(List.of(hanoiW1, hanoiW2, hcmW1, hcmW2));
        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(10L, 1L))
                .thenReturn(Optional.of(hcmW1));
        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(20L, 1L))
                .thenReturn(Optional.of(hcmW2));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveForOrder(order);

        assertSame(hcm, order.getAllocatedWarehouse());
        assertEquals(5, hcmW1.getReservedQuantity());
        assertEquals(5, hcmW2.getReservedQuantity());
    }

    @Test
    void reserveForOrderFallsBackToDefaultOrFirstStockedWarehouseWhenCoordinatesNull() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Warehouse hanoi = warehouse(2L, "Kho Ha Noi", 21.0285, 105.8542, false, true);

        Inventory invHcm = inventory(11L, wine1, hcm, 2, 0, 1); // insufficient on default
        Inventory invHanoi = inventory(12L, wine1, hanoi, 20, 0, 5); // stocked fallback

        Order order = orderWithCoordinates(504L, null, null);
        order.setOrderItems(List.of(item(order, wine1, 5)));

        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(hcm, hanoi));
        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L))).thenReturn(List.of(invHcm, invHanoi));
        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(10L, 2L))
                .thenReturn(Optional.of(invHanoi));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveForOrder(order);

        assertSame(hanoi, order.getAllocatedWarehouse());
        assertEquals(5, invHanoi.getReservedQuantity());
    }

    @Test
    void reserveForOrderThrowsInsufficientStockWhenNoWarehouseHasEnough() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);
        Inventory invHcm = inventory(11L, wine1, hcm, 2, 0, 1);

        Order order = orderWithCoordinates(505L, 10.7769, 106.7009);
        order.setOrderItems(List.of(item(order, wine1, 5)));

        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(hcm));
        when(inventoryRepository.findAllActiveByWineIdIn(List.of(10L))).thenReturn(List.of(invHcm));

        assertThrows(InsufficientStockException.class, () -> inventoryService.reserveForOrder(order));
        assertNull(order.getAllocatedWarehouse());
    }

    @Test
    void confirmReleaseAndRestockActOnAllocatedWarehouse() {
        Wine wine1 = wine(10L, "Margaux");
        Warehouse hanoi = warehouse(2L, "Kho Ha Noi", 21.0285, 105.8542, false, true);
        Inventory invHanoi = inventory(12L, wine1, hanoi, 15, 5, 5);

        Order order = orderWithCoordinates(506L, 21.0278, 105.8342);
        order.setAllocatedWarehouse(hanoi);
        order.setOrderItems(List.of(item(order, wine1, 5)));

        when(inventoryRepository.findByWineIdAndWarehouseIdWithPessimisticLock(10L, 2L))
                .thenReturn(Optional.of(invHanoi));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.confirmOrderPayment(order);
        assertEquals(10, invHanoi.getCurrentQuantity());
        assertEquals(0, invHanoi.getReservedQuantity());

        invHanoi.setReservedQuantity(3);
        inventoryService.releaseOrderReservation(order);
        assertEquals(0, invHanoi.getReservedQuantity());
        assertEquals(10, invHanoi.getCurrentQuantity());

        inventoryService.restockForOrderReturn(order, "admin", "Approved customer return");
        assertEquals(15, invHanoi.getCurrentQuantity());
        verify(inventoryTransactionRepository, atLeast(3)).save(any());
        verify(stockLogRepository, atLeast(3)).save(any());
    }

    @Test
    void sweepAndSchedulerPublishLowStockAlertsSafely() {
        Wine activeWine = wine(10L, "Margaux");
        Wine deletedWine = wine(11L, "Archived Wine");
        deletedWine.setDeleted(true);
        Warehouse hcm = warehouse(1L, "Main Warehouse", 10.7769, 106.7009, true, true);

        Inventory lowInv = inventory(11L, activeWine, hcm, 4, 1, 5); // 3 <= 5
        Inventory okInv = inventory(12L, activeWine, hcm, 20, 0, 5); // 20 > 5
        Inventory deletedInv = inventory(13L, deletedWine, hcm, 1, 0, 5);

        when(inventoryRepository.findAllForActiveWines()).thenReturn(List.of(lowInv, okInv, deletedInv));

        int published = inventoryService.sweepAndPublishLowStockAlerts();
        assertEquals(1, published);
        verify(eventPublisher, times(1)).publishEvent(any(LowStockAlertEvent.class));

        InventoryService mockInvService = mock(InventoryService.class);
        LowStockAlertScheduler scheduler = new LowStockAlertScheduler(mockInvService);
        scheduler.sweepLowStockAlerts();
        verify(mockInvService, times(1)).sweepAndPublishLowStockAlerts();

        doThrow(new RuntimeException("DB momentarily unavailable")).when(mockInvService).sweepAndPublishLowStockAlerts();
        assertDoesNotThrow(scheduler::sweepLowStockAlerts);
    }
}
