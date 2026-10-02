package com.strongwine.strongwine.event;

import org.springframework.context.ApplicationEvent;

public class OrderStatusUpdatedEvent extends ApplicationEvent {
    private final Long orderId;
    private final String status;
    private final String message;

    public OrderStatusUpdatedEvent(Object source, Long orderId, String status, String message) {
        super(source);
        this.orderId = orderId;
        this.status = status;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
