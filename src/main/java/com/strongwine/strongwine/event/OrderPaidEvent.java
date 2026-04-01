package com.strongwine.strongwine.event;

public record OrderPaidEvent(Long orderId, String paymentReference) {
}
