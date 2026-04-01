package com.strongwine.strongwine.service;

public record OtpDeliveryResult(boolean success,
                                int attempts,
                                String recipient,
                                String errorMessage) {

    public static OtpDeliveryResult sent(int attempts, String recipient) {
        return new OtpDeliveryResult(true, attempts, recipient, null);
    }

    public static OtpDeliveryResult failed(int attempts, String recipient, String errorMessage) {
        return new OtpDeliveryResult(false, attempts, recipient, errorMessage);
    }
}
