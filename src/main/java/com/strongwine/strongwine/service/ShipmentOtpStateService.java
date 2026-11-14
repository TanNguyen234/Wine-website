package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Isolated transactional bean so Spring AOP enforces REQUIRES_NEW persistence
 * for failed OTP attempt counters and lockouts even when outer verification rolls back.
 */
@Service
public class ShipmentOtpStateService {

    public static final int OTP_MAX_ATTEMPTS = 5;
    public static final int OTP_LOCK_MINUTES = 15;

    private final ShipmentRepository shipmentRepository;
    private final ShipmentOtpAuditService shipmentOtpAuditService;

    @Autowired
    public ShipmentOtpStateService(ShipmentRepository shipmentRepository,
                                   @Autowired(required = false) ShipmentOtpAuditService shipmentOtpAuditService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentOtpAuditService = shipmentOtpAuditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Shipment recordFailedOtpAttempt(Long shipmentId) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Thiếu mã đơn giao hàng");
        }

        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .or(() -> shipmentRepository.findById(shipmentId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giao hàng: " + shipmentId));

        LocalDateTime now = LocalDateTime.now();
        int currentAttempts = shipment.getOtpAttemptCount() == null ? 0 : shipment.getOtpAttemptCount();
        int nextAttempts = currentAttempts + 1;
        shipment.setOtpAttemptCount(nextAttempts);
        shipment.setUpdatedAt(now);
        if (nextAttempts >= OTP_MAX_ATTEMPTS) {
            shipment.setOtpLockedUntil(now.plusMinutes(OTP_LOCK_MINUTES));
        }

        Shipment saved = shipmentRepository.save(shipment);
        if (shipmentOtpAuditService != null) {
            shipmentOtpAuditService.log(
                    saved,
                    "OTP_VERIFY",
                    "FAILED",
                    "OTP_MISMATCH",
                    "attempts=" + nextAttempts,
                    null
            );
        }
        return saved;
    }
}
