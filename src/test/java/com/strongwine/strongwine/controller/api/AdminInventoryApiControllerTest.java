package com.strongwine.strongwine.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.dto.StockOperationRequest;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInventoryApiControllerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private UserRepository userRepository;

    private AdminInventoryApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminInventoryApiController();
        ReflectionTestUtils.setField(controller, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
    }

    @Test
    void inventoryOverviewJsonMustNotExposeNestedUserCredentials() throws Exception {
        when(inventoryService.getInventoryOverview()).thenReturn(List.of(inventoryWithRelatedUser()));

        assertSafeInventoryResponse(controller.getInventoryOverview().getBody());
    }

    @Test
    void lowStockJsonMustNotExposeNestedUserCredentials() throws Exception {
        when(inventoryService.getLowStockInventories()).thenReturn(List.of(inventoryWithRelatedUser()));

        assertSafeInventoryResponse(controller.getLowStock().getBody());
    }

    @Test
    void stockOperationResponsesMustNotExposeNestedUserCredentials() throws Exception {
        Inventory inventory = inventoryWithRelatedUser();
        when(inventoryService.importStock(11L, 3L, 4, "system", "qa import")).thenReturn(inventory);
        when(inventoryService.exportStock(11L, 3L, 2, "system", "qa export")).thenReturn(inventory);

        StockOperationRequest request = new StockOperationRequest();
        request.setWineId(11L);
        request.setWarehouseId(3L);
        request.setQuantity(4);
        request.setNote("qa import");
        assertSafeInventoryResponse(controller.importStock(11L, request, null).getBody());

        request.setQuantity(2);
        request.setNote("qa export");
        assertSafeInventoryResponse(controller.exportStock(11L, request, null).getBody());
    }

    private void assertSafeInventoryResponse(Object response) throws Exception {
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertFalse(json.contains("HASH_SHOULD_NOT_BE_SERIALIZED"));
        assertFalse(json.contains("\"password\""));
        assertFalse(json.contains("\"orderItems\""));
        assertTrue(json.contains("\"wineName\":\"QA inventory wine\""));
    }

    private Inventory inventoryWithRelatedUser() {
        User user = new User();
        user.setId(7L);
        user.setUsername("inventory-related-user");
        user.setPassword("HASH_SHOULD_NOT_BE_SERIALIZED");
        user.setOrders(new ArrayList<>());

        Order order = new Order();
        order.setOrderDate(null);
        order.setUpdatedAt(null);
        order.setUser(user);
        order.setOrderItems(new ArrayList<>());
        order.setPayments(new ArrayList<>());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);

        Wine wine = new Wine();
        wine.setId(11L);
        wine.setName("QA inventory wine");
        wine.setOrderItems(List.of(orderItem));

        Warehouse warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setName("QA warehouse");
        warehouse.setCreatedAt(null);

        Inventory inventory = new Inventory();
        inventory.setId(5L);
        inventory.setWine(wine);
        inventory.setWarehouse(warehouse);
        inventory.setCurrentQuantity(8);
        inventory.setReservedQuantity(2);
        inventory.setReorderLevel(3);
        inventory.setUpdatedAt(null);
        return inventory;
    }
}
