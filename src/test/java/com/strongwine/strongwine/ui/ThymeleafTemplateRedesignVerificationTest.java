package com.strongwine.strongwine.ui;

import com.strongwine.strongwine.dto.CheckoutForm;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.service.EnumDisplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ThymeleafTemplateRedesignVerificationTest {

    private SpringTemplateEngine templateEngine;
    private StaticApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("enumDisplayService", new EnumDisplayService());
        applicationContext.refresh();

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
        });
    }

    private Context createContext(Map<String, Object> vars) {
        Context ctx = new Context(Locale.forLanguageTag("vi-VN"));
        ctx.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(applicationContext, null)
        );
        ctx.setVariables(vars);
        return ctx;
    }

    @Test
    void shouldRenderAll26RedesignedTemplatesWithoutSpelErrors() throws Exception {
        User customer = new User();
        customer.setId(2L);
        customer.setUsername("demo");
        customer.setEmail("demo@strongwine.vn");
        customer.setRole("USER");

        User shipperUser = new User();
        shipperUser.setId(3L);
        shipperUser.setUsername("shipper.ops.hcm");
        shipperUser.setEmail("shipper@strongwine.vn");
        shipperUser.setRole("SHIPPER");

        Wine wine1 = new Wine();
        wine1.setId(1L);
        wine1.setName("Château Margaux Grand Cru");
        wine1.setType("Red");
        wine1.setCountry("Pháp");
        wine1.setYear(2018);
        wine1.setPrice(new BigDecimal("2850000"));
        wine1.setDescription("Hương trái cây chín đen và gỗ sồi Pháp.");
        wine1.setImageUrl("/uploads/margaux.jpg");

        Wine wine2 = new Wine();
        wine2.setId(2L);
        wine2.setName("Cloudy Bay Sauvignon Blanc");
        wine2.setType("White");
        wine2.setCountry("New Zealand");
        wine2.setYear(2022);
        wine2.setPrice(new BigDecimal("1150000"));
        wine2.setDescription("Thanh mát hương bưởi hồng và chanh dây.");
        wine2.setImageUrl("");

        Warehouse wh = new Warehouse();
        wh.setId(1L);
        wh.setName("Hầm Rượu Trung Tâm Quận 1");
        wh.setLocation("TP. Hồ Chí Minh");

        Inventory inv = new Inventory();
        inv.setId(10L);
        inv.setWine(wine1);
        inv.setWarehouse(wh);
        inv.setCurrentQuantity(24);
        inv.setReservedQuantity(2);
        inv.setReorderLevel(10);

        Inventory lowInv = new Inventory();
        lowInv.setId(11L);
        lowInv.setWine(wine2);
        lowInv.setWarehouse(wh);
        lowInv.setCurrentQuantity(4);
        lowInv.setReservedQuantity(1);
        lowInv.setReorderLevel(10);

        InventoryTransaction invTx = new InventoryTransaction();
        invTx.setId(100L);
        invTx.setWine(wine1);
        invTx.setWarehouse(wh);
        invTx.setOperationType(InventoryOperationType.IMPORT);
        invTx.setQuantity(12);
        invTx.setReferenceType("MANUAL_IMPORT");
        invTx.setReferenceId(1L);
        invTx.setUserId(1L);
        invTx.setCreatedAt(LocalDateTime.now());

        Order order = new Order(customer, new BigDecimal("2850000"));
        order.setId(20L);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.STRIPE);
        order.setShippingFullName("Nguyễn Văn A");
        order.setShippingPhone("0901234567");
        order.setShippingEmail("demo@strongwine.vn");
        order.setShippingAddress("123 Đồng Khởi, Quận 1, TP. Hồ Chí Minh");
        order.setPaymentReference("cs_test_123456");

        OrderItem item = new OrderItem(order, wine1, 1, wine1.getPrice());
        item.setId(30L);
        order.setOrderItems(List.of(item));

        Shipper shipper = new Shipper();
        shipper.setId(5L);
        shipper.setUser(shipperUser);
        shipper.setName("Trần Minh Giao");
        shipper.setPhone("0909888777");
        shipper.setVehicleType("Xe máy thùng bảo ôn");
        shipper.setStatus(ShipperStatus.ACTIVE);
        shipper.setIsAvailable(true);

        Shipment shipment = new Shipment();
        shipment.setId(50L);
        shipment.setOrder(order);
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.DELIVERING);
        shipment.setShippingName(order.getShippingFullName());
        shipment.setShippingPhone(order.getShippingPhone());
        shipment.setShippingAddress(order.getShippingAddress());
        shipment.setOtpCode("482910");
        shipment.setOtpAttemptCount(0);
        shipment.setOtpVerified(false);
        shipment.setPickedUpAt(LocalDateTime.now().minusMinutes(30));
        shipment.setDeliveringAt(LocalDateTime.now().minusMinutes(10));
        shipment.setUpdatedAt(LocalDateTime.now());

        Payment payment = new Payment();
        payment.setId(70L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalPrice());
        payment.setMethod(PaymentMethod.STRIPE);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentReference("pi_test_98765");
        payment.setCreatedAt(LocalDateTime.now());

        PaymentTransaction payTx = new PaymentTransaction();
        payTx.setId(80L);
        payTx.setPayment(payment);
        payTx.setTransactionType("STRIPE_SESSION");
        payTx.setStatus("SUCCESS");
        payTx.setPayload("{\"id\":\"evt_123\"}");
        payTx.setCreatedAt(LocalDateTime.now());

        Review review = new Review(wine1, customer, 5, "Cấu trúc tannin mượt mà, hậu vị rất sâu.");
        review.setId(90L);
        review.setCreatedAt(LocalDateTime.now());

        CheckoutForm checkoutForm = new CheckoutForm();
        checkoutForm.setEmail("demo@strongwine.vn");

        Map<ShipmentStatus, Long> shipmentStats = new EnumMap<>(ShipmentStatus.class);
        for (ShipmentStatus st : ShipmentStatus.values()) {
            shipmentStats.put(st, 2L);
        }

        com.strongwine.strongwine.dto.CartItemDto cartItem = new com.strongwine.strongwine.dto.CartItemDto();
        cartItem.setWine(wine1);
        cartItem.setQuantity(1);
        cartItem.setAvailableStock(22);
        cartItem.setLineTotal(wine1.getPrice());

        Map<String, Object> common = new HashMap<>();
        common.put("cartCount", 2);
        common.put("cartItems", List.of(cartItem));
        common.put("cartTotal", wine1.getPrice());
        common.put("totalPrice", wine1.getPrice());
        common.put("featuredWines", List.of(wine1, wine2));
        common.put("wines", List.of(wine1, wine2));
        common.put("wine", wine1);
        common.put("availableStock", 22);
        common.put("availableStockByWineId", Map.of(1L, 22, 2L, 3));
        common.put("countries", List.of("Pháp", "New Zealand", "Ý", "Chile"));
        common.put("reviews", List.of(review));
        common.put("currentPage", 0);
        common.put("totalPages", 2);
        common.put("pageSize", 9);
        common.put("totalItems", 2);
        common.put("totalEntries", 2);
        common.put("sortBy", "createdAt");
        common.put("sortDir", "desc");
        common.put("checkoutForm", checkoutForm);
        common.put("checkoutToken", "tok_test_001");
        common.put("paymentMethods", List.of("STRIPE", "COD"));
        common.put("orders", List.of(order));
        common.put("order", order);
        common.put("shipment", shipment);
        common.put("shipmentByOrderId", Map.of(20L, shipment));
        common.put("orderId", 20L);
        common.put("fullName", order.getShippingFullName());
        common.put("phone", order.getShippingPhone());
        common.put("address", order.getShippingAddress());
        common.put("orderStatus", order.getStatus());
        common.put("paymentStatus", order.getPaymentStatus());
        common.put("payment", payment);
        common.put("successUrl", "/orders/20?paid=true");
        common.put("failedUrl", "/orders/20?paid=false");
        common.put("cancelUrl", "/orders/20");
        common.put("token", "reset_tok_123");
        // Admin attributes
        common.put("totalRevenue", new BigDecimal("18500000"));
        common.put("totalOrders", 14L);
        common.put("totalWines", 16L);
        common.put("lowStockCount", 1);
        common.put("totalUsers", 8L);
        common.put("totalShipments", 12L);
        common.put("pendingPayments", 2L);
        common.put("revenueLabelsJson", "[\"T5/2026\",\"T6/2026\",\"T7/2026\",\"T8/2026\",\"T9/2026\",\"T10/2026\"]");
        common.put("revenueValuesJson", "[2500000,4200000,3100000,5800000,6400000,18500000]");
        common.put("categoryLabelsJson", "[\"Red\",\"White\",\"Rose\",\"Sparkling\"]");
        common.put("categoryValuesJson", "[8,4,2,2]");
        common.put("recentOrders", List.of(order));
        common.put("lowStockItems", List.of(lowInv));
        common.put("defaultWarehouse", wh);
        common.put("warehouses", List.of(wh));
        common.put("inventories", List.of(inv, lowInv));
        common.put("inventoryTransactions", List.of(invTx));
        common.put("payments", List.of(payment));
        common.put("paymentTransactions", List.of(payTx));
        common.put("shipments", List.of(shipment));
        common.put("shipmentStats", shipmentStats);
        common.put("statuses", Arrays.asList(ShipmentStatus.values()));
        common.put("shippers", List.of(shipper));
        common.put("allowedTransitionsByShipmentId", Map.of(50L, List.of(ShipmentStatus.COMPLETED, ShipmentStatus.FAILED)));
        common.put("editingShipment", shipment);
        common.put("canDelete", true);
        common.put("shipperStats", Map.of("total", 3L, "active", 2L, "available", 2L));
        common.put("activeLoadByShipperId", Map.of(5L, 1L));
        common.put("editingShipper", shipper);
        common.put("users", List.of(customer, shipperUser));
        common.put("editingUser", customer);
        common.put("roles", List.of("USER", "SHIPPER", "ADMIN"));
        // Shipper attributes
        common.put("currentShipperId", 5L);
        common.put("currentShipperName", shipper.getName());
        common.put("deliveringCount", 1L);
        common.put("assignedCount", 0L);
        common.put("doneCount", 4L);
        common.put("param", Map.of());

        Map<String, String> templateExpectedLandmarks = new LinkedHashMap<>();
        templateExpectedLandmarks.put("home", "hero");
        templateExpectedLandmarks.put("wine-list", "filterForm");
        templateExpectedLandmarks.put("wine-details", "detail-grid");
        templateExpectedLandmarks.put("cart", "data-cart-page=\"true\"");
        templateExpectedLandmarks.put("checkout", "checkoutForm");
        templateExpectedLandmarks.put("orders", "Đơn hàng #20");
        templateExpectedLandmarks.put("order-detail", "orderTimelineStepper");
        templateExpectedLandmarks.put("order-confirmation", "data-order-confirmation=\"true\"");
        templateExpectedLandmarks.put("payment-session", "Xác nhận giao dịch");
        templateExpectedLandmarks.put("login", "auth-layout");
        templateExpectedLandmarks.put("register", "auth-layout");
        templateExpectedLandmarks.put("forgot-password", "auth-layout");
        templateExpectedLandmarks.put("reset-password", "auth-layout");
        templateExpectedLandmarks.put("admin-dashboard", "revenueChart");
        templateExpectedLandmarks.put("admin-orders", "admin-shell");
        templateExpectedLandmarks.put("admin-inventory", "inventoryModal");
        templateExpectedLandmarks.put("admin-payments", "pi_test_98765");
        templateExpectedLandmarks.put("admin-shipments", "482910");
        templateExpectedLandmarks.put("admin-shipment-form", "Vận đơn #50");
        templateExpectedLandmarks.put("admin-shippers", "Trần Minh Giao");
        templateExpectedLandmarks.put("admin-shipper-form", "Xe máy thùng bảo ôn");
        templateExpectedLandmarks.put("admin-users", "demo@strongwine.vn");
        templateExpectedLandmarks.put("admin-user-form", "demo@strongwine.vn");
        templateExpectedLandmarks.put("admin-wine-import", "excelDropZone");
        templateExpectedLandmarks.put("wine-form", "multipart/form-data");
        templateExpectedLandmarks.put("shipper-dashboard", "shipper-phone");

        Path outDir = Path.of("target", "rendered-ui");
        Files.createDirectories(outDir);

        for (Map.Entry<String, String> entry : templateExpectedLandmarks.entrySet()) {
            String tpl = entry.getKey();
            String expected = entry.getValue();

            Map<String, Object> vars = new HashMap<>(common);
            if (tpl.equals("admin-shipper-form")) {
                vars.put("statuses", Arrays.asList(ShipperStatus.values()));
            }

            String html = assertDoesNotThrow(
                    () -> templateEngine.process(tpl, createContext(vars)),
                    "Failed rendering template: " + tpl
            );
            assertNotNull(html);
            assertTrue(html.contains(expected),
                    "Template " + tpl + " did not contain expected landmark: " + expected);

            Files.writeString(outDir.resolve(tpl + ".html"), html, StandardCharsets.UTF_8);
        }

        assertEquals(26, templateExpectedLandmarks.size());
    }
}
