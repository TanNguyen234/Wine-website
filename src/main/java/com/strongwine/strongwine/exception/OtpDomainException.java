package com.strongwine.strongwine.exception;

public class OtpDomainException extends RuntimeException {

    public OtpDomainException(String message) {
        super(message);
    }

    public OtpDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
