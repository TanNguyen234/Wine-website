package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        LocalDateTime orderDate,
        BigDecimal totalPrice,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String shippingFullName,
        String shippingPhone,
        String shippingEmail,
        String shippingAddress,
        Double shippingLatitude,
        Double shippingLongitude,
        String note,
        List<OrderItemDto> orderItems,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        String voucherCode,
        String allocatedWarehouseName) {

    /**
     * Backward-compatible 15-argument constructor preserving pre-V3 callers.
     */
    public OrderDto(
            Long id,
            Long userId,
            LocalDateTime orderDate,
            BigDecimal totalPrice,
            OrderStatus status,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String shippingFullName,
            String shippingPhone,
            String shippingEmail,
            String shippingAddress,
            Double shippingLatitude,
            Double shippingLongitude,
            String note,
            List<OrderItemDto> orderItems) {
        this(
                id,
                userId,
                orderDate,
                totalPrice,
                status,
                paymentStatus,
                paymentMethod,
                shippingFullName,
                shippingPhone,
                shippingEmail,
                shippingAddress,
                shippingLatitude,
                shippingLongitude,
                note,
                orderItems,
                totalPrice,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null);
    }

    public static OrderDto from(Order order) {
        if (order == null) {
            return null;
        }
        BigDecimal subtotal = order.getSubtotalAmount() != null ? order.getSubtotalAmount() : order.getTotalPrice();
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal shippingFee = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
        String warehouseName = order.getAllocatedWarehouse() != null ? order.getAllocatedWarehouse().getName() : null;

        return new OrderDto(
                order.getId(),
                order.getUser() == null ? null : order.getUser().getId(),
                order.getOrderDate(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getShippingFullName(),
                order.getShippingPhone(),
                order.getShippingEmail(),
                order.getShippingAddress(),
                order.getShippingLatitude(),
                order.getShippingLongitude(),
                order.getNote(),
                order.getOrderItems() == null
                        ? List.of()
                        : order.getOrderItems().stream().map(OrderItemDto::from).toList(),
                subtotal,
                discount,
                shippingFee,
                order.getVoucherCode(),
                warehouseName);
    }
}
