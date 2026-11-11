package com.strongwine.strongwine.exception;

/**
 * Thrown when an order or profile shipping address is outside mainland Vietnam
 * or located in remote islands / offshore archipelagos.
 */
public class DeliveryAddressNotSupportedException extends StrongWineException {

    public DeliveryAddressNotSupportedException(String message) {
        super(message);
    }

    public DeliveryAddressNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
