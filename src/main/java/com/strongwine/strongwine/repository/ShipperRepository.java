package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperRepository extends JpaRepository<Shipper, Long> {
    List<Shipper> findAllByOrderByCreatedAtDesc();
    List<Shipper> findAllByOrderByNameAsc();
    Optional<Shipper> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByUserIdAndIdNot(Long userId, Long id);
    List<Shipper> findByStatusOrderByNameAsc(ShipperStatus status);
    List<Shipper> findByStatusAndIsAvailableTrueOrderByNameAsc(ShipperStatus status);
    long countByStatus(ShipperStatus status);
    long countByIsAvailableTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Shipper> findFirstByStatusAndIsAvailableTrueOrderByIdAsc(ShipperStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipper s WHERE s.id = :shipperId")
    Optional<Shipper> findByIdForUpdate(@Param("shipperId") Long shipperId);

    Optional<Shipper> findFirstByStatusOrderByIdAsc(ShipperStatus status);
}

