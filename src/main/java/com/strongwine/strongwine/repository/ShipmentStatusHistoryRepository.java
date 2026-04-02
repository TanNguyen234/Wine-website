package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.ShipmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, Long> {
    List<ShipmentStatusHistory> findTop100ByShipmentIdOrderByCreatedAtDesc(Long shipmentId);
}
