package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutTemplateSelectionStateTest {

    @Test
    void checkoutTemplateDoesNotShowCoordinatesBeforeTheCustomerSelectsAPin() throws IOException {
        try (InputStream templateStream = getClass().getResourceAsStream("/templates/checkout.html")) {
            assertTrue(templateStream != null, "Checkout template should be available on the classpath");
            String template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("<strong id=\"selectedMapLocation\">Chưa chọn địa điểm giao hàng</strong>"));
            assertFalse(template.contains("<strong id=\"selectedMapLocation\">10.7769, 106.7009</strong>"));
        }
    }

    @Test
    void coordinateSearchKeepsTheExactCoordinatesSuppliedByTheCustomer() throws IOException {
        try (InputStream scriptStream = getClass().getResourceAsStream("/static/js/checkout-map.js")) {
            assertTrue(scriptStream != null, "Checkout map script should be available on the classpath");
            String script = new String(scriptStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains("function parseCoordinateQuery(value)"));
            assertTrue(script.contains("const coordinates = parseCoordinateQuery(keyword);"));
            assertTrue(script.contains("setMarkerAt(coordinates.lat, coordinates.lng)"));
        }
    }

    @Test
    void reverseGeocodedAddressReplacesTheMapFallbackButKeepsManualAddress() throws IOException {
        try (InputStream scriptStream = getClass().getResourceAsStream("/static/js/checkout-map.js")) {
            assertTrue(scriptStream != null, "Checkout map script should be available on the classpath");
            String script = new String(scriptStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains("const addressIsMapFallback = currentAddress === \"Vị trí được chọn trên bản đồ\""));
            assertTrue(script.contains("if ((!currentAddress || addressIsMapFallback) && placeName)"));
            assertTrue(script.contains("else if (!currentAddress)"));
        }
    }
}
