package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Query("SELECT i FROM Inventory i WHERE i.wine.deleted = false")
    List<Inventory> findAllForActiveWines();

    @Query("SELECT i FROM Inventory i WHERE i.wine.deleted = false")
    Page<Inventory> findAllForActiveWines(Pageable pageable);

    Optional<Inventory> findByWineId(Long wineId);
    
    Optional<Inventory> findByWineIdAndWarehouseId(Long wineId, Long warehouseId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.wine.id = :wineId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findByWineIdAndWarehouseIdWithPessimisticLock(@Param("wineId") Long wineId, @Param("warehouseId") Long warehouseId);
    
    List<Inventory> findByCurrentQuantityLessThanEqual(Integer quantity);
    List<Inventory> findByCurrentQuantityGreaterThan(Integer quantity);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.warehouse w WHERE i.wine.id = :wineId AND w.active = true")
    List<Inventory> findAllActiveByWineId(@Param("wineId") Long wineId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.warehouse w WHERE i.wine.id IN :wineIds AND w.active = true")
    List<Inventory> findAllActiveByWineIdIn(@Param("wineIds") List<Long> wineIds);

    List<Inventory> findByWarehouseActiveTrue();
}
