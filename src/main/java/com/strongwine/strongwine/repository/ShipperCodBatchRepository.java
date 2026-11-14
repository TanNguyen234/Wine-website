package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipperCodBatchRepository extends JpaRepository<ShipperCodBatch, Long> {
    Optional<ShipperCodBatch> findByBatchCode(String batchCode);

    List<ShipperCodBatch> findByShipperIdOrderBySubmittedAtDesc(Long shipperId);

    List<ShipperCodBatch> findByStatusOrderBySubmittedAtDesc(CodBatchStatus status);

    List<ShipperCodBatch> findAllByOrderBySubmittedAtDesc();

    long countByStatus(CodBatchStatus status);
}
