package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    boolean existsByOrderId(Long orderId);
    Optional<Shipment> findByOrderId(Long orderId);

    @Query("""
        SELECT s
        FROM Shipment s
        LEFT JOIN FETCH s.shipper
        WHERE s.order.id = :orderId
        """)
    Optional<Shipment> findByOrderIdWithShipper(@Param("orderId") Long orderId);

    @Query("""
        SELECT s
        FROM Shipment s
        LEFT JOIN FETCH s.shipper
        JOIN FETCH s.order o
        WHERE o.id IN :orderIds
        """)
    List<Shipment> findByOrderIdInWithShipper(@Param("orderIds") List<Long> orderIds);

    @Query("""
        SELECT s
        FROM Shipment s
        LEFT JOIN FETCH s.shipper sh
        LEFT JOIN FETCH sh.user
        JOIN FETCH s.order o
        JOIN FETCH o.user
        ORDER BY s.createdAt DESC
        """)
    List<Shipment> findAllForAdminOrderByCreatedAtDesc();

    @Query("""
        SELECT s
        FROM Shipment s
        LEFT JOIN FETCH s.shipper sh
        LEFT JOIN FETCH sh.user
        JOIN FETCH s.order o
        JOIN FETCH o.user
        WHERE s.id = :shipmentId
        """)
    Optional<Shipment> findByIdForAdmin(@Param("shipmentId") Long shipmentId);

    List<Shipment> findByShipperIdOrderByCreatedAtDesc(Long shipperId);
    long countByStatus(ShipmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :shipmentId")
    Optional<Shipment> findByIdForUpdate(@Param("shipmentId") Long shipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Shipment> findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus status);

    boolean existsByShipperIdAndStatusIn(Long shipperId, List<ShipmentStatus> statuses);

    long countByShipperIdAndStatusIn(Long shipperId, List<ShipmentStatus> statuses);
}
