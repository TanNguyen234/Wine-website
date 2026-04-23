package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.service.CategoryService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.WineService;
import com.strongwine.strongwine.repository.WarehouseRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.io.IOException;

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
    private final CategoryService categoryService;
    private final WarehouseRepository warehouseRepository;

    public AdminController(WineService wineService,
                           UserService userService,
                           OrderService orderService,
                           ReviewService reviewService,
                           InventoryService inventoryService,
                           PaymentService paymentService,
                           ShipperService shipperService,
                           ShipmentService shipmentService,
                           CategoryService categoryService,
                           WarehouseRepository warehouseRepository) {
        this.wineService = wineService;
        this.userService = userService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shipperService = shipperService;
        this.shipmentService = shipmentService;
        this.categoryService = categoryService;
        this.warehouseRepository = warehouseRepository;
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
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            model.addAttribute("revenueByWeekJson", mapper.writeValueAsString(orderService.getRevenueStatsByWeek()));
            model.addAttribute("revenueByMonthJson", mapper.writeValueAsString(orderService.getRevenueStatsByMonth()));
            model.addAttribute("revenueByQuarterJson", mapper.writeValueAsString(orderService.getRevenueStatsByQuarter()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            model.addAttribute("revenueByWeekJson", "{}");
            model.addAttribute("revenueByMonthJson", "{}");
            model.addAttribute("revenueByQuarterJson", "{}");
        }
        
        return "admin-dashboard";
    }

    /**
     * Admin inventory management page
     */
    @GetMapping("/inventory")
    public String inventoryPage(Model model) {
        var inventories = inventoryService.getInventoryOverview();
        var lowStockItems = inventoryService.getLowStockInventories();
        var transactions = inventoryService.getRecentTransactions();
        var categories = categoryService.getAllCategories();
        var warehouses = warehouseRepository.findAll();

        model.addAttribute("inventories", inventories);
        model.addAttribute("lowStockItems", lowStockItems);
        model.addAttribute("lowStockCount", lowStockItems.size());
        model.addAttribute("transactions", transactions);
        model.addAttribute("totalStockQuantity", inventoryService.getTotalStockQuantity());
        model.addAttribute("totalTransactionCount", inventoryService.getTotalTransactionCount());
        model.addAttribute("categories", categories);
        model.addAttribute("warehouses", warehouses);

        return "admin-inventory";
    }

    @GetMapping("/export-revenue")
    public void exportRevenueCsv(HttpServletResponse response, String period) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; file=\"revenue_detailed_report_" + (period != null ? period : "all") + ".csv\"");
        response.setCharacterEncoding("UTF-8");

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        java.util.List<com.strongwine.strongwine.entity.Order> paidOrders = orderService.getPaidOrders();
        
        // Group orders by period bucket
        java.util.Map<String, java.util.List<com.strongwine.strongwine.entity.Order>> groupedOrders = new java.util.TreeMap<>();
        
        for (com.strongwine.strongwine.entity.Order order : paidOrders) {
            java.time.LocalDateTime dt = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
            String bucket = "Unknown";
            if (dt != null) {
                if ("weekly".equals(period)) {
                    int year = dt.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
                    int week = dt.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    bucket = String.format("%d-W%02d", year, week);
                } else if ("quarterly".equals(period)) {
                    int year = dt.getYear();
                    int q = dt.get(java.time.temporal.IsoFields.QUARTER_OF_YEAR);
                    bucket = String.format("%d-Q%d", year, q);
                } else {
                    bucket = dt.format(formatter);
                }
            }
            groupedOrders.computeIfAbsent(bucket, k -> new java.util.ArrayList<>()).add(order);
        }

        response.getWriter().write('\uFEFF'); // BOM for Excel UTF-8
        response.getWriter().println("Nhom Thoi Gian,Ma Don Hang,Nguoi Dung,Thoi Gian Thanh Toan,Phuong Thuc,Gia Tri (VND)");

        for (java.util.Map.Entry<String, java.util.List<com.strongwine.strongwine.entity.Order>> entry : groupedOrders.entrySet()) {
            String bucketName = entry.getKey();
            java.util.List<com.strongwine.strongwine.entity.Order> ordersInBucket = entry.getValue();
            double bucketTotal = 0;

            for (com.strongwine.strongwine.entity.Order order : ordersInBucket) {
                java.time.LocalDateTime dt = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
                String id = String.valueOf(order.getId());
                String user = order.getUser() != null ? order.getUser().getUsername() : "Khach Le";
                String paidTimeStr = dt != null ? dt.toString() : "";
                String method = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "";
                double amount = order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0;
                bucketTotal += amount;
                
                response.getWriter().println(String.format("%s,%s,%s,%s,%s,%f", bucketName, id, user, paidTimeStr, method, amount));
            }
            
            // Sub-total row for the bucket
            response.getWriter().println(String.format("TỔNG CỘNG %s,,,,,%f", bucketName, bucketTotal));
            response.getWriter().println(",,,,,"); // Empty line for separation
        }
    }
}






