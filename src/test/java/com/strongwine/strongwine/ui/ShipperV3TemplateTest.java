package com.strongwine.strongwine.ui;

import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.service.EnumDisplayService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShipperV3TemplateTest {

    private static SpringTemplateEngine templateEngine;
    private static StaticApplicationContext applicationContext;

    @BeforeAll
    static void setUpEngine() {
        applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("enumDisplayService", new EnumDisplayService());
        applicationContext.refresh();

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context,
                                                String base,
                                                Map<String, Object> parameters) {
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
    void rendersNullSafelyInEmptyStateUnderShipperShellWithoutBootstrap() {
        Map<String, Object> vars = new HashMap<>();
        String html = assertDoesNotThrow(
                () -> templateEngine.process("shipper-dashboard", createContext(vars))
        );

        assertTrue(html.contains("class=\"shipper-body\""));
        assertTrue(html.contains("class=\"shipper-phone\""));
        assertTrue(html.contains("/css/tokens.css"));
        assertTrue(html.contains("/css/shipper.css"));
        assertTrue(html.contains("/manifest.webmanifest"));
        assertTrue(html.contains("/sw-shipper.js"));
        assertTrue(html.contains("/js/shipper-mobile-actions.js"));
        assertTrue(html.contains("class=\"shipper-bottom-nav\""));
        assertTrue(html.contains("/shipper/cod-batches"));
        assertFalse(html.toLowerCase().contains("bootstrap"), "Shipper V3 UI must not load Bootstrap");
        assertFalse(html.toLowerCase().contains("font-awesome"), "Shipper V3 UI must not load FontAwesome");
        assertTrue(html.contains("Chưa có vận đơn được phân công"));
    }

    @Test
    void rendersStopsPaymentBadgesDialogsDangerZoneAndCodBatchesSafely() {
        User customer = new User();
        customer.setId(2L);
        customer.setUsername("buyer01");

        User shipperUser = new User();
        shipperUser.setId(3L);
        shipperUser.setUsername("shipper.hcm");

        Shipper shipper = new Shipper();
        shipper.setId(5L);
        shipper.setUser(shipperUser);
        shipper.setName("Trần Minh Giao");
        shipper.setPhone("0909888777");

        Wine wine1 = new Wine();
        wine1.setId(1L);
        wine1.setName("Château Margaux Grand Cru");
        wine1.setPrice(new BigDecimal("2850000"));

        Wine wine2 = new Wine();
        wine2.setId(2L);
        wine2.setName("Rượu Nếp Cái Hoa Vàng");
        wine2.setPrice(new BigDecimal("450000"));

        // COD Delivering Stop
        Order codOrder = new Order(customer, new BigDecimal("2850000"));
        codOrder.setId(101L);
        codOrder.setPaymentMethod(PaymentMethod.COD);
        codOrder.setPaymentStatus(PaymentStatus.PENDING);
        codOrder.setStatus(OrderStatus.PENDING);
        OrderItem codItem = new OrderItem(codOrder, wine1, 1, wine1.getPrice());
        codOrder.setOrderItems(List.of(codItem));

        Shipment codDelivering = new Shipment();
        codDelivering.setId(501L);
        codDelivering.setOrder(codOrder);
        codDelivering.setShipper(shipper);
        codDelivering.setStatus(ShipmentStatus.DELIVERING);
        codDelivering.setShippingName("Nguyễn Văn A");
        codDelivering.setShippingPhone("0901234567");
        codDelivering.setShippingAddress("123 Đồng Khởi, Quận 1, TP. Hồ Chí Minh");
        codDelivering.setOtpCode("482910");
        codDelivering.setProofOfDeliveryUrl("/uploads/pod-501.jpg");
        codDelivering.setDeliveringAt(LocalDateTime.now().minusMinutes(10));

        // Online-Paid Assigned Stop
        Order onlineOrder = new Order(customer, new BigDecimal("900000"));
        onlineOrder.setId(102L);
        onlineOrder.setPaymentMethod(PaymentMethod.STRIPE);
        onlineOrder.setPaymentStatus(PaymentStatus.SUCCESS);
        onlineOrder.setStatus(OrderStatus.PAID);
        OrderItem onlineItem = new OrderItem(onlineOrder, wine2, 2, wine2.getPrice());
        onlineOrder.setOrderItems(List.of(onlineItem));

        Shipment onlineAssigned = new Shipment();
        onlineAssigned.setId(502L);
        onlineAssigned.setOrder(onlineOrder);
        onlineAssigned.setShipper(shipper);
        onlineAssigned.setStatus(ShipmentStatus.ASSIGNED);
        onlineAssigned.setShippingName("Lê Thị B");
        onlineAssigned.setShippingPhone("0912345678");
        onlineAssigned.setShippingAddress("45 Lê Lợi, Quận 1, TP. Hồ Chí Minh");

        // Unbatched Completed COD Stop
        Order unbatchedOrder = new Order(customer, new BigDecimal("1500000"));
        unbatchedOrder.setId(103L);
        unbatchedOrder.setPaymentMethod(PaymentMethod.COD);
        unbatchedOrder.setPaymentStatus(PaymentStatus.PENDING);
        unbatchedOrder.setStatus(OrderStatus.PAID);

        Shipment unbatchedCod = new Shipment();
        unbatchedCod.setId(503L);
        unbatchedCod.setOrder(unbatchedOrder);
        unbatchedCod.setShipper(shipper);
        unbatchedCod.setStatus(ShipmentStatus.COMPLETED);
        unbatchedCod.setShippingName("Phạm Văn C");
        unbatchedCod.setShippingPhone("0987654321");
        unbatchedCod.setShippingAddress("88 Hai Bà Trưng, Quận 1, TP. Hồ Chí Minh");
        unbatchedCod.setCodCollectedAmount(new BigDecimal("1500000"));
        unbatchedCod.setOtpVerified(true);
        unbatchedCod.setCompletedAt(LocalDateTime.now().minusHours(1));

        // COD Batches (PENDING_SETTLEMENT, VERIFIED, DISPUTED)
        ShipperCodBatch pendingBatch = new ShipperCodBatch();
        pendingBatch.setId(1L);
        pendingBatch.setBatchCode("COD-20261114-AAA111");
        pendingBatch.setOrderCount(2);
        pendingBatch.setTotalExpectedAmount(new BigDecimal("2000000"));
        pendingBatch.setTotalSubmittedAmount(new BigDecimal("2000000"));
        pendingBatch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
        pendingBatch.setNote("Nộp ca sáng");
        pendingBatch.setSubmittedAt(LocalDateTime.now().minusHours(2));

        ShipperCodBatch verifiedBatch = new ShipperCodBatch();
        verifiedBatch.setId(2L);
        verifiedBatch.setBatchCode("COD-20261113-BBB222");
        verifiedBatch.setOrderCount(3);
        verifiedBatch.setTotalExpectedAmount(new BigDecimal("4500000"));
        verifiedBatch.setTotalSubmittedAmount(new BigDecimal("4500000"));
        verifiedBatch.setStatus(CodBatchStatus.VERIFIED);
        verifiedBatch.setVerifiedBy("admin");
        verifiedBatch.setVerifiedAt(LocalDateTime.now().minusDays(1));

        ShipperCodBatch disputedBatch = new ShipperCodBatch();
        disputedBatch.setId(3L);
        disputedBatch.setBatchCode("COD-20261112-CCC333");
        disputedBatch.setOrderCount(1);
        disputedBatch.setTotalExpectedAmount(new BigDecimal("1800000"));
        disputedBatch.setTotalSubmittedAmount(new BigDecimal("1700000"));
        disputedBatch.setStatus(CodBatchStatus.DISPUTED);
        disputedBatch.setNote("Thiếu 100.000 ₫ so với biên lai");
        disputedBatch.setVerifiedBy("ketoan");
        disputedBatch.setVerifiedAt(LocalDateTime.now().minusDays(2));

        Map<String, Object> vars = new HashMap<>();
        vars.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-shipper-test"));
        vars.put("isAdminView", false);
        vars.put("currentShipperId", 5L);
        vars.put("currentShipperName", shipper.getName());
        vars.put("shipments", List.of(codDelivering, onlineAssigned, unbatchedCod));
        vars.put("unbatchedCodShipments", List.of(unbatchedCod));
        vars.put("codBatches", List.of(pendingBatch, verifiedBatch, disputedBatch));
        vars.put("assignedCount", 1L);
        vars.put("deliveringCount", 1L);
        vars.put("doneCount", 1L);
        vars.put("success", "Đã tải lên ảnh bằng chứng giao hàng");
        vars.put("error", "OTP không đúng");

        String html = templateEngine.process("shipper-dashboard", createContext(vars));

        // Shipper identity & SSE attribute
        assertTrue(html.contains("data-shipper-id=\"5\""));
        assertTrue(html.contains("Trần Minh Giao"));
        assertFalse(html.contains("482910"), "Shipper dashboard must never expose the secret OTP value in HTML");

        // Sticky KPI + 5 Filter Chips
        assertTrue(html.contains("Tất cả"));
        assertTrue(html.contains("Cần nhận"));
        assertTrue(html.contains("Đang giao"));
        assertTrue(html.contains("Hoàn tất"));
        assertTrue(html.contains("Bảng kê COD"));

        // Pinned landmarks + stop card details
        assertTrue(html.contains("Địa chỉ giao hàng"));
        assertTrue(html.contains("Sản phẩm trong đơn"));
        assertTrue(html.contains("Château Margaux Grand Cru"));
        assertTrue(html.contains("SL: 1 chai"));
        assertTrue(html.contains("tel:0901234567"));
        assertTrue(html.contains("https://www.google.com/maps/dir/"));
        assertTrue(html.contains("CẦN THU COD"));
        assertTrue(html.contains("2,850,000 ₫"));
        assertTrue(html.contains("ĐÃ THANH TOÁN ONLINE"));
        assertTrue(html.contains("Đã có ảnh POD"));

        // Flash banners
        assertTrue(html.contains("Đã tải lên ảnh bằng chứng giao hàng"));
        assertTrue(html.contains("OTP không đúng"));

        // Native <dialog> modals
        assertTrue(html.contains("<dialog id=\"podModal\""));
        assertTrue(html.contains("closedby=\"any\""));
        assertTrue(html.contains("accept=\"image/*\""));
        assertTrue(html.contains("capture=\"environment\""));
        assertTrue(html.contains("enctype=\"multipart/form-data\""));

        assertTrue(html.contains("<dialog id=\"otpModal\""));
        assertTrue(html.contains("class=\"otp-single-input\""));
        assertTrue(html.contains("inputmode=\"numeric\""));
        assertTrue(html.contains("maxlength=\"6\""));
        assertTrue(html.contains("pattern=\"[0-9]{6}\""));
        assertTrue(html.contains("autocomplete=\"one-time-code\""));
        assertFalse(html.contains("class=\"otp-box"), "Legacy 6-box OTP grid must be replaced by single OTP input");
        assertTrue(html.contains("Gửi lại OTP"));

        assertTrue(html.contains("shipper-danger-zone"));
        assertTrue(html.contains("<dialog id=\"failModal\""));
        assertTrue(html.contains("name=\"reason\""));

        // COD Batch Section & Modal
        assertTrue(html.contains("id=\"codBatchesSection\""));
        assertTrue(html.contains("<dialog id=\"codBatchModal\""));
        assertTrue(html.contains("name=\"shipmentIds\""));
        assertTrue(html.contains("action=\"/shipper/cod-batches\""));
        assertTrue(html.contains("COD-20261114-AAA111"));
        assertTrue(html.contains("PENDING_SETTLEMENT"));
        assertTrue(html.contains("VERIFIED"));
        assertTrue(html.contains("DISPUTED"));
        assertTrue(html.contains("Thiếu 100.000 ₫ so với biên lai"));

        // Manual refresh chip instead of forced reload
        assertTrue(html.contains("Làm mới danh sách"));
    }

    @Test
    void shipperMobileActionsJsEnforcesClassicIifeNoReloadSseReplayAndSafeDom() throws IOException {
        String js = Files.readString(
                Path.of("src", "main", "resources", "static", "js", "shipper-mobile-actions.js"),
                StandardCharsets.UTF_8
        );

        assertTrue(js.contains("\"use strict\";") || js.contains("'use strict';"));
        assertFalse(js.contains("export "), "Classic deferred script must not use ES module exports");
        assertFalse(js.contains("import "), "Classic deferred script must not use ES module imports");
        assertFalse(js.contains("location.reload"), "Shipper JS must NEVER call window.location.reload()");
        assertFalse(js.contains("window.alert"), "Shipper JS must NEVER call blocking window.alert()");
        assertFalse(js.contains("innerHTML"), "Shipper JS must avoid innerHTML to prevent XSS");

        assertTrue(js.contains("/api/live/shipper/"));
        assertTrue(js.contains("/stream"));
        assertTrue(js.contains("lastEventId"));
        assertTrue(js.contains("SwToast"));
        assertTrue(js.contains("podModal"));
        assertTrue(js.contains("otpModal"));
        assertTrue(js.contains("failModal"));
        assertTrue(js.contains("codBatchModal"));
    }
}
