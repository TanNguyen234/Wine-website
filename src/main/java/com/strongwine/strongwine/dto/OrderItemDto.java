package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.OrderItem;
import java.math.BigDecimal;

public record OrderItemDto(Long id, Long wineId, String wineName, Integer quantity, BigDecimal price) {
    public static OrderItemDto from(OrderItem item) {
        return new OrderItemDto(
                item.getId(),
                item.getWine() == null ? null : item.getWine().getId(),
                item.getWine() == null ? null : item.getWine().getName(),
                item.getQuantity(),
                item.getPrice());
    }
}
