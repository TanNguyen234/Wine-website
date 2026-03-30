package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Order operations
 */
@RestController
@RequestMapping("/api/orders")
public class OrderApiController {
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;
    
    /**
     * Get all orders (Admin only)
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null || !isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orderService.getAllOrders());
    }
    
    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Order> order = orderService.getOrderById(id);
        if (order.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(currentUser) && !order.get().getUser().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return order.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get orders by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isAdmin(currentUser) && !userId.equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId));
    }
    
    /**
     * Create a new order
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestBody Map<Long, Integer> cartItems,
            Authentication authentication) {
        try {
            User currentUser = resolveCurrentUser(authentication);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long userId = currentUser.getId();
            Order order = orderService.createOrder(userId, cartItems);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update an order
     */
    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        try {
            Order updatedOrder = orderService.updateOrder(id, order);
            return ResponseEntity.ok(updatedOrder);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Delete an order
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}





