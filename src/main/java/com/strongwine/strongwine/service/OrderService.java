package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Cart;
import com.strongwine.strongwine.entity.CartItem;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.CartItemRepository;
import com.strongwine.strongwine.repository.CartRepository;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for Order business logic
 */
@Service
@Transactional
public class OrderService implements CodSettlementPort {

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

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;

    @Autowired(required = false)
    private CartRepository cartRepository;

    @Autowired(required = false)
    private CartItemRepository cartItemRepository;

    @Lazy
    @Autowired(required = false)
    private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;


    /**
     * Get all orders
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public org.springframework.data.domain.Page<Order> getAllOrdersPage(org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public org.springframework.data.domain.Page<Order> getOrdersPage(com.strongwine.strongwine.entity.OrderStatus status, org.springframework.data.domain.Pageable pageable) {
        if (status == null) {
            return orderRepository.findAll(pageable);
        }
        return orderRepository.findByStatus(status, pageable);
    }
    
    public long countOrders() {
        return orderRepository.count();
    }

    public long countOrdersByStatus(com.strongwine.strongwine.entity.OrderStatus status) {
        if (status == null) {
            return orderRepository.count();
        }
        return orderRepository.countByStatus(status);
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
        return createPendingOrder(
                userId,
                cartItems,
                fullName,
                phone,
                email,
                address,
                deliveryLat,
                deliveryLng,
                note,
                paymentMethod,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null
        );
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
                                    String paymentMethod,
                                    BigDecimal subtotalAmount,
                                    BigDecimal discountAmount,
                                    BigDecimal shippingFee,
                                    String voucherCode,
                                    Warehouse allocatedWarehouse) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
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
        
        BigDecimal computedSubtotal = BigDecimal.ZERO;
        
        // Create order items and calculate subtotal
        for (Map.Entry<Long, Integer> entry : cartItems.entrySet()) {
            Long wineId = entry.getKey();
            Integer quantity = entry.getValue();
            
            Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new ResourceNotFoundException("Wine not found with id: " + wineId));
            
            BigDecimal unitPrice = wine.getPrice() != null ? wine.getPrice() : BigDecimal.ZERO;
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setWine(wine);
            orderItem.setQuantity(quantity);
            orderItem.setPrice(unitPrice);
            
            order.getOrderItems().add(orderItem);
            computedSubtotal = computedSubtotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        BigDecimal effectiveSubtotal = subtotalAmount != null ? subtotalAmount : computedSubtotal;
        BigDecimal effectiveDiscount = nonNegativeOrZero(discountAmount);
        BigDecimal effectiveShippingFee = nonNegativeOrZero(shippingFee);
        BigDecimal effectiveTotal = effectiveSubtotal.add(effectiveShippingFee).subtract(effectiveDiscount);
        if (effectiveTotal.compareTo(BigDecimal.ZERO) < 0) {
            effectiveTotal = BigDecimal.ZERO;
        }

        order.setSubtotalAmount(effectiveSubtotal);
        order.setDiscountAmount(effectiveDiscount);
        order.setShippingFee(effectiveShippingFee);
        order.setVoucherCode(voucherCode != null && !voucherCode.isBlank() ? voucherCode.trim() : null);
        order.setAllocatedWarehouse(allocatedWarehouse);
        order.setTotalPrice(effectiveTotal);

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

    @Override
    public void settleCodOrder(Long orderId, String actorUsername) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Payment existingPayment = findLockedCodOrLatestPayment(orderId);
        boolean isCod = order.getPaymentMethod() == PaymentMethod.COD
                || (existingPayment != null && existingPayment.getMethod() == PaymentMethod.COD);
        if (!isCod) {
            throw new IllegalStateException("Đơn hàng #" + orderId + " không sử dụng phương thức thanh toán COD");
        }

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.RETURN_REQUESTED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalStateException("Không thể xác nhận thu hộ COD cho đơn hàng ở trạng thái " + order.getStatus());
        }

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Thanh toán COD của đơn #" + orderId + " không ở trạng thái chờ thu hộ (" + order.getPaymentStatus() + ")");
        }

        String actor = normalizeActor(actorUsername, "system");
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> beforeState = statusSnapshot(order);

        String codReference = settleCodPaymentRecord(order, existingPayment, actor, now);

        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentReference(codReference);
        if (order.getPaidAt() == null) {
            order.setPaidAt(now);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
        }
        order.setUpdatedAt(now);
        orderRepository.save(order);

        inventoryService.confirmOrderPayment(order);

        if (adminAuditLogService != null) {
            adminAuditLogService.log(actor, "SETTLE_COD", "ORDER", order.getId(), beforeState, statusSnapshot(order), null);
        }

        applicationEventPublisher.publishEvent(new OrderStatusUpdatedEvent(
                this,
                order.getId(),
                order.getStatus().name(),
                "Đã thu hộ COD thành công (" + codReference + ")"
        ));
    }

    public void cancelPendingOrder(Long orderId) {
        cancelPendingOrder(orderId, "system", "ORDER_CANCELLED");
    }

    public void cancelPendingOrder(Long orderId, String actorUsername, String reason) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        // Cancelling twice (VNPay return and IPN both report a failure) must not release the reservation twice.
        if (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.RETURN_REQUESTED
                || order.getStatus() == OrderStatus.RETURNED) {
            return;
        }

        String actor = normalizeActor(actorUsername, "system");
        String effectiveReason = (reason == null || reason.isBlank()) ? "ORDER_CANCELLED" : reason.trim();
        Map<String, Object> beforeState = statusSnapshot(order);

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        inventoryService.releaseOrderReservation(order);

        if (shipmentOrderLifecyclePort != null) {
            shipmentOrderLifecyclePort.cancelShipmentForOrder(orderId, actor, effectiveReason);
        }

        if (adminAuditLogService != null) {
            adminAuditLogService.log(actor, "CANCEL_ORDER", "ORDER", order.getId(), beforeState, statusSnapshot(order), null);
        }

        applicationEventPublisher.publishEvent(new OrderStatusUpdatedEvent(
                this,
                order.getId(),
                OrderStatus.CANCELLED.name(),
                "Đơn hàng #" + order.getId() + " đã được hủy (" + effectiveReason + ")"
        ));
    }

    public ReorderSummary reorderOrder(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng hoặc người dùng không hợp lệ");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getUser() == null || !userId.equals(order.getUser().getId())) {
            throw new SecurityException("Bạn không có quyền đặt lại đơn hàng này");
        }

        List<OrderItem> orderItems = order.getOrderItems() != null ? order.getOrderItems() : List.of();
        List<Long> candidateWineIds = new ArrayList<>();
        for (OrderItem item : orderItems) {
            if (item != null && item.getWine() != null && item.getWine().getId() != null && !item.getWine().isDeleted()) {
                candidateWineIds.add(item.getWine().getId());
            }
        }

        Map<Long, Integer> availableByWineId = candidateWineIds.isEmpty()
                ? Map.of()
                : inventoryService.getAvailableStockByWineIds(candidateWineIds);

        Cart cart = null;
        Map<Long, Integer> addedQuantities = new LinkedHashMap<>();
        List<ReorderItemSnapshot> addedItems = new ArrayList<>();
        List<String> skippedItems = new ArrayList<>();

        for (OrderItem item : orderItems) {
            Wine wine = item != null ? item.getWine() : null;
            if (wine == null || wine.getId() == null) {
                skippedItems.add("Sản phẩm không xác định");
                continue;
            }
            if (wine.isDeleted()) {
                skippedItems.add(wine.getName());
                continue;
            }
            int available = availableByWineId.getOrDefault(wine.getId(), 0);
            if (available <= 0) {
                skippedItems.add(wine.getName());
                continue;
            }

            int requestedQty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            int qtyToAdd = Math.min(requestedQty, available);

            if (cartRepository != null && cartItemRepository != null) {
                if (cart == null) {
                    cart = resolveOrCreateCart(order.getUser(), userId);
                }
                CartItem cartItem = cartItemRepository.findByCartIdAndWineId(cart.getId(), wine.getId()).orElse(null);
                if (cartItem == null) {
                    cartItem = new CartItem();
                    cartItem.setCart(cart);
                    cartItem.setWine(wine);
                    cartItem.setQuantity(qtyToAdd);
                } else {
                    int combined = Math.min((cartItem.getQuantity() != null ? cartItem.getQuantity() : 0) + qtyToAdd, available);
                    cartItem.setQuantity(combined);
                    qtyToAdd = combined;
                }
                cartItemRepository.save(cartItem);
            }

            addedQuantities.put(wine.getId(), qtyToAdd);
            addedItems.add(new ReorderItemSnapshot(
                    wine.getId(),
                    wine.getName(),
                    wine.getPrice() != null ? wine.getPrice() : BigDecimal.ZERO,
                    qtyToAdd,
                    wine.getImageUrl(),
                    available
            ));
        }

        return new ReorderSummary(
                order.getId(),
                addedItems.size(),
                Collections.unmodifiableMap(addedQuantities),
                List.copyOf(addedItems),
                List.copyOf(skippedItems)
        );
    }

    private Cart resolveOrCreateCart(User owner, Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(owner);
            return cartRepository.save(created);
        });
    }

    private Payment findLockedCodOrLatestPayment(Long orderId) {
        if (paymentRepository == null) {
            return null;
        }
        List<Payment> payments = paymentRepository.findByOrderIdForUpdate(orderId);
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        return payments.stream()
                .filter(p -> p.getMethod() == PaymentMethod.COD)
                .findFirst()
                .orElse(payments.get(0));
    }

    private String settleCodPaymentRecord(Order order, Payment existingPayment, String actor, LocalDateTime now) {
        String existingRef = order.getPaymentReference();
        if ((existingRef == null || existingRef.isBlank()) && existingPayment != null) {
            existingRef = existingPayment.getPaymentReference();
        }
        String reference = (existingRef != null && !existingRef.isBlank())
                ? existingRef
                : "COD-" + order.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (paymentRepository == null) {
            return reference;
        }

        Payment payment = existingPayment != null ? existingPayment : new Payment();
        if (existingPayment == null) {
            payment.setOrder(order);
            payment.setCurrency("VND");
            payment.setCreatedAt(now);
        }
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO);
        payment.setPaymentReference(reference);
        payment.setGatewayResponse("COD collected by " + actor);
        payment.setUpdatedAt(now);

        Payment savedPayment = paymentRepository.save(payment);
        if (paymentTransactionRepository != null) {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setPayment(savedPayment);
            tx.setTransactionType("COD_COLLECTED");
            tx.setStatus("SUCCESS");
            tx.setPayload("actor=" + actor + ";orderId=" + order.getId() + ";reference=" + reference);
            tx.setCreatedAt(now);
            paymentTransactionRepository.save(tx);
        }
        return reference;
    }

    private Map<String, Object> statusSnapshot(Order order) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        snapshot.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        snapshot.put("paymentReference", order.getPaymentReference());
        return snapshot;
    }

    private BigDecimal nonNegativeOrZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private String normalizeActor(String actorUsername, String fallback) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return fallback;
        }
        return actorUsername.trim();
    }

    private PaymentMethod parsePaymentMethod(String paymentMethod) {
        try {
            return PaymentMethod.valueOf(paymentMethod == null ? "STRIPE" : paymentMethod.toUpperCase());
        } catch (Exception ex) {
            return PaymentMethod.STRIPE;
        }
    }

    public record ReorderItemSnapshot(
            Long productId,
            String name,
            BigDecimal price,
            int quantity,
            String imageUrl,
            int stock
    ) {}

    public record ReorderSummary(
            Long orderId,
            int addedCount,
            Map<Long, Integer> addedQuantities,
            List<ReorderItemSnapshot> addedItems,
            List<String> skippedItems
    ) {}
}






