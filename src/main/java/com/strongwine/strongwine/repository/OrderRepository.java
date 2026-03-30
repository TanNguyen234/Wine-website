package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * Find all orders by user
     */
    List<Order> findByUser(User user);
    
    /**
     * Find all orders by user ID
     */
    List<Order> findByUserId(Long userId);

    /**
     * Find all orders by user ID in reverse chronological order.
     */
    List<Order> findByUserIdOrderByOrderDateDesc(Long userId);

    /**
     * Find one order by order id and owner user id.
     */
    java.util.Optional<Order> findByIdAndUserId(Long id, Long userId);
    
    /**
     * Calculate total revenue from all orders
     */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = com.strongwine.strongwine.entity.OrderStatus.PAID")
    Double getTotalRevenue();

        /**
         * Orders that are ready for shipment creation in admin screen.
         */
        @Query("""
                        SELECT o
                        FROM Order o
                        WHERE o.status = :status
                            AND NOT EXISTS (
                                    SELECT 1
                                    FROM Shipment s
                                    WHERE s.order.id = o.id
                            )
                        ORDER BY o.orderDate DESC
                        """)
        List<Order> findOrdersWithoutShipmentByStatus(OrderStatus status);
}





