package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentOtpAuditLog;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.ShipmentOtpAuditLogRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ShipmentOtpAuditService {

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
        audit.setReason(limit(reason, 1000));
        audit.setMetadata(limit(metadata, 2000));
        audit.setActorUsername(limit(actorUsername, 255));

        if (actorUsername != null && !actorUsername.isBlank()) {
            User actor = userRepository.findByUsername(actorUsername.trim()).orElse(null);
            if (actor != null) {
                audit.setActorUserId(actor.getId());
            }
        }

        auditLogRepository.save(audit);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
