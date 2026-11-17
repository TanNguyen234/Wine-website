package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions found by the 2026-11-17 live QA (headed browser, DB-checked):
 * BUG-SW-01  cancel dialog not rendered for PENDING_ASSIGNMENT orders (dialog th:if still required shipment == null).
 * BUG-SW-02  Excel preview read validCount/errorCount, backend returns validRowsCount/errorRowsCount -> 0 valid, import blocked.
 * LAYOUT-1   hero bottle img collapsed to 0x0 (no explicit height) -> bottle invisible on home.
 * LAYOUT-2   admin tables not wrapped -> wide tables pushed content past viewport (right side clipped).
 * LAYOUT-3   mobile header actions / shipper filter chips overflowed the 390px viewport.
 */
class LiveQa20261117RegressionTest {

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void cancelDialogRendersForPendingAssignmentLikeTheDetailPanel() throws IOException {
        String html = read("src/main/resources/templates/order-detail.html");
        int dialog = html.indexOf("<dialog id=\"cancelOrderModal\"");
        assertTrue(dialog >= 0, "cancel dialog must exist");
        String dialogTag = html.substring(dialog, html.indexOf('>', dialog) + 1);
        assertTrue(dialogTag.contains("shipment.status.name() == 'PENDING_ASSIGNMENT'"),
                "dialog th:if must accept PENDING_ASSIGNMENT shipments (BUG-SW-01)");
    }

    @Test
    void excelPreviewReadsBackendCounterNames() throws IOException {
        String js = read("src/main/resources/static/js/admin-excel-import.js");
        assertTrue(js.contains("data.validRowsCount"), "preview must read validRowsCount (BUG-SW-02)");
        assertTrue(js.contains("data.errorRowsCount"), "preview must read errorRowsCount (BUG-SW-02)");
        assertTrue(!js.contains("data.validCount"), "preview must not read the non-existent validCount");
    }

    @Test
    void heroBottleImageHasExplicitHeight() throws IOException {
        String home = read("src/main/resources/templates/home.html");
        assertTrue(home.contains("style=\"height:400px;max-width:160px;aspect-ratio:150/400;width:auto;"),
                "hero bottle img needs explicit height, otherwise 0x0 for SVG without intrinsic size (LAYOUT-1)");
    }

    @Test
    void adminTablesScrollInsideTheirOwnBox() throws IOException {
        String css = read("src/main/resources/static/css/admin.css");
        assertTrue(css.contains(".admin-main table {") && css.contains("overflow-x: auto"),
                "admin tables must scroll inside the page (LAYOUT-2)");
    }

    @Test
    void mobileHeaderWrapsAndShipperChipsWrap() throws IOException {
        String css = read("src/main/resources/static/css/sw-redesign.css");
        assertTrue(css.contains(".header-actions { flex-wrap: wrap;"), "mobile header actions must wrap (LAYOUT-3)");
        String shipper = read("src/main/resources/templates/shipper-dashboard.html");
        assertTrue(shipper.contains(".shipper-filter-row { flex-wrap: wrap;"), "shipper chips must wrap (LAYOUT-3)");
    }
}
