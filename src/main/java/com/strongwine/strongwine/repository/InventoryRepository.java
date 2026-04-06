package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByWineId(Long wineId);
    
    Optional<Inventory> findByWineIdAndWarehouseId(Long wineId, Long warehouseId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.wine.id = :wineId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findByWineIdAndWarehouseIdWithPessimisticLock(@Param("wineId") Long wineId, @Param("warehouseId") Long warehouseId);
    
    List<Inventory> findByCurrentQuantityLessThanEqual(Integer quantity);
    List<Inventory> findByCurrentQuantityGreaterThan(Integer quantity);

    boolean existsByWarehouseId(Long warehouseId);
}
