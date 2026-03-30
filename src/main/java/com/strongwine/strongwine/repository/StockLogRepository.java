package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.StockLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLogRepository extends JpaRepository<StockLog, Long> {
}
