package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.WineService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for F-SW-07: admin-dashboard.html reads paidRevenue, ordersPending and shippingActive,
 * but the controller never set them, so the KPI cards always showed 0.
 */
class AdminDashboardKpiTest {

    @Test
    void dashboardExposesTheKpiAttributesTheTemplateReads() {
        WineService wineService = mock(WineService.class);
        UserService userService = mock(UserService.class);
        OrderService orderService = mock(OrderService.class);
        ReviewService reviewService = mock(ReviewService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PaymentService paymentService = mock(PaymentService.class);
        ShipperService shipperService = mock(ShipperService.class);
        ShipmentService shipmentService = mock(ShipmentService.class);

        when(orderService.getTotalRevenue()).thenReturn(116_000_000d);
        when(orderService.countOrdersByStatus(OrderStatus.PENDING)).thenReturn(4L);
        when(orderService.getAllOrdersPage(any())).thenReturn(new PageImpl<>(List.of()));
        when(shipmentService.getShipmentStatusStats()).thenReturn(Map.of(
                ShipmentStatus.ASSIGNED, 2L,
                ShipmentStatus.PICKED_UP, 1L,
                ShipmentStatus.DELIVERING, 1L,
                ShipmentStatus.COMPLETED, 6L,
                ShipmentStatus.PENDING_ASSIGNMENT, 3L));

        AdminController controller = new AdminController(wineService, userService, orderService, reviewService,
                inventoryService, paymentService, shipperService, shipmentService);
        Model model = new ConcurrentModel();

        controller.dashboard(7, null, null, model);

        assertEquals(116_000_000d, model.getAttribute("paidRevenue"));
        assertEquals(4L, model.getAttribute("ordersPending"));
        assertEquals(4L, model.getAttribute("shippingActive"));
    }

    @Test
    void activeShipmentsCountOnlyShipmentsStillOnTheRoute() {
        assertEquals(0L, AdminController.countActiveShipments(Map.of()));
        assertEquals(3L, AdminController.countActiveShipments(Map.of(
                ShipmentStatus.ASSIGNED, 1L,
                ShipmentStatus.PICKED_UP, 1L,
                ShipmentStatus.DELIVERING, 1L,
                ShipmentStatus.COMPLETED, 9L,
                ShipmentStatus.PENDING_ASSIGNMENT, 5L)));
    }
}
