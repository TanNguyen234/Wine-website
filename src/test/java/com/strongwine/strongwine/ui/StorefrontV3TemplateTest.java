package com.strongwine.strongwine.ui;

import com.strongwine.strongwine.dto.CartItemDto;
import com.strongwine.strongwine.dto.CheckoutForm;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.service.EnumDisplayService;
import com.strongwine.strongwine.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class StorefrontV3TemplateTest {

    private static final Path TEMPLATES_ROOT = Path.of("src", "main", "resources", "templates");
    private static final Path JS_ROOT = Path.of("src", "main", "resources", "static", "js");

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
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
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

    private Map<String, Object> buildBaseStorefrontModel() {
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

        Wine tradWine = new Wine();
        tradWine.setId(2L);
        tradWine.setName("Rượu Nếp Cái Hoa Vàng Ủ Chum");
        tradWine.setType("Traditional");
        tradWine.setCountry("Việt Nam");
        tradWine.setYear(2024);
        tradWine.setPrice(new BigDecimal("490000"));
        tradWine.setDescription("Ủ chum sành truyền thống thơm dịu.");
        tradWine.setImageUrl("");

        Order order = new Order(customer, new BigDecimal("2850000"));
        order.setId(20L);
        order.setSubtotalAmount(new BigDecimal("2850000"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
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
        shipment.setPickedUpAt(LocalDateTime.now().minusMinutes(30));

        Payment payment = new Payment();
        payment.setId(70L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalPrice());
        payment.setMethod(PaymentMethod.STRIPE);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentReference("pi_test_98765");
        payment.setCreatedAt(LocalDateTime.now());

        Review review = new Review(wine1, customer, 5, "Cấu trúc tannin mượt mà, hậu vị rất sâu.");
        review.setId(90L);
        review.setCreatedAt(LocalDateTime.now());

        UserAddress address = new UserAddress();
        address.setId(12L);
        address.setUser(customer);
        address.setLabel("Nhà riêng");
        address.setRecipientName("Nguyễn Văn A");
        address.setPhone("0901234567");
        address.setAddressLine("123 Đồng Khởi");
        address.setWard("Phường Bến Nghé");
        address.setDistrict("Quận 1");
        address.setProvince("TP. Hồ Chí Minh");
        address.setIsDefault(true);

        CartItemDto cartItem = new CartItemDto();
        cartItem.setWine(wine1);
        cartItem.setQuantity(1);
        cartItem.setAvailableStock(22);
        cartItem.setLineTotal(wine1.getPrice());

        CheckoutForm checkoutForm = new CheckoutForm();
        checkoutForm.setEmail("demo@strongwine.vn");

        Map<String, Object> model = new HashMap<>();
        model.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
        model.put("isAdmin", false);
        model.put("user", customer);
        model.put("cartCount", 1);
        model.put("cartItems", List.of(cartItem));
        model.put("cartTotal", wine1.getPrice());
        model.put("totalPrice", wine1.getPrice());
        model.put("featuredWines", List.of(wine1, tradWine));
        model.put("traditionalWines", List.of(tradWine));
        model.put("wines", List.of(wine1, tradWine));
        model.put("wine", wine1);
        model.put("availableStock", 22);
        model.put("availableStockByWineId", Map.of(1L, 22, 2L, 8));
        model.put("countries", List.of("Pháp", "Việt Nam", "Ý"));
        model.put("reviews", List.of(review));
        model.put("currentPage", 0);
        model.put("totalPages", 2);
        model.put("pageSize", 9);
        model.put("totalItems", 2);
        model.put("sortBy", "createdAt");
        model.put("sortDir", "desc");
        model.put("checkoutForm", checkoutForm);
        model.put("checkoutToken", "tok_test_001");
        model.put("paymentMethods", List.of("STRIPE", "COD"));
        model.put("savedAddresses", List.of(address));
        model.put("defaultAddress", address);
        model.put("latestFullName", address.getRecipientName());
        model.put("latestPhone", address.getPhone());
        model.put("latestAddress", "123 Đồng Khởi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh");
        model.put("orders", List.of(order));
        model.put("totalOrders", 1L);
        model.put("totalSpent", order.getTotalPrice());
        model.put("order", order);
        model.put("shipment", shipment);
        model.put("shipmentByOrderId", Map.of(20L, shipment));
        model.put("orderId", 20L);
        model.put("fullName", order.getShippingFullName());
        model.put("phone", order.getShippingPhone());
        model.put("address", order.getShippingAddress());
        model.put("orderStatus", order.getStatus());
        model.put("paymentStatus", order.getPaymentStatus());
        model.put("payment", payment);
        model.put("successUrl", "/orders/20?paid=true");
        model.put("failedUrl", "/orders/20?paid=false");
        model.put("cancelUrl", "/orders/20");
        model.put("token", "reset_tok_123");
        model.put("param", Map.of());
        return model;
    }

    @Test
    void rendersAll14StorefrontAndAuthTemplatesWithoutCdnBloatOrFakeCopy() {
        List<String> templates = List.of(
                "home",
                "wine-list",
                "wine-details",
                "cart",
                "checkout",
                "orders",
                "order-detail",
                "order-confirmation",
                "payment-session",
                "user-profile",
                "login",
                "register",
                "forgot-password",
                "reset-password"
        );

        Map<String, Object> model = buildBaseStorefrontModel();
        List<String> forbiddenSnippets = List.of(
                "cdn.jsdelivr.net",
                "stackpath.bootstrapcdn",
                "bootstrap.min",
                "font-awesome",
                "fontawesome",
                "4.9/5",
                "17 đánh giá",
                "82%",
                "16+",
                "thành viên VIP",
                "VIP"
        );

        for (String template : templates) {
            String html = assertDoesNotThrow(
                    () -> templateEngine.process(template, createContext(model)),
                    "Failed rendering template: " + template
            );
            assertNotNull(html);
            assertTrue(html.contains("/css/tokens.css"), template + " must include V3 tokens.css");
            for (String forbidden : forbiddenSnippets) {
                assertFalse(html.contains(forbidden),
                        "Template " + template + " must not contain forbidden snippet: " + forbidden);
            }
            assertFalse(html.contains("class=\"fa-") || html.contains("fas fa-") || html.contains("far fa-"),
                    "Template " + template + " must not contain FontAwesome classes");
        }

        for (String authTemplate : List.of("login", "register", "forgot-password", "reset-password")) {
            String html = templateEngine.process(authTemplate, createContext(model));
            assertTrue(html.contains("auth-layout"), authTemplate + " must keep auth-layout landmark");
            assertTrue(html.contains("name=\"_csrf\""), authTemplate + " must include hidden CSRF input");
        }

        String loginHtml = templateEngine.process("login", createContext(model));
        assertTrue(loginHtml.contains("id=\"username\"") && loginHtml.contains("autocomplete=\"username\""));
        assertTrue(loginHtml.contains("id=\"password\"") && loginHtml.contains("autocomplete=\"current-password\""));
    }

    @Test
    void homeAndWineListPreserveTraditionalCatalogAndFilterHierarchy() {
        Map<String, Object> model = buildBaseStorefrontModel();

        String homeHtml = templateEngine.process("home", createContext(model));
        assertTrue(homeHtml.contains("hero"));
        assertTrue(homeHtml.contains("/wines?type=Traditional"));
        assertTrue(homeHtml.contains("Rượu truyền thống"));
        assertTrue(homeHtml.contains("data-wishlist-toggle"));

        String listHtml = templateEngine.process("wine-list", createContext(model));
        int asideStart = listHtml.indexOf("<aside class=\"filter-panel\"");
        int asideEnd = listHtml.indexOf("</aside>", asideStart);
        int filterFormPos = listHtml.indexOf("<form id=\"filterForm\"");
        assertTrue(asideStart >= 0 && asideEnd > asideStart, "wine-list must have <aside class=\"filter-panel\">");
        assertTrue(filterFormPos > asideStart && filterFormPos < asideEnd,
                "filterForm must reside inside <aside class=\"filter-panel\">");

        int filterFormClose = listHtml.indexOf("</form>", filterFormPos);
        String insideFilterForm = listHtml.substring(filterFormPos, filterFormClose);
        assertFalse(insideFilterForm.contains("/wines/admin/delete/"),
                "Admin delete form must never be nested inside filterForm");

        assertTrue(listHtml.contains("value=\"Traditional\""));
        assertTrue(listHtml.contains("<dialog id=\"quickViewModal\""));
        assertTrue(listHtml.contains("data-wishlist-toggle"));
        assertTrue(listHtml.contains("/js/wine-filters.js"));
        assertTrue(listHtml.contains("/js/wine-filter-drawer.js"));
    }

    @Test
    void wineDetailsComputesDynamicRatingsAndBottleFallbackSafely() {
        Map<String, Object> emptyReviewModel = buildBaseStorefrontModel();
        Wine blankImageWine = new Wine();
        blankImageWine.setId(2L);
        blankImageWine.setName("Rượu Nếp Cái Hoa Vàng");
        blankImageWine.setType("Traditional");
        blankImageWine.setCountry("Việt Nam");
        blankImageWine.setPrice(new BigDecimal("490000"));
        blankImageWine.setImageUrl("");
        emptyReviewModel.put("wine", blankImageWine);
        emptyReviewModel.put("reviews", List.of());

        String emptyHtml = templateEngine.process("wine-details", createContext(emptyReviewModel));
        assertTrue(emptyHtml.contains("detail-grid"));
        assertTrue(emptyHtml.contains("0.0"));
        assertTrue(emptyHtml.contains("Chưa có đánh giá"));
        assertTrue(emptyHtml.contains("<svg"), "Blank wine image should render bottleSvg fallback");
        assertTrue(emptyHtml.contains("data-wishlist-toggle"));
        assertTrue(emptyHtml.contains("/cart/checkout"));

        Map<String, Object> reviewedModel = buildBaseStorefrontModel();
        reviewedModel.put("success", "Cảm ơn bạn đã gửi đánh giá!");
        String reviewedHtml = templateEngine.process("wine-details", createContext(reviewedModel));
        assertTrue(reviewedHtml.contains("Cảm ơn bạn đã gửi đánh giá!"));
        assertTrue(reviewedHtml.contains("5.0"));
        assertTrue(reviewedHtml.contains("100%"));
        assertTrue(reviewedHtml.contains("id=\"editReviewModal\""));
        assertTrue(reviewedHtml.contains("/reviews/create"));
    }

    @Test
    void cartAndCheckoutPreserveRequiredLandmarksAndQuoteControls() {
        Map<String, Object> model = buildBaseStorefrontModel();
        model.put("reorderItems", List.of(
                new OrderService.ReorderItemSnapshot(1L, "Château Margaux Grand Cru", new BigDecimal("2850000"), 2, "/uploads/margaux.jpg", 22)
        ));

        String cartHtml = templateEngine.process("cart", createContext(model));
        assertTrue(cartHtml.contains("data-cart-page=\"true\""));
        assertTrue(cartHtml.contains("data-reorder-item=\"true\""));
        assertTrue(cartHtml.contains("cartVoucherCode"));
        assertTrue(cartHtml.contains("/cart/checkout"));

        String checkoutHtml = templateEngine.process("checkout", createContext(model));
        assertTrue(checkoutHtml.contains("checkoutForm"));
        assertTrue(checkoutHtml.contains("<strong id=\"selectedMapLocation\">Chưa chọn địa điểm giao hàng</strong>"));
        assertFalse(checkoutHtml.contains("10.7769, 106.7009"),
                "checkout initial HTML must not fabricate default selected coordinates");
        assertTrue(checkoutHtml.contains("savedAddressId"));
        assertTrue(checkoutHtml.contains("voucherCode"));
        assertTrue(checkoutHtml.contains("checkoutSubtotal"));
        assertTrue(checkoutHtml.contains("checkoutShippingFee"));
        assertTrue(checkoutHtml.contains("checkoutTotal"));
    }

    @Test
    void ordersAndOrderDetailSupportInvoicesReorderReturnsAndCustomerOtpIsolation() throws IOException {
        Map<String, Object> model = buildBaseStorefrontModel();

        String ordersHtml = templateEngine.process("orders", createContext(model));
        assertTrue(ordersHtml.contains("Đơn hàng #20"));
        assertTrue(ordersHtml.contains("/orders/20/reorder"));
        assertTrue(ordersHtml.contains("/orders/20/invoice?format=pdf"));
        assertTrue(ordersHtml.contains("/orders/20/invoice?format=xlsx"));
        assertTrue(ordersHtml.contains("id=\"cancelOrderModal\""));
        assertFalse(ordersHtml.contains("return confirm("));

        String rawOrderDetail = Files.readString(TEMPLATES_ROOT.resolve("order-detail.html"), StandardCharsets.UTF_8);
        assertTrue(rawOrderDetail.contains("!isAdmin and shipment != null"));

        Map<String, Object> adminView = new HashMap<>(model);
        adminView.put("isAdmin", true);
        adminView.put("decryptedOtp", "654321");
        String adminHtml = templateEngine.process("order-detail", createContext(adminView));
        assertFalse(adminHtml.contains("id=\"otpVaultCard\""));
        assertFalse(adminHtml.contains("654321"));

        Map<String, Object> customerDecryptedView = new HashMap<>(model);
        customerDecryptedView.put("isAdmin", false);
        customerDecryptedView.put("decryptedOtp", "654321");
        String customerDecryptedHtml = templateEngine.process("order-detail", createContext(customerDecryptedView));
        assertTrue(customerDecryptedHtml.contains("id=\"otpVaultCard\""));
        assertTrue(customerDecryptedHtml.contains("654321"));

        Map<String, Object> fallbackOtpView = new HashMap<>(model);
        fallbackOtpView.put("isAdmin", false);
        String fallbackOtpHtml = templateEngine.process("order-detail", createContext(fallbackOtpView));
        assertTrue(fallbackOtpHtml.contains("482910"));

        Order completedOrder = (Order) model.get("order");
        completedOrder.setStatus(OrderStatus.PAID);
        Shipment completedShipment = (Shipment) model.get("shipment");
        completedShipment.setStatus(ShipmentStatus.COMPLETED);
        String completedHtml = templateEngine.process("order-detail", createContext(model));
        assertTrue(completedHtml.contains("id=\"returnModal\""));
        assertTrue(completedHtml.contains("/orders/20/returns"));
        assertTrue(completedHtml.contains("/orders/20/invoice?format=pdf"));
        assertTrue(completedHtml.contains("/orders/20/invoice?format=xlsx"));
        assertTrue(completedHtml.contains("/orders/20/reorder"));
        assertTrue(completedHtml.contains("orderTimelineStepper"));
        assertTrue(completedHtml.contains("/js/order-tracking-realtime.js"));
        assertTrue(completedHtml.contains("/js/order-tracking-page.js"));
        assertFalse(completedHtml.contains("toast-container"));
        assertFalse(completedHtml.contains("data-bs-dismiss"));
    }

    @Test
    void userProfileRendersFourAccessibleTabsAndHandlesNullStateGracefully() {
        Map<String, Object> fullModel = buildBaseStorefrontModel();
        String fullHtml = templateEngine.process("user-profile", createContext(fullModel));
        assertTrue(fullHtml.contains("Hồ sơ"));
        assertTrue(fullHtml.contains("Sổ địa chỉ"));
        assertTrue(fullHtml.contains("Yêu thích"));
        assertTrue(fullHtml.contains("Thông báo"));
        assertTrue(fullHtml.contains("id=\"wishlistGrid\""));
        assertTrue(fullHtml.contains("id=\"addressModal\""));
        assertTrue(fullHtml.contains("id=\"profileNotificationsList\""));
        assertTrue(fullHtml.contains("Nhà riêng"));
        assertTrue(fullHtml.contains("Nguyễn Văn A"));
        assertFalse(fullHtml.contains("VIP"));

        Map<String, Object> nullModel = new HashMap<>();
        nullModel.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf"));
        nullModel.put("param", Map.of());
        String nullHtml = assertDoesNotThrow(
                () -> templateEngine.process("user-profile", createContext(nullModel)),
                "user-profile must render safely even when optional user/order/address fields are null"
        );
        assertTrue(nullHtml.contains("Tài khoản của tôi"));

        String confirmHtml = templateEngine.process("order-confirmation", createContext(fullModel));
        assertTrue(confirmHtml.contains("data-order-confirmation=\"true\""));

        String payHtml = templateEngine.process("payment-session", createContext(fullModel));
        assertTrue(payHtml.contains("Xác nhận giao dịch"));
    }

    @Test
    void ownedStorefrontScriptsEnforceStrictClassicIifeAndSafeDomApis() throws IOException {
        List<String> scripts = List.of(
                "store-nav.js",
                "notifications-client.js",
                "wishlist.js",
                "ai-assistant.js",
                "cart-client.js",
                "cart-drawer.js",
                "wine-filters.js",
                "wine-filter-drawer.js",
                "checkout-map.js",
                "order-tracking-realtime.js",
                "order-tracking-page.js",
                "wine-details.js"
        );

        Pattern blockingPopupPattern = Pattern.compile("(^|[^\\w$.])(alert|confirm|prompt)\\s*\\(");
        Pattern esmPattern = Pattern.compile("(?m)^\\s*(import\\s+|export\\s+(default|function|const|let|var|\\{))");

        for (String name : scripts) {
            Path path = JS_ROOT.resolve(name);
            assertTrue(Files.exists(path), "Expected storefront script to exist: " + name);
            String source = Files.readString(path, StandardCharsets.UTF_8);

            assertTrue(source.contains("\"use strict\";"), name + " must declare strict mode");
            assertFalse(esmPattern.matcher(source).find(), name + " must stay a classic script (zero ESM import/export)");
            assertFalse(source.contains("innerHTML") || source.contains("outerHTML"),
                    name + " must not assign raw innerHTML/outerHTML");
            assertFalse(source.contains("window.location.reload"),
                    name + " must not hard-reload the page");
            assertFalse(blockingPopupPattern.matcher(source).find(),
                    name + " must not call blocking browser alert/confirm/prompt dialogs");
        }

        String realtimeJs = Files.readString(JS_ROOT.resolve("order-tracking-realtime.js"), StandardCharsets.UTF_8);
        assertTrue(realtimeJs.contains("lastEventId"), "order-tracking-realtime.js must track Last-Event-ID for SSE recovery");

        String notificationsJs = Files.readString(JS_ROOT.resolve("notifications-client.js"), StandardCharsets.UTF_8);
        assertTrue(notificationsJs.contains("lastEventId"), "notifications-client.js must track Last-Event-ID for SSE recovery");

        String checkoutMapJs = Files.readString(JS_ROOT.resolve("checkout-map.js"), StandardCharsets.UTF_8);
        assertTrue(checkoutMapJs.contains("function parseCoordinateQuery(value)"));
        assertTrue(checkoutMapJs.contains("strongwine:location-updated"));
        assertTrue(checkoutMapJs.contains("setMarkerAt(coordinates.lat, coordinates.lng)"));
    }
}
