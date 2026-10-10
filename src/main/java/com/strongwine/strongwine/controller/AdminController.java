package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.WineService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
        long totalWines = wineService.countWines();
        long totalUsers = userService.countUsers();
        long totalOrders = orderService.countOrders();
        Double totalRevenue = orderService.getTotalRevenue();
        long lowStockCount = inventoryService.countLowStockInventories();
        long pendingPayments = paymentService.countPendingPayments();
        Map<String, Long> shipperStats = shipperService.getShipperOverviewStats();
        Map<?, Long> shipmentStats = shipmentService.getShipmentStatusStats();
        long totalShipments = shipmentStats.values().stream().mapToLong(Long::longValue).sum();

        List<Inventory> lowStockItems = inventoryService.getLowStockInventories().stream().limit(6).toList();
        List<Order> recentOrders = orderService.getAllOrdersPage(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "orderDate"))
        ).getContent();

        // Real 7-day revenue series (in million VND) from paid orders
        List<Order> allOrders = orderService.getAllOrders();
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<String> revLabels = new ArrayList<>();
        List<Double> revValues = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            revLabels.add("\"" + day.format(fmt) + "\"");
            double daySum = allOrders.stream()
                    .filter(o -> o.getPaymentStatus() == PaymentStatus.SUCCESS
                            && o.getOrderDate() != null
                            && o.getOrderDate().toLocalDate().equals(day)
                            && o.getTotalPrice() != null)
                    .mapToDouble(o -> o.getTotalPrice().doubleValue() / 1_000_000.0)
                    .sum();
            revValues.add(Math.round(daySum * 100.0) / 100.0);
        }

        // Real category breakdown from Wine table
        List<Wine> allWines = wineService.getAllWines();
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        typeCounts.put("Vang đỏ", 0L);
        typeCounts.put("Vang trắng", 0L);
        typeCounts.put("Vang hồng", 0L);
        typeCounts.put("Vang sủi", 0L);
        for (Wine w : allWines) {
            String t = w.getType() == null ? "" : w.getType().trim().toLowerCase(Locale.ROOT);
            if (t.contains("red") || t.contains("đỏ")) {
                typeCounts.put("Vang đỏ", typeCounts.get("Vang đỏ") + 1);
            } else if (t.contains("white") || t.contains("trắng")) {
                typeCounts.put("Vang trắng", typeCounts.get("Vang trắng") + 1);
            } else if (t.contains("rose") || t.contains("rosé") || t.contains("hồng")) {
                typeCounts.put("Vang hồng", typeCounts.get("Vang hồng") + 1);
            } else {
                typeCounts.put("Vang sủi", typeCounts.get("Vang sủi") + 1);
            }
        }

        model.addAttribute("totalWines", totalWines);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("lowStockItems", lowStockItems);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("pendingPayments", pendingPayments);
        model.addAttribute("shipperStats", shipperStats);
        model.addAttribute("shipmentStats", shipmentStats);
        model.addAttribute("totalShipments", totalShipments);
        model.addAttribute("revenueLabelsJson", "[" + String.join(",", revLabels) + "]");
        model.addAttribute("revenueValuesJson", revValues.toString());
        model.addAttribute("categoryLabelsJson", "[\"Vang đỏ\",\"Vang trắng\",\"Vang hồng\",\"Vang sủi\"]");
        model.addAttribute("categoryValuesJson", typeCounts.values().stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]")));
        model.addAttribute("categoryCounts", typeCounts);
        
        return "admin-dashboard";
    }

    @GetMapping("/wines/import")
    public String wineImportPage(Model model) {
        Warehouse defaultWh = inventoryService.getDefaultWarehouse();
        model.addAttribute("defaultWarehouse", defaultWh);
        return "admin-wine-import";
    }
}






