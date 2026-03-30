package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for OrderItem entity
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}





