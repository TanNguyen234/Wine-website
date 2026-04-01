package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.InvalidOtpRecipientException;
import com.strongwine.strongwine.exception.OtpDeliveryException;
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
    private final boolean allowFallbackRecipient;
    private final int retryMaxAttempts;
    private final long retryInitialBackoffMs;

    public ShipmentOtpEmailService(@Autowired(required = false) JavaMailSender mailSender,
                                   @Value("${app.mail.enabled:false}") boolean mailEnabled,
                                   @Value("${app.mail.from:no-reply@strongwine.local}") String fromAddress,
                                   @Value("${app.mail.fallback-recipient:}") String fallbackRecipient,
                                   @Value("${app.mail.allow-fallback-recipient:false}") boolean allowFallbackRecipient,
                                   @Value("${app.mail.retry-max-attempts:3}") int retryMaxAttempts,
                                   @Value("${app.mail.retry-initial-backoff-ms:250}") long retryInitialBackoffMs) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.fallbackRecipient = fallbackRecipient == null ? "" : fallbackRecipient.trim();
        this.allowFallbackRecipient = allowFallbackRecipient;
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryInitialBackoffMs = Math.max(0L, retryInitialBackoffMs);
    }

    public OtpDeliveryResult sendShipmentOtp(Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            throw new OtpDeliveryException("Shipment is required");
        }
        if (shipment.getOtpCode() == null || shipment.getOtpCode().isBlank()) {
            throw new OtpDeliveryException("Shipment OTP is missing");
        }
        if (!mailEnabled) {
            throw new OtpDeliveryException("Email sending is disabled. Set app.mail.enabled=true to send OTP");
        }
        if (mailSender == null) {
            throw new OtpDeliveryException("JavaMailSender is not available in current runtime");
        }

        String recipient = resolveRecipient(shipment);
        validateEmail(recipient);

        String customerName = shipment.getShippingName() == null ? "quy khach" : shipment.getShippingName();
        String orderInfo = shipment.getOrder() != null ? String.valueOf(shipment.getOrder().getId()) : "N/A";
        String expiresAtText = shipment.getOtpExpiresAt() == null ? "10 phut" : shipment.getOtpExpiresAt().toString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("[StrongWine] Ma OTP giao hang #" + shipment.getId());
        message.setText(buildEmailBody(customerName, orderInfo, shipment.getOtpCode(), expiresAtText));

        long backoff = retryInitialBackoffMs;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                mailSender.send(message);
                return OtpDeliveryResult.sent(attempt, recipient);
            } catch (MailException ex) {
                if (attempt >= retryMaxAttempts) {
                    return OtpDeliveryResult.failed(attempt, recipient, ex.getMessage());
                }
                sleepBackoff(backoff);
                backoff = Math.min(backoff * 2L, 4000L);
            }
        }

        return OtpDeliveryResult.failed(retryMaxAttempts, recipient, "Unknown error");
    }

    private String resolveRecipient(Shipment shipment) {
        Order order = shipment.getOrder();
        if (order == null) {
            throw new InvalidOtpRecipientException("Shipment has no order linked");
        }

        User user = order.getUser();
        String userEmail = user == null ? null : user.getEmail();
        if (userEmail != null && !userEmail.isBlank() && EMAIL_PATTERN.matcher(userEmail.trim()).matches()) {
            return userEmail.trim();
        }

        if (allowFallbackRecipient && !fallbackRecipient.isBlank() && EMAIL_PATTERN.matcher(fallbackRecipient).matches()) {
            return fallbackRecipient;
        }

        throw new InvalidOtpRecipientException("Order recipient email is invalid and no fallback recipient configured");
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidOtpRecipientException("Recipient email is invalid: " + email);
        }
    }

    private void sleepBackoff(long backoffMs) {
        if (backoffMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OtpDeliveryException("Interrupted while retrying OTP email", ex);
        }
    }

    private String buildEmailBody(String customerName, String orderInfo, String otpCode, String expiresAtText) {
        return "Xin chao " + customerName + ",\n\n"
                + "Don hang #" + orderInfo + " da duoc tao shipment.\n"
                + "Ma OTP xac nhan giao hang cua ban la: " + otpCode + "\n\n"
                + "Ma OTP co hieu luc den: " + expiresAtText + "\n"
                + "Vui long cung cap ma nay khi shipper giao hang.\n"
                + "Neu ban khong thuc hien yeu cau nay, vui long lien he StrongWine ngay.\n\n"
                + "StrongWine";
    }
}
