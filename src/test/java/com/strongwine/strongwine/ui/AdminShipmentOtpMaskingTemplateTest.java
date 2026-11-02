package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminShipmentOtpMaskingTemplateTest {

    @Test
    void adminShipmentListShowsDeliveryStateWithoutRenderingTheOtpSecret() throws IOException {
        try (InputStream templateStream = getClass().getResourceAsStream("/templates/admin-shipments.html")) {
            assertTrue(templateStream != null, "Admin shipment template should be available on the classpath");
            String template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(template.contains("s.otpCode"), "Admin shipment list must not render OTP codes");
            assertTrue(template.contains("s.otpDeliveryStatus"), "Admin shipment list should show delivery state");
            assertTrue(template.contains("s.status.name() == 'COMPLETED' and s.otpVerified"),
                    "Completed OTP deliveries should be identified from the verified shipment state");
            assertTrue(template.contains("Đã xác thực"),
                    "Admin shipment list should show successful OTP verification after completion");
            assertTrue(template.contains("Hoàn tất thủ công"),
                    "Admin shipment list should distinguish admin completion without OTP verification");
        }
    }
}
