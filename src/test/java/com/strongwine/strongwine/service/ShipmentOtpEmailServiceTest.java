package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.InvalidOtpRecipientException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShipmentOtpEmailServiceTest {

    @Test
    void sendShipmentOtp_prioritizesCheckoutShippingEmailOverUserEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ShipmentOtpEmailService service = new ShipmentOtpEmailService(
                mailSender,
                true,
                "no-reply@strongwine.local",
                "fallback@example.com",
                true,
                1,
                0
        );

        Shipment shipment = createShipment("checkout@example.com", "account@example.com");

        OtpDeliveryResult result = service.sendShipmentOtp(shipment);

        assertThat(result.success()).isTrue();
        assertThat(result.recipient()).isEqualTo("checkout@example.com");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTo()).containsExactly("checkout@example.com");
    }

    @Test
    void sendShipmentOtp_fallsBackToUserEmailWhenShippingEmailMissing() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ShipmentOtpEmailService service = new ShipmentOtpEmailService(
                mailSender,
                true,
                "no-reply@strongwine.local",
                "fallback@example.com",
                true,
                1,
                0
        );

        Shipment shipment = createShipment(null, "account@example.com");

        OtpDeliveryResult result = service.sendShipmentOtp(shipment);

        assertThat(result.success()).isTrue();
        assertThat(result.recipient()).isEqualTo("account@example.com");
    }

    @Test
    void sendShipmentOtp_usesConfiguredFallbackWhenNoShippingOrUserEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ShipmentOtpEmailService service = new ShipmentOtpEmailService(
                mailSender,
                true,
                "no-reply@strongwine.local",
                "fallback@example.com",
                true,
                1,
                0
        );

        Shipment shipment = createShipment(null, null);

        OtpDeliveryResult result = service.sendShipmentOtp(shipment);

        assertThat(result.success()).isTrue();
        assertThat(result.recipient()).isEqualTo("fallback@example.com");
    }

    @Test
    void sendShipmentOtp_throwsWhenNoValidRecipientAvailable() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ShipmentOtpEmailService service = new ShipmentOtpEmailService(
                mailSender,
                true,
                "no-reply@strongwine.local",
                "",
                false,
                1,
                0
        );

        Shipment shipment = createShipment("invalid-email", null);

        assertThatThrownBy(() -> service.sendShipmentOtp(shipment))
                .isInstanceOf(InvalidOtpRecipientException.class)
                .hasMessageContaining("Email người nhận không hợp lệ");
    }

    private Shipment createShipment(String shippingEmail, String userEmail) {
        User user = new User();
        user.setId(100L);
        user.setEmail(userEmail);

        Order order = new Order();
        order.setId(200L);
        order.setUser(user);

        Shipment shipment = new Shipment();
        shipment.setId(300L);
        shipment.setOrder(order);
        shipment.setShippingName("Test Customer");
        shipment.setShippingEmail(shippingEmail);
        shipment.setOtpCode("123456");
        shipment.setOtpExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));

        return shipment;
    }
}
