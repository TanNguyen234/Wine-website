package com.strongwine.strongwine.exception;

public class OtpDeliveryException extends OtpDomainException {

    public OtpDeliveryException(String message) {
        super(message);
    }

    public OtpDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
