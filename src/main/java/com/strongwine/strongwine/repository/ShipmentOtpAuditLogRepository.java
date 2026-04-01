package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.ShipmentOtpAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentOtpAuditLogRepository extends JpaRepository<ShipmentOtpAuditLog, Long> {
    List<ShipmentOtpAuditLog> findTop100ByShipmentIdOrderByCreatedAtDesc(Long shipmentId);
}
