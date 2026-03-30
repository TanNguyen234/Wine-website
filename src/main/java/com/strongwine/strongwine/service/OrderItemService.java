package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for OrderItem business logic
 */
@Service
@Transactional
public class OrderItemService {
    
    @Autowired
    private OrderItemRepository orderItemRepository;
    
    /**
     * Get all order items
     */
    public List<OrderItem> getAllOrderItems() {
        return orderItemRepository.findAll();
    }
    
    /**
     * Get order item by ID
     */
    public Optional<OrderItem> getOrderItemById(Long id) {
        return orderItemRepository.findById(id);
    }
    
    /**
     * Create a new order item
     */
    public OrderItem createOrderItem(OrderItem orderItem) {
        return orderItemRepository.save(orderItem);
    }
    
    /**
     * Update an existing order item
     */
    public OrderItem updateOrderItem(Long id, OrderItem orderItemDetails) {
        OrderItem orderItem = orderItemRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("OrderItem not found with id: " + id));
        
        orderItem.setQuantity(orderItemDetails.getQuantity());
        orderItem.setPrice(orderItemDetails.getPrice());
        
        return orderItemRepository.save(orderItem);
    }
    
    /**
     * Delete an order item
     */
    public void deleteOrderItem(Long id) {
        if (!orderItemRepository.existsById(id)) {
            throw new RuntimeException("OrderItem not found with id: " + id);
        }
        orderItemRepository.deleteById(id);
    }
}





