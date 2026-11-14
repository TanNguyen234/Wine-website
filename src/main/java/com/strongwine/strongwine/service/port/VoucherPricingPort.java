package com.strongwine.strongwine.service.port;

import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;

import java.math.BigDecimal;

public interface VoucherPricingPort {
    VoucherValidationResult validateAndQuote(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee);

    void redeemForOrder(String code, User user, Order order);
}
