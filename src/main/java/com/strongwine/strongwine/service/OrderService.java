package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for Order business logic
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private WineRepository wineRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;


    /**
     * Get all orders
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public org.springframework.data.domain.Page<Order> getAllOrdersPage(org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
    
    public long countOrders() {
        return orderRepository.count();
    }
    
    /**
     * Get order by ID
     */
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    
    /**
     * Get all orders for a user
     */
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByOrderDateDesc(userId);
    }

    /**
     * Get one order owned by a specific user.
     */
    public Optional<Order> getOrderByIdForUser(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId);
    }
    
    /**
     * Create a new order from cart items
     * @param userId The user placing the order
     * @param cartItems Map of wineId -> quantity
     */
    public Optional<Order> createOrder(Long userId, Map<Long, Integer> cartItems) {
        if (userId == null || cartItems == null || cartItems.isEmpty() || !userRepository.existsById(userId)) {
            return Optional.empty();
        }
        for (Map.Entry<Long, Integer> item : cartItems.entrySet()) {
            if (item.getKey() == null || item.getValue() == null || item.getValue() <= 0
                    || !wineRepository.existsById(item.getKey())) {
                return Optional.empty();
            }
        }
        return Optional.of(createPendingOrder(userId, cartItems, null, null, null, null, null, null, null, PaymentMethod.STRIPE.name()));
    }

    public Order createPendingOrder(Long userId,
                                    Map<Long, Integer> cartItems,
                                    String fullName,
                                    String phone,
                                    String email,
                                    String address,
                                    Double deliveryLat,
                                    Double deliveryLng,
                                    String note,
                                    String paymentMethod) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(parsePaymentMethod(paymentMethod));
        order.setShippingFullName(fullName);
        order.setShippingPhone(phone);
        order.setShippingEmail(email);
        order.setShippingAddress(address);
        order.setShippingLatitude(deliveryLat);
        order.setShippingLongitude(deliveryLng);
        order.setNote(note);
        order.setUpdatedAt(LocalDateTime.now());
        
        BigDecimal totalPrice = BigDecimal.ZERO;
        
        // Create order items and calculate total
        for (Map.Entry<Long, Integer> entry : cartItems.entrySet()) {
            Long wineId = entry.getKey();
            Integer quantity = entry.getValue();
            
            Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new RuntimeException("Wine not found with id: " + wineId));
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setWine(wine);
            orderItem.setQuantity(quantity);
            orderItem.setPrice(wine.getPrice());
            
            order.getOrderItems().add(orderItem);
            totalPrice = totalPrice.add(wine.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        
        order.setTotalPrice(totalPrice);
        Order savedOrder = orderRepository.save(order);
        inventoryService.reserveForOrder(savedOrder);
        return savedOrder;
    }
    
    /**
     * Update an existing order
     */
    public Optional<Order> updateOrder(Long id, Order orderDetails) {
        Optional<Order> existingOrder = orderRepository.findById(id);
        if (existingOrder.isEmpty()) {
            return Optional.empty();
        }
        Order order = existingOrder.get();
        
        order.setTotalPrice(orderDetails.getTotalPrice());
        order.setUpdatedAt(LocalDateTime.now());
        // Note: Order items are typically not updated after order creation
        
        return Optional.of(orderRepository.save(order));
    }

    /**
     * Delete an order
     */
    public OrderDeletionResult deleteOrder(Long id) {
        Optional<Order> existingOrder = orderRepository.findById(id);
        if (existingOrder.isEmpty()) {
            return OrderDeletionResult.NOT_FOUND;
        }

        Order order = existingOrder.get();
        if (order.getStatus() != OrderStatus.PENDING
                || order.getPaymentStatus() != PaymentStatus.PENDING
                || order.getPaymentReference() != null
                || !order.getPayments().isEmpty()
                || shipmentRepository.existsByOrderId(id)) {
            return OrderDeletionResult.CONFLICT;
        }

        inventoryService.releaseOrderReservation(order);
        orderRepository.delete(order);
        return OrderDeletionResult.DELETED;
    }
    
    /**
     * Get total revenue
     */
    public Double getTotalRevenue() {
        return orderRepository.getTotalRevenue();
    }

    public void markOrderPaid(Long orderId, String paymentReference) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.PAID && order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Order {} already marked as paid, skipping.", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Payment arrived after the order was cancelled and its stock released. Reserve the stock again so
            // confirmOrderPayment never deducts a reservation that does not exist. Throws when stock is gone.
            inventoryService.reserveForOrder(order);
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentReference(paymentReference);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        inventoryService.confirmOrderPayment(order);

        // Shipment creation and shipper assignment are handled by OrderPaidShipmentListener
        // which runs @TransactionalEventListener(phase = AFTER_COMMIT) â€” this ensures
        // a shipment failure never rolls back a successful payment transaction.
        applicationEventPublisher.publishEvent(new OrderPaidEvent(order.getId(), paymentReference));
    }

    public void cancelPendingOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        // Cancelling twice (VNPay return and IPN both report a failure) must not release the reservation twice.
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        inventoryService.releaseOrderReservation(order);
    }

    private PaymentMethod parsePaymentMethod(String paymentMethod) {
        try {
            return PaymentMethod.valueOf(paymentMethod == null ? "STRIPE" : paymentMethod.toUpperCase());
        } catch (Exception ex) {
            return PaymentMethod.STRIPE;
        }
    }
}






