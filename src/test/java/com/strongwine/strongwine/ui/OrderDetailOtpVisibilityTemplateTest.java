package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDetailOtpVisibilityTemplateTest {

    @Test
    void orderDetailOtpVaultIsRestrictedToTheCustomer() throws IOException {
        try (InputStream templateStream = getClass().getResourceAsStream("/templates/order-detail.html")) {
            assertTrue(templateStream != null, "Order detail template should be available on the classpath");
            String template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("!isAdmin and shipment != null"),
                    "An administrator must not see the customer's delivery OTP");
        }
    }
}
