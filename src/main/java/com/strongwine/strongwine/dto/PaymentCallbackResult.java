package com.strongwine.strongwine.dto;

public class PaymentCallbackResult {
    private final boolean success;
    private final String message;
    private final Long orderId;

    public PaymentCallbackResult(boolean success, String message, Long orderId) {
        this.success = success;
        this.message = message;
        this.orderId = orderId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Long getOrderId() {
        return orderId;
    }
}
