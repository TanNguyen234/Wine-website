package com.strongwine.strongwine.ui;

import com.strongwine.strongwine.controller.AdminShipperController;
import com.strongwine.strongwine.controller.AdminWineController;
import com.strongwine.strongwine.controller.ReturnController;
import com.strongwine.strongwine.entity.AdminAuditLog;
import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.ReturnRequest;
import com.strongwine.strongwine.entity.ReturnStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.EnumDisplayService;
import com.strongwine.strongwine.service.FileStorageService;
import com.strongwine.strongwine.service.ReturnRequestService;
import com.strongwine.strongwine.service.ShipperCodBatchService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.WineService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AdminV3TemplateTest {

    private static final List<String> ALL_ADMIN_TEMPLATES = List.of(
            "admin-dashboard",
            "admin-wines",
            "wine-form",
            "admin-wine-import",
            "admin-inventory",
            "admin-orders",
            "admin-payments",
            "admin-shipments",
            "admin-shipment-form",
            "admin-shippers",
            "admin-shipper-form",
            "admin-cod-batches",
            "admin-returns",
            "admin-vouchers",
            "admin-users",
            "admin-user-form",
            "admin-audit-logs",
            "admin-profile"
    );

    private static SpringTemplateEngine templateEngine;
    private static StaticApplicationContext applicationContext;

    @BeforeAll
    static void setUpEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("enumDisplayService", EnumDisplayService.class);
        applicationContext.refresh();

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    private String render(String templateName, Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication webApp = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(webApp.buildExchange(request, response), Locale.forLanguageTag("vi-VN"));
        context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(applicationContext, null));
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return templateEngine.process(templateName, context);
    }

    @Test
    @DisplayName("All 18 Admin V3 templates render cleanly when sparse/null model attributes exist under StaticApplicationContext")
    void allAdminTemplatesAreNullSafeAndWrappedInAdminShell() {
        for (String template : ALL_ADMIN_TEMPLATES) {
            String html = render(template, Map.of());
            assertNotNull(html, "Rendered HTML should not be null for " + template);
            assertTrue(html.contains("admin-shell"), "Template should be wrapped in admin-shell: " + template);
            assertTrue(html.contains("adminLiveBadge"), "Template should include topbar live badge: " + template);
            assertTrue(html.contains("data-theme-toggle"), "Template should include theme toggle: " + template);
            assertFalse(html.contains("cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"),
                    "Admin V3 templates should not rely on Bootstrap CSS CDN: " + template);
        }
    }

    @Test
    @DisplayName("Dashboard renders operational KPI alerts for pending returns, pending COD batches, and REFUND_REQUIRED")
    void dashboardRendersOperationalAlertsAndCharts() {
        Wine wine = new Wine("Château Margaux", "Red", 2018, new BigDecimal("8500000"), "Grand Cru");
        wine.setId(10L);
        wine.setCountry("France");

        String html = render("admin-dashboard", Map.ofEntries(
                Map.entry("success", "Đồng bộ dữ liệu thành công"),
                Map.entry("pendingReturnsCount", 3L),
                Map.entry("pendingCodBatchesCount", 2L),
                Map.entry("refundRequiredCount", 4L),
                Map.entry("lowStockCount", 5L),
                Map.entry("totalOrders", 24L),
                Map.entry("ordersPending", 6L),
                Map.entry("shippingActive", 8L),
                Map.entry("totalWines", 15L),
                Map.entry("paidRevenue", new BigDecimal("120500000")),
                Map.entry("wines", List.of(wine)),
                Map.entry("availableStockByWineId", Map.of(10L, 12)),
                Map.entry("revenueLabelsJson", "[\"T10\",\"T11\"]"),
                Map.entry("revenueValuesJson", "[50000000,70500000]"),
                Map.entry("categoryLabelsJson", "[\"Vang đỏ\",\"Rượu truyền thống\"]"),
                Map.entry("categoryValuesJson", "[10,5]"),
                Map.entry("currentPage", 0),
                Map.entry("pageSize", 10),
                Map.entry("totalEntries", 1L),
                Map.entry("totalPages", 1),
                Map.entry("hasNext", false),
                Map.entry("hasPrevious", false)
        ));

        assertTrue(html.contains("REFUND_REQUIRED"));
        assertTrue(html.contains("/admin/returns"));
        assertTrue(html.contains("/admin/shippers/cod-batches"));
        assertTrue(html.contains("id=\"revenueChart\""));
        assertTrue(html.contains("/js/admin-live-notifications.js"));
        assertTrue(html.contains("Đồng bộ dữ liệu thành công"));
    }

    @Test
    @DisplayName("New Admin V3 pages render domain data, CSRF forms, and actions cleanly")
    void newAdminV3TemplatesRenderDomainDataAndActions() {
        Wine wine = new Wine("Rượu Nếp Cái Hoa Vàng", "Traditional", 2022, new BigDecimal("420000"), "Truyền thống");
        wine.setId(12L);
        wine.setCountry("Việt Nam");

        String winesHtml = render("admin-wines", Map.ofEntries(
                Map.entry("wines", List.of(wine)),
                Map.entry("availableStockByWineId", Map.of(12L, 18)),
                Map.entry("types", List.of("Red", "White", "Rose", "Sparkling", "Traditional")),
                Map.entry("currentPage", 0),
                Map.entry("pageSize", 10),
                Map.entry("totalPages", 1),
                Map.entry("totalEntries", 1L),
                Map.entry("hasNext", false),
                Map.entry("hasPrevious", false),
                Map.entry("keyword", ""),
                Map.entry("type", "Traditional")
        ));
        assertTrue(winesHtml.contains("Rượu Nếp Cái Hoa Vàng"));
        assertTrue(winesHtml.contains("/admin/wines/edit/12"));
        assertTrue(winesHtml.contains("/admin/wines/delete/12"));
        assertTrue(winesHtml.contains("name=\"_csrf\""));

        Voucher voucher = new Voucher();
        voucher.setId(5L);
        voucher.setCode("MAISON20");
        voucher.setType(VoucherType.PERCENT);
        voucher.setValue(new BigDecimal("20"));
        voucher.setMinOrderValue(new BigDecimal("500000"));
        voucher.setActive(true);

        String vouchersHtml = render("admin-vouchers", Map.of(
                "vouchers", List.of(voucher),
                "voucherTypes", VoucherType.values(),
                "types", VoucherType.values(),
                "totalVouchers", 1,
                "activeVouchers", 1L,
                "editingVoucher", voucher
        ));
        assertTrue(vouchersHtml.contains("MAISON20"));
        assertTrue(vouchersHtml.contains("/admin/vouchers/5/toggle"));
        assertTrue(vouchersHtml.contains("Cập nhật Voucher"));

        Order order = new Order();
        order.setId(88L);
        User customer = new User("minhanh", "pw", "USER");
        ReturnRequest rr = new ReturnRequest();
        rr.setId(3L);
        rr.setOrder(order);
        rr.setUser(customer);
        rr.setReason("Chai rỉ nút bần");
        rr.setStatus(ReturnStatus.REQUESTED);
        rr.setRefundAmount(new BigDecimal("1800000"));

        String returnsHtml = render("admin-returns", Map.of(
                "returns", List.of(rr),
                "returnStatuses", ReturnStatus.values(),
                "requestedCount", 1L,
                "approvedCount", 0L,
                "refundedCount", 0L,
                "rejectedCount", 0L
        ));
        assertTrue(returnsHtml.contains("/admin/returns/3/approve"));
        assertTrue(returnsHtml.contains("/admin/returns/3/reject"));
        assertTrue(returnsHtml.contains("value=\"returns\""));

        Shipper shipper = new Shipper();
        shipper.setId(4L);
        shipper.setName("Trần Minh Giao");
        shipper.setPhone("0901112233");
        ShipperCodBatch batch = new ShipperCodBatch();
        batch.setId(9L);
        batch.setBatchCode("COD-2026-09");
        batch.setShipper(shipper);
        batch.setOrderCount(4);
        batch.setTotalExpectedAmount(new BigDecimal("5000000"));
        batch.setTotalSubmittedAmount(new BigDecimal("4900000"));
        batch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);

        String codHtml = render("admin-cod-batches", Map.of(
                "codBatches", List.of(batch),
                "codBatchStatuses", CodBatchStatus.values(),
                "pendingCount", 1L,
                "verifiedCount", 0L,
                "disputedCount", 0L,
                "totalBatches", 1
        ));
        assertTrue(codHtml.contains("COD-2026-09"));
        assertTrue(codHtml.contains("Lệch số"));
        assertTrue(codHtml.contains("/admin/shippers/cod-batches/9/verify"));
        assertTrue(codHtml.contains("value=\"cod-batches\""));

        AdminAuditLog log = new AdminAuditLog();
        log.setActorUsername("admin");
        log.setAction("WINE_UPDATE");
        log.setTargetType("WINE");
        log.setTargetId(12L);
        log.setBeforeJson("{\"price\":400000}");
        log.setAfterJson("{\"price\":420000}");
        log.setIpAddress("127.0.0.1");
        String auditHtml = render("admin-audit-logs", Map.of(
                "auditLogs", List.of(log),
                "currentPage", 0,
                "totalPages", 1,
                "totalEntries", 1L,
                "hasNext", false,
                "hasPrevious", false
        ));
        assertTrue(auditHtml.contains("WINE_UPDATE"));
        assertTrue(auditHtml.contains("diff-grid"));
        assertTrue(auditHtml.contains("420000"));
    }

    @Test
    @DisplayName("Orders, Payments, and Shipments preserve all V3 landmarks and strict OTP masking")
    void ordersPaymentsAndShipmentsKeepLandmarksAndOtpMasking() {
        Order order = new Order();
        order.setId(77L);
        order.setShippingFullName("Nguyễn Văn Khách");
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(new BigDecimal("2500000"));
        order.setOrderDate(LocalDateTime.now());

        String ordersHtml = render("admin-orders", Map.of(
                "orders", List.of(order),
                "currentPage", 0,
                "pageSize", 10,
                "totalPages", 1,
                "totalEntries", 1L,
                "hasNext", false,
                "hasPrevious", false
        ));
        assertTrue(ordersHtml.contains("/admin/orders/77/mark-paid"));
        assertTrue(ordersHtml.contains("/admin/orders/77/settle-cod"));
        assertTrue(ordersHtml.contains("/admin/orders/77/cancel"));
        assertTrue(ordersHtml.contains("format=pdf"));
        assertTrue(ordersHtml.contains("format=xlsx"));

        Payment refundReq = new Payment();
        refundReq.setId(19L);
        refundReq.setOrder(order);
        refundReq.setMethod(PaymentMethod.STRIPE);
        refundReq.setStatus(PaymentStatus.REFUND_REQUIRED);
        refundReq.setAmount(new BigDecimal("2500000"));
        refundReq.setPaymentReference("pi_test_98765");

        String paymentsHtml = render("admin-payments", Map.of(
                "payments", List.of(refundReq),
                "refundRequiredPayments", List.of(refundReq),
                "refundRequiredCount", 1L,
                "totalItems", 1L,
                "currentPage", 0,
                "pageSize", 10,
                "totalPages", 1,
                "totalEntries", 1L,
                "hasNext", false,
                "hasPrevious", false
        ));
        assertTrue(paymentsHtml.contains("pi_test_98765"));
        assertTrue(paymentsHtml.contains("Đánh dấu đã hoàn tiền"));

        Shipment shipment = new Shipment();
        shipment.setId(50L);
        shipment.setOrder(order);
        shipment.setStatus(ShipmentStatus.COMPLETED);
        shipment.setOtpVerified(true);
        shipment.setOtpCode("482910");

        String shipmentsHtml = render("admin-shipments", Map.of(
                "shipments", List.of(shipment),
                "currentPage", 0,
                "pageSize", 10,
                "totalPages", 1,
                "totalEntries", 1L,
                "hasNext", false,
                "hasPrevious", false
        ));
        assertTrue(shipmentsHtml.contains("Trạng thái OTP"));
        assertTrue(shipmentsHtml.contains("Đã xác thực"));
        assertFalse(shipmentsHtml.contains("482910"), "Raw OTP digits must never appear in admin-shipments HTML");
    }

    @Test
    @DisplayName("Admin JS scripts follow strict IIFE contract with safe DOM, sw:themechange, and SSE Last-Event-ID")
    void adminJsScriptsRespectSafetyAndV3Contracts() throws Exception {
        for (String name : List.of("admin-charts.js", "admin-excel-import.js", "admin-live-notifications.js", "admin-inventory.js")) {
            Path path = Path.of("src/main/resources/static/js", name);
            assertTrue(Files.exists(path), "Missing JS file: " + name);
            String js = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(js.contains("\"use strict\";"), "Expected strict mode in " + name);
            assertFalse(js.contains("innerHTML"), "innerHTML forbidden in " + name);
            assertFalse(js.contains("window.alert"), "window.alert forbidden in " + name);
            assertFalse(js.contains("window.confirm"), "window.confirm forbidden in " + name);
            assertFalse(js.contains("export "), "Bare ESM export forbidden for deferred classic script " + name);
            assertFalse(js.contains("import "), "Bare ESM import forbidden for deferred classic script " + name);
        }

        String chartsJs = Files.readString(Path.of("src/main/resources/static/js/admin-charts.js"), StandardCharsets.UTF_8);
        assertTrue(chartsJs.contains("sw:themechange"));
        assertTrue(chartsJs.contains("revenueChart"));

        String sseJs = Files.readString(Path.of("src/main/resources/static/js/admin-live-notifications.js"), StandardCharsets.UTF_8);
        assertTrue(sseJs.contains("/api/live/admin/notifications"));
        assertTrue(sseJs.contains("lastEventId"));
        assertTrue(sseJs.contains("adminLiveBadge"));
        assertTrue(sseJs.contains("adminNotifList"));
        assertTrue(sseJs.contains("adminNotifBadgeCount"));
        assertTrue(sseJs.contains("SwToast"));
    }

    @Test
    @DisplayName("AdminWineController GET /admin/wines renders paged admin-wines view with filter attributes")
    void adminWineControllerRendersAdminWines() throws Exception {
        WineService wineService = mock(WineService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        Wine sample = new Wine("Margaux", "Red", 2019, new BigDecimal("4500000"), "Bordeaux");
        sample.setId(1L);
        Page<Wine> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 10), 1);

        when(wineService.searchWinesPage(eq("Margaux"), eq("Red"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        AdminWineController controller = new AdminWineController(wineService, fileStorageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/wines")
                        .param("keyword", "Margaux")
                        .param("type", "Red")
                        .param("sortBy", "price")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-wines"))
                .andExpect(model().attributeExists(
                        "wines", "currentPage", "pageSize", "sortBy", "sortDir",
                        "totalPages", "hasNext", "hasPrevious", "totalEntries",
                        "keyword", "type", "types"));
    }

    @Test
    @DisplayName("ReturnController splits SSR /admin/returns and JSON /api/admin/returns and honors returnTo=returns")
    void returnControllerSsrApiAndRedirectBehavior() throws Exception {
        ReturnRequestService returnService = mock(ReturnRequestService.class);
        UserRepository userRepo = mock(UserRepository.class);

        ReturnRequest req1 = new ReturnRequest();
        req1.setId(7L);
        req1.setStatus(ReturnStatus.REQUESTED);
        req1.setReason("Chai lỗi");
        when(returnService.getReturnRequestsByStatus(null)).thenReturn(List.of(req1));

        ReturnController controller = new ReturnController(returnService, userRepo);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/returns"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-returns"))
                .andExpect(model().attributeExists(
                        "returns", "returnStatuses", "requestedCount", "approvedCount", "refundedCount", "rejectedCount"))
                .andExpect(model().attribute("requestedCount", 1L));

        mockMvc.perform(get("/api/admin/returns"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value(7));

        mockMvc.perform(post("/admin/returns/7/approve").param("returnTo", "returns"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/returns"));
        verify(returnService).approveReturn(eq(7L), eq("admin"), isNull());

        mockMvc.perform(post("/admin/returns/7/reject"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"));
    }

    @Test
    @DisplayName("AdminShipperController renders admin-cod-batches and honors returnTo=cod-batches")
    void adminShipperControllerCodBatchesSsrAndRedirectBehavior() throws Exception {
        ShipperService shipperService = mock(ShipperService.class);
        ShipperCodBatchService batchService = mock(ShipperCodBatchService.class);

        ShipperCodBatch pending = new ShipperCodBatch();
        pending.setId(9L);
        pending.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
        when(batchService.getAllBatches()).thenReturn(List.of(pending));

        AdminShipperController controller = new AdminShipperController(shipperService, batchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/shippers/cod-batches"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-cod-batches"))
                .andExpect(model().attributeExists(
                        "codBatches", "codBatchStatuses", "pendingCount", "verifiedCount", "disputedCount", "totalBatches"))
                .andExpect(model().attribute("pendingCount", 1L));

        mockMvc.perform(post("/admin/shippers/cod-batches/9/verify").param("returnTo", "cod-batches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shippers/cod-batches"));

        mockMvc.perform(post("/admin/shippers/cod-batches/9/dispute").param("reason", "Thiếu 50k"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shippers"));
    }
}
