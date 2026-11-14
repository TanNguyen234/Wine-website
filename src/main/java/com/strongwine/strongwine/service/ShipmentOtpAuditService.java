package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentOtpAuditLog;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.ShipmentOtpAuditLogRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ShipmentOtpAuditService {

    private static final Pattern OTP_KEYED_SECRET_PATTERN =
            Pattern.compile("(?i)(\\b(?:otp|otp_code|otpcode|code|raw|secret|token|pin|mã(?:\\s*otp)?))([=:\\s]+)(\\d{6}\\b)");

    private final ShipmentOtpAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public ShipmentOtpAuditService(ShipmentOtpAuditLogRepository auditLogRepository,
                                   UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void log(Shipment shipment,
                    String action,
                    String status,
                    String reason,
                    String metadata,
                    String actorUsername) {
        if (shipment == null || shipment.getId() == null) {
            return;
        }

        ShipmentOtpAuditLog audit = new ShipmentOtpAuditLog();
        audit.setShipment(shipment);
        audit.setOrderId(shipment.getOrder() == null ? null : shipment.getOrder().getId());
        audit.setOtpUserId(shipment.getOtpUserId());
        audit.setAction(action);
        audit.setStatus(status);
        audit.setReason(limit(maskSensitiveOtpText(reason, shipment.getOtpCode()), 1000));
        audit.setMetadata(limit(maskSensitiveOtpText(metadata, shipment.getOtpCode()), 2000));
        audit.setActorUsername(limit(actorUsername, 255));

        if (actorUsername != null && !actorUsername.isBlank()) {
            User actor = userRepository.findByUsername(actorUsername.trim()).orElse(null);
            if (actor != null) {
                audit.setActorUserId(actor.getId());
            }
        }

        auditLogRepository.save(audit);
    }

    public static String maskSensitiveOtpText(String value, String knownOtpCode) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = OTP_KEYED_SECRET_PATTERN.matcher(value).replaceAll("$1$2******");
        if (knownOtpCode != null && !knownOtpCode.isBlank()) {
            masked = masked.replace(knownOtpCode.trim(), "******");
        }
        return masked;
    }

    static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
