package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.WineService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller for admin dashboard
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final WineService wineService;
    private final UserService userService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShipperService shipperService;
    private final ShipmentService shipmentService;

    public AdminController(WineService wineService,
                           UserService userService,
                           OrderService orderService,
                           ReviewService reviewService,
                           InventoryService inventoryService,
                           PaymentService paymentService,
                           ShipperService shipperService,
                           ShipmentService shipmentService) {
        this.wineService = wineService;
        this.userService = userService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shipperService = shipperService;
        this.shipmentService = shipmentService;
    }
    
    /**
     * Admin dashboard
     */
    @GetMapping
    public String dashboard(Model model) {
        // Efficient Statistics Loading
        long totalWines = wineService.countWines();
        long totalUsers = userService.countUsers();
        long totalOrders = orderService.countOrders();
        Double totalRevenue = orderService.getTotalRevenue();
        long lowStockCount = inventoryService.countLowStockInventories();
        long totalInventories = inventoryService.countInventories();
        long pendingPayments = paymentService.countPendingPayments();
        Map<String, Long> shipperStats = shipperService.getShipperOverviewStats();
        Map<?, Long> shipmentStats = shipmentService.getShipmentStatusStats();
        long totalShipments = shipmentStats.values().stream().mapToLong(Long::longValue).sum();
        
        model.addAttribute("totalWines", totalWines);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("pendingPayments", pendingPayments);
        model.addAttribute("shipperStats", shipperStats);
        model.addAttribute("shipmentStats", shipmentStats);
        model.addAttribute("totalShipments", totalShipments);
        
        // Chart Data: Revenue Last 7 Days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> revenueData = orderService.getDailyRevenueLast7Days(sevenDaysAgo);
        model.addAttribute("revenueLabels", revenueData.stream().map(row -> row[0].toString()).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("revenueValues", revenueData.stream().map(row -> row[1]).collect(java.util.stream.Collectors.toList()));

        // Chart Data: Order Status Counts
        List<Object[]> statusData = orderService.getOrderStatusCounts();
        model.addAttribute("statusLabels", statusData.stream().map(row -> row[0].toString()).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("statusValues", statusData.stream().map(row -> row[1]).collect(java.util.stream.Collectors.toList()));

        // Chart Data: Top 5 Selling Wines
        List<Object[]> topSecondaryData = wineService.getTopSellingWines(5);
        model.addAttribute("topWineLabels", topSecondaryData.stream().map(row -> row[0].toString()).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("topWineValues", topSecondaryData.stream().map(row -> row[1]).collect(java.util.stream.Collectors.toList()));

        // Chart Data: Inventory Low vs Normal
        model.addAttribute("inventoryLabels", List.of("Low Stock", "Normal Stock"));
        model.addAttribute("inventoryValues", List.of(lowStockCount, Math.max(0, totalInventories - lowStockCount)));

        return "admin-dashboard";
    }
}






