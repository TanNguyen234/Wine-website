package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ShipmentOtpEmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromAddress;
    private final String fallbackRecipient;

    public ShipmentOtpEmailService(@Autowired(required = false) JavaMailSender mailSender,
                                   @Value("${app.mail.enabled:false}") boolean mailEnabled,
                                   @Value("${app.mail.from:no-reply@strongwine.local}") String fromAddress,
                                   @Value("${app.mail.fallback-recipient:}") String fallbackRecipient) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.fallbackRecipient = fallbackRecipient == null ? "" : fallbackRecipient.trim();
    }

    public void sendShipmentOtp(Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            throw new IllegalArgumentException("Shipment is required");
        }
        if (shipment.getOtpCode() == null || shipment.getOtpCode().isBlank()) {
            throw new IllegalStateException("Shipment OTP is missing");
        }
        if (!mailEnabled) {
            throw new IllegalStateException("Email sending is disabled. Set app.mail.enabled=true to send OTP");
        }
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender is not available in current runtime");
        }

        String recipient = resolveRecipient(shipment);
        validateEmail(recipient);

        String customerName = shipment.getShippingName() == null ? "quy khach" : shipment.getShippingName();
        String orderInfo = shipment.getOrder() != null ? String.valueOf(shipment.getOrder().getId()) : "N/A";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("[StrongWine] Ma OTP giao hang #" + shipment.getId());
        message.setText(buildEmailBody(customerName, orderInfo, shipment.getOtpCode()));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new IllegalStateException("Failed to send OTP email: " + ex.getMessage(), ex);
        }
    }

    private String resolveRecipient(Shipment shipment) {
        Order order = shipment.getOrder();
        if (order == null) {
            throw new IllegalStateException("Shipment has no order linked");
        }

        User user = order.getUser();
        String username = user == null ? null : user.getUsername();
        if (username != null && !username.isBlank() && EMAIL_PATTERN.matcher(username.trim()).matches()) {
            return username.trim();
        }

        if (!fallbackRecipient.isBlank() && EMAIL_PATTERN.matcher(fallbackRecipient).matches()) {
            return fallbackRecipient;
        }

        throw new IllegalStateException("Order recipient email is invalid and no fallback recipient configured");
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("Recipient username is not a valid email: " + email);
        }
    }

    private String buildEmailBody(String customerName, String orderInfo, String otpCode) {
        return "Xin chao " + customerName + ",\n\n"
                + "Don hang #" + orderInfo + " da duoc tao shipment.\n"
                + "Ma OTP xac nhan giao hang cua ban la: " + otpCode + "\n\n"
                + "Vui long cung cap ma nay khi shipper giao hang.\n"
                + "Neu ban khong thuc hien yeu cau nay, vui long lien he StrongWine ngay.\n\n"
                + "StrongWine";
    }
}
