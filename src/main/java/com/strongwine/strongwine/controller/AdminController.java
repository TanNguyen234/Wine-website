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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.strongwine.strongwine.repository.ReturnRequestRepository returnRequestRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.strongwine.strongwine.repository.ShipperCodBatchRepository shipperCodBatchRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.strongwine.strongwine.repository.PaymentRepository paymentRepository;

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
    
    public String dashboard(int days, LocalDate fromDate, LocalDate toDate, Model model) {
        return dashboard(days, fromDate, toDate, 0, 10, model);
    }

    /**
     * Admin dashboard with configurable daily revenue range and quick wine catalog pagination
     */
    @GetMapping({"", "/dashboard"})
    public String dashboard(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        long totalWines = wineService.countWines();
        long totalUsers = userService.countUsers();
        long totalOrders = orderService.countOrders();
        Double totalRevenue = orderService.getTotalRevenue();
        long lowStockCount = inventoryService.countLowStockInventories();
        long pendingPayments = paymentService.countPendingPayments();
        Map<String, Long> shipperStats = Objects.requireNonNullElse(shipperService.getShipperOverviewStats(), Map.of());
        Map<?, Long> shipmentStats = Objects.requireNonNullElse(shipmentService.getShipmentStatusStats(), Map.of());
        long totalShipments = shipmentStats.values().stream()
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        List<Inventory> rawLowStock = inventoryService.getLowStockInventories();
        List<Inventory> lowStockItems = (rawLowStock == null ? List.<Inventory>of() : rawLowStock).stream().limit(6).toList();
        var recentPage = orderService.getAllOrdersPage(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "orderDate"))
        );
        List<Order> recentOrders = recentPage != null ? recentPage.getContent() : List.of();

        // Calculate dynamic date range: defaults to today and 7 days prior (today - 6 days through today)
        List<Order> allOrders = Objects.requireNonNullElse(orderService.getAllOrders(), List.of());
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;

        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                LocalDate temp = fromDate;
                fromDate = toDate;
                toDate = temp;
            }
            // Safeguard against extreme ranges to maintain chart readability
            if (ChronoUnit.DAYS.between(fromDate, toDate) > 90) {
                fromDate = toDate.minusDays(89);
            }
            start = fromDate;
            end = toDate;
        } else {
            int effectiveDays = (days < 1 || days > 90) ? 7 : days;
            end = today;
            start = end.minusDays(effectiveDays - 1);
        }

        long daysSpan = ChronoUnit.DAYS.between(start, end) + 1;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter fullFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> revLabels = new ArrayList<>();
        List<Long> revValues = new ArrayList<>();
        double rangeRevenue = 0.0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            final LocalDate currentDay = d;
            revLabels.add("\"" + currentDay.format(fmt) + "\"");
            double daySumVnd = allOrders.stream()
                    .filter(o -> o != null
                            && o.getPaymentStatus() == PaymentStatus.SUCCESS
                            && o.getStatus() != com.strongwine.strongwine.entity.OrderStatus.CANCELLED
                            && o.getStatus() != com.strongwine.strongwine.entity.OrderStatus.RETURNED
                            && o.getOrderDate() != null
                            && o.getOrderDate().toLocalDate().equals(currentDay)
                            && o.getTotalPrice() != null)
                    .mapToDouble(o -> o.getTotalPrice().doubleValue())
                    .sum();
            revValues.add(Math.round(daySumVnd));
            rangeRevenue += daySumVnd;
        }

        // Real category breakdown & paged quick-catalog table from Wine table
        List<Wine> rawAllWines = wineService.getAllWines();
        List<Wine> allWines = rawAllWines == null ? List.of() : rawAllWines;
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        typeCounts.put("Vang đỏ", 0L);
        typeCounts.put("Vang trắng", 0L);
        typeCounts.put("Vang hồng", 0L);
        typeCounts.put("Vang sủi", 0L);
        typeCounts.put("Rượu truyền thống", 0L);
        for (Wine w : allWines) {
            if (w == null) {
                continue;
            }
            String t = w.getType() == null ? "" : w.getType().trim().toLowerCase(Locale.ROOT);
            if (t.contains("red") || t.contains("đỏ")) {
                typeCounts.put("Vang đỏ", typeCounts.get("Vang đỏ") + 1);
            } else if (t.contains("white") || t.contains("trắng")) {
                typeCounts.put("Vang trắng", typeCounts.get("Vang trắng") + 1);
            } else if (t.contains("rose") || t.contains("rosé") || t.contains("hồng")) {
                typeCounts.put("Vang hồng", typeCounts.get("Vang hồng") + 1);
            } else if (t.contains("traditional") || t.contains("truyền thống") || t.contains("truyen thong")) {
                typeCounts.put("Rượu truyền thống", typeCounts.get("Rượu truyền thống") + 1);
            } else {
                typeCounts.put("Vang sủi", typeCounts.get("Vang sủi") + 1);
            }
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int totalWineEntries = allWines.size();
        int totalPages = totalWineEntries == 0 ? 0 : (int) Math.ceil((double) totalWineEntries / safeSize);
        if (totalPages > 0 && safePage >= totalPages) {
            safePage = totalPages - 1;
        }
        int fromIndex = Math.min(safePage * safeSize, totalWineEntries);
        int toIndex = Math.min(fromIndex + safeSize, totalWineEntries);
        List<Wine> pagedWines = allWines.subList(fromIndex, toIndex);

        List<Long> wineIds = pagedWines.stream()
                .filter(Objects::nonNull)
                .map(Wine::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Integer> availableStockByWineId = !wineIds.isEmpty()
                ? Objects.requireNonNullElse(inventoryService.getAvailableStockByWineIds(wineIds), Map.of())
                : Map.of();

        long pendingReturnsCount = returnRequestRepository != null
                ? returnRequestRepository.countByStatus(com.strongwine.strongwine.entity.ReturnStatus.REQUESTED)
                : 0L;
        long pendingCodBatchesCount = shipperCodBatchRepository != null
                ? shipperCodBatchRepository.countByStatus(com.strongwine.strongwine.entity.CodBatchStatus.PENDING_SETTLEMENT)
                : 0L;
        long refundRequiredCount = paymentRepository != null
                ? paymentRepository.countByStatus(PaymentStatus.REFUND_REQUIRED)
                : paymentService.countRefundRequiredPayments();

        model.addAttribute("totalWines", totalWines);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        // Names below are read by admin-dashboard.html; they were missing and the KPI cards always showed 0.
        model.addAttribute("paidRevenue", totalRevenue != null ? totalRevenue : 0.0);
        model.addAttribute("ordersPending", orderService.countOrdersByStatus(com.strongwine.strongwine.entity.OrderStatus.PENDING));
        model.addAttribute("shippingActive", countActiveShipments(shipmentStats));
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("lowStockItems", lowStockItems);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("pendingPayments", pendingPayments);
        model.addAttribute("pendingReturnsCount", pendingReturnsCount);
        model.addAttribute("pendingCodBatchesCount", pendingCodBatchesCount);
        model.addAttribute("refundRequiredCount", refundRequiredCount);
        model.addAttribute("shipperStats", shipperStats);
        model.addAttribute("shipmentStats", shipmentStats);
        model.addAttribute("totalShipments", totalShipments);
        model.addAttribute("wines", pagedWines);
        model.addAttribute("availableStockByWineId", availableStockByWineId);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasNext", safePage + 1 < totalPages);
        model.addAttribute("hasPrevious", safePage > 0 && totalPages > 0);
        model.addAttribute("totalEntries", (long) totalWineEntries);
        model.addAttribute("selectedDays", daysSpan);
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());
        model.addAttribute("rangeDateText", start.format(fullFmt) + " - " + end.format(fullFmt));
        model.addAttribute("rangeRevenue", rangeRevenue);
        model.addAttribute("revenueLabelsJson", "[" + String.join(",", revLabels) + "]");
        model.addAttribute("revenueValuesJson", revValues.toString());
        model.addAttribute("categoryLabelsJson", "[\"Vang đỏ\",\"Vang trắng\",\"Vang hồng\",\"Vang sủi\",\"Rượu truyền thống\"]");
        model.addAttribute("categoryValuesJson", typeCounts.values().stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]")));
        model.addAttribute("categoryCounts", typeCounts);

        return "admin-dashboard";
    }

    /** Shipments still on the delivery route: assigned, picked up or out for delivery. */
    static long countActiveShipments(Map<?, Long> shipmentStats) {
        return shipmentStats.entrySet().stream()
                .filter(e -> e.getKey() instanceof com.strongwine.strongwine.entity.ShipmentStatus status
                        && (status == com.strongwine.strongwine.entity.ShipmentStatus.ASSIGNED
                        || status == com.strongwine.strongwine.entity.ShipmentStatus.PICKED_UP
                        || status == com.strongwine.strongwine.entity.ShipmentStatus.DELIVERING))
                .mapToLong(e -> e.getValue() == null ? 0L : e.getValue())
                .sum();
    }

    @GetMapping("/wines/import")
    public String wineImportPage(Model model) {
        Warehouse defaultWh = inventoryService.getDefaultWarehouse();
        model.addAttribute("defaultWarehouse", defaultWh);
        return "admin-wine-import";
    }
}






