package com.strongwine.strongwine.exception;

/**
 * A VNPay callback that cannot be matched to a valid payment. Carries the RspCode that must be returned to VNPay
 * for IPN calls (01 order not found, 04 invalid amount).
 */
public class VnPayCallbackException extends StrongWineException {

    private final String rspCode;

    public VnPayCallbackException(String rspCode, String message) {
        super(message);
        this.rspCode = rspCode;
    }

    public String getRspCode() {
        return rspCode;
    }
}
