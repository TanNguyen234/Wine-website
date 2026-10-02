package com.strongwine.strongwine.exception;

public class StrongWineException extends RuntimeException {
    public StrongWineException(String message) {
        super(message);
    }
    public StrongWineException(String message, Throwable cause) {
        super(message, cause);
    }
}
