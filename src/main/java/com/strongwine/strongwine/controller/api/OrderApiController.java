package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.OrderDto;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderDeletionResult;
import com.strongwine.strongwine.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderDto>> getAllOrders(Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null || !isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orderService.getAllOrders().stream().map(OrderDto::from).toList());
    }
    
    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Order> order = orderService.getOrderById(id);
        if (order.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(currentUser) && (order.get().getUser() == null
                || !order.get().getUser().getId().equals(currentUser.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return order.map(OrderDto::from).map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get orders by user ID
     */
    @GetMapping("/user/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderDto>> getOrdersByUserId(@PathVariable Long userId, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isAdmin(currentUser) && !userId.equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId).stream().map(OrderDto::from).toList());
    }
    
    /**
     * Create a new order
     */
    @PostMapping
    public ResponseEntity<OrderDto> createOrder(
            @RequestBody Map<Long, Integer> cartItems,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Order> order = orderService.createOrder(currentUser.getId(), cartItems);
        if (order.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderDto.from(order.get()));
    }
    
    /**
     * Update an order
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<OrderDto> updateOrder(@PathVariable Long id, @RequestBody Order order,
                                                Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Order> updatedOrder = orderService.updateOrder(id, order);
        if (updatedOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OrderDto.from(updatedOrder.get()));
    }
    
    /**
     * Delete an order
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return switch (orderService.deleteOrder(id)) {
            case DELETED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<Map<String, Object>> reorderOrder(@PathVariable Long id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            OrderService.ReorderSummary summary = orderService.reorderOrder(id, currentUser.getId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderId", summary.orderId());
            body.put("addedCount", summary.addedCount());
            body.put("skippedCount", summary.skippedItems().size());
            body.put("addedQuantities", summary.addedQuantities());
            body.put("addedItems", summary.addedItems());
            body.put("skippedItems", summary.skippedItems());
            body.put("redirectUrl", "/cart");
            body.put("message", summary.addedCount() > 0
                    ? "Đã thêm " + summary.addedCount() + " sản phẩm vào giỏ hàng"
                    : "Không có sản phẩm nào còn khả dụng để đặt lại");
            return ResponseEntity.ok(body);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
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





