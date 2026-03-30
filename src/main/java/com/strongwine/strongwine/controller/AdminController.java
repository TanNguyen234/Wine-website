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
        // Statistics
        long totalWines = wineService.getAllWines().size();
        long totalUsers = userService.getAllUsers().size();
        long totalOrders = orderService.getAllOrders().size();
        Double totalRevenue = orderService.getTotalRevenue();
        long lowStockCount = inventoryService.getLowStockInventories().size();
        long pendingPayments = paymentService.getRecentPayments().stream().filter(p -> p.getStatus().name().equals("PENDING")).count();
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
        
        // Lists
        model.addAttribute("wines", wineService.getAllWines());
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("orders", orderService.getAllOrders());
        model.addAttribute("reviews", reviewService.getAllReviews());
        model.addAttribute("inventories", inventoryService.getInventoryOverview());
        model.addAttribute("inventoryTransactions", inventoryService.getRecentTransactions());
        model.addAttribute("payments", paymentService.getRecentPayments());
        model.addAttribute("paymentTransactions", paymentService.getRecentTransactions());
        model.addAttribute("lowStockItems", inventoryService.getLowStockInventories());
        
        return "admin-dashboard";
    }
}






