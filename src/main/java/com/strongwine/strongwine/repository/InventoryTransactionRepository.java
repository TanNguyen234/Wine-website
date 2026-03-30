package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    List<InventoryTransaction> findTop100ByOrderByCreatedAtDesc();
    List<InventoryTransaction> findByWineIdOrderByCreatedAtDesc(Long wineId);
}