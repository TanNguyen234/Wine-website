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
        List<OrderItemDto> orderItems) {

    public static OrderDto from(Order order) {
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
                        : order.getOrderItems().stream().map(OrderItemDto::from).toList());
    }
}
