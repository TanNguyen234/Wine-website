package com.strongwine.strongwine.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.service.EnumDisplayService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3DesignSystemVerificationTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");
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
        resolver.setCheckExistence(true);
        resolver.setOrder(1);

        StringTemplateResolver stringResolver = new StringTemplateResolver();
        stringResolver.setTemplateMode(TemplateMode.HTML);
        stringResolver.setCacheable(false);
        stringResolver.setOrder(2);

        templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(resolver);
        templateEngine.addTemplateResolver(stringResolver);
        templateEngine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(org.thymeleaf.context.IExpressionContext context,
                                                String base,
                                                Map<String, Object> parameters) {
                return "";
            }
        });
    }

    private static Context newContext() {
        Context ctx = new Context(Locale.forLanguageTag("vi-VN"));
        ctx.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(applicationContext, null)
        );
        return ctx;
    }

    private static String renderFragment(String fragmentExpr, Context ctx) {
        return templateEngine.process("<th:block th:replace=\"~{" + fragmentExpr + "}\"/>", ctx);
    }

    private static String readStatic(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void tokensAndPersonaCssDeclareLayersDualThemesAndAccessibilityContracts() throws IOException {
        String tokens = readStatic("css/tokens.css");
        assertTrue(tokens.contains("@layer reset, tokens, base, layout, components, utilities, themes;"));
        assertFalse(tokens.contains("* {"), "tokens.css must not declare a global universal * reset");
        assertTrue(tokens.contains(":root"));
        assertTrue(tokens.contains("color-scheme: dark light;"));
        assertTrue(tokens.contains("[data-theme=\"cellar\"]"));
        assertTrue(tokens.contains("[data-theme=\"ivory\"]"));
        assertTrue(tokens.contains("--surface-canvas: #0c0d10;"));
        assertTrue(tokens.contains("--accent-crimson: #6f1d2b;"));
        assertTrue(tokens.contains("--accent-gold: #b5a68e;"));
        assertTrue(tokens.contains("--sw-bordeaux"));
        assertTrue(tokens.contains("--sw-champagne"));
        assertTrue(tokens.contains("@view-transition"));
        assertTrue(tokens.contains("prefers-reduced-motion"));
        assertTrue(tokens.contains(":focus-visible"));
        assertTrue(tokens.contains("tabular-nums"));

        String storefront = readStatic("css/storefront.css");
        assertTrue(storefront.contains("@layer layout, components, utilities;"));
        assertTrue(storefront.contains("@container"));
        assertTrue(storefront.contains("@starting-style"));
        assertTrue(storefront.contains("overscroll-behavior: contain"));
        String storefrontWithoutComments = storefront.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        assertFalse(storefrontWithoutComments.contains("mix-blend-mode: multiply"));

        String admin = readStatic("css/admin.css");
        assertTrue(admin.contains("@layer layout, components, utilities;"));
        assertTrue(admin.contains(".admin-shell"));
        assertTrue(admin.contains("@container admin-table (max-width: 639px)"));
        assertTrue(admin.contains(".diff-badge"));
        assertFalse(admin.contains("!important"), "admin.css should not fight Bootstrap with !important");

        String shipper = readStatic("css/shipper.css");
        assertTrue(shipper.contains("@layer layout, components, utilities;"));
        assertTrue(shipper.contains("100dvh"));
        assertTrue(shipper.contains("env(safe-area-inset-bottom)"));
        assertTrue(shipper.contains("min-height: 48px"));
        assertTrue(shipper.contains(".otp-single-input"));
        assertTrue(shipper.contains("touch-action: manipulation"));
    }

    @Test
    void pwaManifestServiceWorkerAndJsKitsEnforceSecurityAndProgressiveContracts() throws IOException {
        String manifestRaw = readStatic("manifest.webmanifest");
        JsonNode manifest = new ObjectMapper().readTree(manifestRaw);
        assertEquals("/shipper/dashboard", manifest.get("start_url").asText());
        assertEquals("standalone", manifest.get("display").asText());
        assertEquals("#0c0d10", manifest.get("theme_color").asText());
        assertEquals("#0c0d10", manifest.get("background_color").asText());
        assertEquals("/favicon.svg", manifest.get("icons").get(0).get("src").asText());

        String sw = readStatic("sw-shipper.js");
        assertTrue(sw.contains("/css/tokens.css"));
        assertTrue(sw.contains("/css/shipper.css"));
        assertTrue(sw.contains("/manifest.webmanifest"));
        assertTrue(sw.contains("pathname.indexOf(\"/api/\") === 0"));
        assertTrue(sw.contains("pathname.indexOf(\"/shipper/\") === 0"));
        assertTrue(sw.contains("request.method !== \"GET\""));
        assertTrue(sw.contains("request.mode === \"navigate\""));

        String themeJs = readStatic("js/theme-toggle.js");
        assertTrue(themeJs.contains("sw_theme"));
        assertTrue(themeJs.contains("aria-pressed"));
        assertTrue(themeJs.contains("[data-theme-toggle]"));
        assertFalse(themeJs.contains("export "), "Classic script must not use bare ES module exports");

        String dialogJs = readStatic("js/dialog-kit.js");
        assertTrue(dialogJs.contains("showModal"));
        assertTrue(dialogJs.contains("[data-dialog-open]"));
        assertTrue(dialogJs.contains("[data-dialog-close]"));
        assertTrue(dialogJs.contains("window.SwDialog"));
        assertTrue(dialogJs.contains("window.SwToast"));
        assertFalse(dialogJs.contains("innerHTML"), "Toast rendering must use textContent to avoid XSS");
        assertFalse(dialogJs.contains("window.alert"));
    }

    @Test
    void iconsFragmentRendersAll19BespokeWineIconsAndSprite() {
        List<String> iconNames = List.of(
                "wine-glass", "decanter", "oak-barrel", "wax-seal", "grape-cluster",
                "verified-bottle", "warehouse-map", "cod-wallet", "bell", "heart",
                "sun", "moon", "search", "cart", "menu", "close", "truck", "shield", "check"
        );

        String spriteHtml = renderFragment("fragments/icons :: sprite", newContext());
        for (String name : iconNames) {
            assertTrue(spriteHtml.contains("id=\"sw-icon-" + name + "\""), "Missing sprite symbol for " + name);

            Context ctx = newContext();
            String rendered = renderFragment("fragments/icons :: icon('" + name + "')", ctx);
            assertTrue(rendered.contains("sw-icon--" + name), "Failed to render icon " + name);
            assertTrue(rendered.contains("stroke-width=\"1.5\""), "Expected 1.5px stroke on icon " + name);
        }
    }

    @Test
    void storeShellFragmentRendersDesignSystemAndPreservesLandmarks() {
        Context ctx = newContext();
        ctx.setVariable("cartCount", 3);
        ctx.setVariable("wishlistCount", 2);
        ctx.setVariable("unreadNotificationCount", 5);

        String headHtml = renderFragment("fragments/store-shell :: head('Hầm rượu')", ctx);
        assertTrue(headHtml.contains("/favicon.svg"));
        assertTrue(headHtml.contains("/css/tokens.css"));
        assertTrue(headHtml.contains("/css/storefront.css"));
        assertFalse(headHtml.contains("bootstrap"), "Storefront shell must not load Bootstrap");
        assertFalse(headHtml.contains("font-awesome"), "Storefront shell must not load FontAwesome");

        String headerHtml = renderFragment("fragments/store-shell :: header('traditional')", ctx);
        assertTrue(headerHtml.contains("id=\"siteNav\""));
        assertTrue(headerHtml.contains("/wines?type=Traditional"));
        assertTrue(headerHtml.contains("Rượu truyền thống"));
        assertTrue(headerHtml.contains("data-theme-toggle"));
        assertTrue(headerHtml.contains("js-open-cart-drawer"));
        assertTrue(headerHtml.contains("js-cart-count"));
        assertTrue(headerHtml.contains("id=\"notifBellMenu\""));
        assertTrue(headerHtml.contains("popover=\"auto\""));

        String bottleHtml = renderFragment("fragments/store-shell :: bottleSvg('wine-svg')", ctx);
        assertTrue(bottleHtml.contains("<svg"));

        String footerHtml = renderFragment("fragments/store-shell :: footer", ctx);
        assertTrue(footerHtml.contains("<dialog id=\"cartDrawer\""));
        assertTrue(footerHtml.contains("id=\"cartDrawerItems\""));
        assertTrue(footerHtml.contains("id=\"cartDrawerSubtotal\""));
        assertTrue(footerHtml.contains("id=\"aiToggle\""));
        assertTrue(footerHtml.contains("<dialog id=\"aiPanel\""));
        assertTrue(footerHtml.contains("id=\"aiClose\""));
        assertTrue(footerHtml.contains("id=\"aiFeed\""));
        assertTrue(footerHtml.contains("id=\"aiForm\""));
        assertTrue(footerHtml.contains("id=\"aiPrompt\""));
        assertTrue(footerHtml.contains("id=\"swToastRegion\""));
        assertTrue(footerHtml.contains("popover=\"manual\""));
        assertTrue(footerHtml.contains("/js/theme-toggle.js"));
        assertTrue(footerHtml.contains("/js/dialog-kit.js"));
        assertTrue(footerHtml.contains("/js/notifications-client.js"));
        assertTrue(footerHtml.contains("/js/wishlist.js"));
    }

    @Test
    void adminAndShipperShellsRenderWithExpectedRoutesAndNoSpelErrors() {
        Context ctx = newContext();
        ctx.setVariable("unreadNotificationCount", 4);

        String adminHead = renderFragment("fragments/admin-shell :: head('Tổng quan')", ctx);
        assertTrue(adminHead.contains("/favicon.svg"));
        assertTrue(adminHead.contains("/css/tokens.css"));
        assertTrue(adminHead.contains("/css/admin.css"));
        assertFalse(adminHead.contains("bootstrap.min.css"), "Admin shell must not load unlayered Bootstrap CSS");

        String adminSidebar = renderFragment("fragments/admin-shell :: sidebar('cod-batches')", ctx);
        assertTrue(adminSidebar.contains("/admin/wines/create"));
        assertTrue(adminSidebar.contains("/admin/wines"));
        assertTrue(adminSidebar.contains("/admin/vouchers"));
        assertTrue(adminSidebar.contains("/admin/returns"));
        assertTrue(adminSidebar.contains("/admin/shippers/cod-batches"));
        assertTrue(adminSidebar.contains("/admin/audit-logs"));

        String adminTopbar = renderFragment("fragments/admin-shell :: topbar", ctx);
        assertTrue(adminTopbar.contains("id=\"adminLiveBadge\""));
        assertTrue(adminTopbar.contains("live-pulse-dot"));
        assertTrue(adminTopbar.contains("id=\"adminNotifBellMenu\""));
        assertTrue(adminTopbar.contains("id=\"adminLiveToast\""));
        assertTrue(adminTopbar.contains("data-theme-toggle"));

        String adminScripts = renderFragment("fragments/admin-shell :: scripts", ctx);
        assertTrue(adminScripts.contains("/js/theme-toggle.js"));
        assertTrue(adminScripts.contains("/js/dialog-kit.js"));
        assertTrue(adminScripts.contains("/js/admin-live-notifications.js"));

        ctx.setVariable("shipperId", 9L);
        String shipperHead = renderFragment("fragments/shipper-shell :: head('Bảng điều phối')", ctx);
        assertTrue(shipperHead.contains("/favicon.svg"));
        assertTrue(shipperHead.contains("/manifest.webmanifest"));
        assertTrue(shipperHead.contains("/css/tokens.css"));
        assertTrue(shipperHead.contains("/css/shipper.css"));
        assertTrue(shipperHead.contains("name=\"shipper-id\" content=\"9\""));
        assertFalse(shipperHead.contains("bootstrap"), "Shipper shell must not load Bootstrap");
        assertFalse(shipperHead.contains("font-awesome"), "Shipper shell must not load FontAwesome");

        String shipperHeader = renderFragment("fragments/shipper-shell :: header('Nguyễn Văn An')", ctx);
        assertTrue(shipperHeader.contains("Nguyễn Văn An"));
        assertTrue(shipperHeader.contains("data-theme-toggle"));

        String shipperBottomNav = renderFragment("fragments/shipper-shell :: bottomNav('dashboard')", ctx);
        assertTrue(shipperBottomNav.contains("/shipper/dashboard"));
        assertTrue(shipperBottomNav.contains("/shipper/cod-batches"));

        String shipperScripts = renderFragment("fragments/shipper-shell :: scripts", ctx);
        assertTrue(shipperScripts.contains("/sw-shipper.js"));
        assertTrue(shipperScripts.contains("/js/theme-toggle.js"));
        assertTrue(shipperScripts.contains("/js/dialog-kit.js"));
    }

    @Test
    void paginationFragmentRendersNullSafelyAndAvoidsDuplicateQueryParams() {
        Context withoutParamCtx = newContext();
        withoutParamCtx.setVariable("currentPage", 1);
        withoutParamCtx.setVariable("pageSize", 12);
        withoutParamCtx.setVariable("totalEntries", 36L);
        withoutParamCtx.setVariable("totalPages", 3);
        withoutParamCtx.setVariable("hasPrevious", true);
        withoutParamCtx.setVariable("hasNext", true);

        String basicHtml = renderFragment("fragments/pagination :: pagination('/wines')", withoutParamCtx);
        assertTrue(basicHtml.contains("aria-label=\"Phân trang\""));
        assertTrue(basicHtml.contains("/wines?page=0"));
        assertTrue(basicHtml.contains("aria-current=\"page\""));

        Context withStatusCtx = newContext();
        withStatusCtx.setVariable("currentPage", 0);
        withStatusCtx.setVariable("pageSize", 20);
        withStatusCtx.setVariable("totalEntries", 45L);
        withStatusCtx.setVariable("totalPages", 3);
        withStatusCtx.setVariable("hasPrevious", false);
        withStatusCtx.setVariable("hasNext", true);
        withStatusCtx.setVariable("param", Map.of("status", new String[]{"PAID"}, "keyword", new String[]{"SW"}));

        String ordersHtml = renderFragment(
                "fragments/pagination :: pagination('/admin/orders?status=PAID')",
                withStatusCtx
        );
        assertTrue(ordersHtml.contains("status=PAID"));
        assertFalse(ordersHtml.contains("status=PAID&amp;status=PAID"), "Must not duplicate status query param");
        assertTrue(ordersHtml.contains("keyword=SW"));
    }
}
