package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.VoucherType;

import java.math.BigDecimal;

public record VoucherValidationResult(
    boolean valid,
    String code,
    VoucherType voucherType,
    BigDecimal discountAmount,
    BigDecimal shippingDiscount,
    BigDecimal finalShippingFee,
    BigDecimal finalTotal,
    String message
) {
    public static VoucherValidationResult valid(
        String code,
        VoucherType voucherType,
        BigDecimal discountAmount,
        BigDecimal shippingDiscount,
        BigDecimal finalShippingFee,
        BigDecimal finalTotal,
        String message
    ) {
        return new VoucherValidationResult(
            true,
            code,
            voucherType,
            nonNegative(discountAmount),
            nonNegative(shippingDiscount),
            nonNegative(finalShippingFee),
            nonNegative(finalTotal),
            message
        );
    }

    public static VoucherValidationResult invalid(
        String code,
        BigDecimal originalShippingFee,
        BigDecimal originalSubtotal,
        String message
    ) {
        BigDecimal safeSubtotal = nonNegative(originalSubtotal);
        BigDecimal safeShippingFee = nonNegative(originalShippingFee);
        return new VoucherValidationResult(
            false,
            code,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            safeShippingFee,
            safeSubtotal.add(safeShippingFee),
            message
        );
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
