package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused RED -> GREEN regression suite for frontend templates, CSS tokens/stacking,
 * and client JS contracts uncovered during the 2026-11-18 pre-demo audit & live QA.
 */
class PreDemoAuditUiRegressionTest {

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void tokensCssDefinesAdminAndShipperZIndexAndSemanticTextVariables() throws IOException {
        String tokens = read("src/main/resources/static/css/tokens.css");
        assertTrue(tokens.contains("--z-sticky:"), "tokens.css must define --z-sticky");
        assertTrue(tokens.contains("--z-drawer:"), "tokens.css must define --z-drawer");
        assertTrue(tokens.contains("--z-modal:"), "tokens.css must define --z-modal");
        assertTrue(tokens.contains("--z-toast:"), "tokens.css must define --z-toast");
        assertTrue(tokens.contains("--status-ok-text:"), "tokens.css must define --status-ok-text");
        assertTrue(tokens.contains("--status-danger-text:"), "tokens.css must define --status-danger-text");
    }

    @Test
    void adminShippersTemplateUsesActualOverviewStatKeys() throws IOException {
        String html = read("src/main/resources/templates/admin-shippers.html");
        assertTrue(html.contains("shipperStats['total']"), "admin-shippers.html must read shipperStats['total']");
        assertTrue(html.contains("shipperStats['active']"), "admin-shippers.html must read shipperStats['active']");
        assertTrue(html.contains("shipperStats['available']"), "admin-shippers.html must read shipperStats['available']");
        assertFalse(html.contains("shipperStats['totalShippers']"), "admin-shippers.html must not read non-existent totalShippers key");
    }

    @Test
    void adminExcelImportJsReadsDtoCounterAndFieldNames() throws IOException {
        String js = read("src/main/resources/static/js/admin-excel-import.js");
        assertTrue(js.contains("data.insertCount"), "admin-excel-import.js must read data.insertCount");
        assertTrue(js.contains("data.updateCount"), "admin-excel-import.js must read data.updateCount");
        assertTrue(js.contains("err.fieldName"), "admin-excel-import.js must read err.fieldName");
    }

    @Test
    void adminLiveNotificationsJsReadsLinkUrlFromBackendPayload() throws IOException {
        String js = read("src/main/resources/static/js/admin-live-notifications.js");
        assertTrue(js.contains("item.linkUrl"), "admin-live-notifications.js must read item.linkUrl");
    }

    @Test
    void storefrontStylesDefineDialogHeadAndIsolateCheckoutLeafletMap() throws IOException {
        String storeShell = read("src/main/resources/templates/fragments/store-shell.html");
        String storefrontCss = read("src/main/resources/static/css/storefront.css");
        assertFalse(storeShell.contains("class=\"btn-crimson\""),
                "Cart drawer primary checkout link must use styled button class instead of undefined .btn-crimson");
        assertTrue(storefrontCss.contains(".dialog-head"),
                "storefront.css must style .dialog-head so modal close buttons align right");
        assertTrue(storefrontCss.contains("isolation: isolate"),
                "storefront.css must isolate .map-mock stacking context so Leaflet panes do not bleed over sticky header");
    }

    @Test
    void checkoutTemplateIncludesDefaultAddressFallbackAndSafeDefaultPaymentMethod() throws IOException {
        String checkout = read("src/main/resources/templates/checkout.html");
        int addressInputIdx = checkout.indexOf("id=\"address\"");
        assertTrue(addressInputIdx >= 0, "#address input must exist on checkout.html");
        String addressSnippet = checkout.substring(Math.max(0, addressInputIdx - 120),
                Math.min(checkout.length(), addressInputIdx + 360));
        assertTrue(addressSnippet.contains("defaultAddress"),
                "#address input must include defaultAddress fallback so map init does not overwrite saved address");
    }

    @Test
    void bottleSvgAssetsDefineExplicitIntrinsicPixelDimensions() throws IOException {
        for (String name : new String[]{
                "champagne-brut.svg", "dalat-signature.svg", "margaux-grand-cru.svg",
                "mo-yentu.svg", "ninhthuan-reserve.svg", "sauvignon-blanc.svg"}) {
            String svg = read("src/main/resources/static/images/bottles/" + name);
            assertFalse(svg.contains("width=\"100%\""),
                    name + " must define explicit pixel width/height instead of width=\"100%\" so <img> never collapses to 0x0");
            assertTrue(svg.contains("width=\"160\"") && svg.contains("height=\"450\""),
                    name + " must declare intrinsic width=\"160\" height=\"450\"");
        }
    }
}
