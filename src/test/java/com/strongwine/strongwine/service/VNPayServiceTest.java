package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VNPayServiceTest {

    private VNPayService vnPayService;

    @BeforeEach
    void setUp() {
        vnPayService = new VNPayService();
        ReflectionTestUtils.setField(vnPayService, "tmnCode", "L5GXH1AU");
        ReflectionTestUtils.setField(vnPayService, "hashSecret", "EVCHM01XP0U7PT31IRMOJKC2IXRV2DW7");
        ReflectionTestUtils.setField(vnPayService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(vnPayService, "returnUrl", "http://localhost:8080/api/payments/vnpay/return");
        ReflectionTestUtils.setField(vnPayService, "version", "2.1.0");
        ReflectionTestUtils.setField(vnPayService, "command", "pay");
        ReflectionTestUtils.setField(vnPayService, "orderType", "other");
        ReflectionTestUtils.setField(vnPayService, "locale", "vn");
        ReflectionTestUtils.setField(vnPayService, "expireMinutes", 15);
    }

    @Test
    void createPaymentUrl_generatesSignedUrl() {
        Payment payment = buildPayment();

        String paymentUrl = vnPayService.createPaymentUrl(payment, "http://localhost:8080", "127.0.0.1");
        Map<String, String> callbackParams = parseQuery(paymentUrl);

        assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(callbackParams.get("vnp_TmnCode")).isEqualTo("L5GXH1AU");
        assertThat(callbackParams.get("vnp_TxnRef")).isEqualTo("PAY-TEST-12345678");
        assertThat(callbackParams.get("vnp_Amount")).isEqualTo("15000000");
        assertThat(callbackParams.get("vnp_ReturnUrl")).isEqualTo("http://localhost:8080/api/payments/vnpay/return");
        assertThat(callbackParams.get("vnp_SecureHash")).isNotBlank();
        assertThat(vnPayService.isValidSignature(callbackParams)).isTrue();
    }

    @Test
    void isValidSignature_rejectsTamperedParams() {
        Payment payment = buildPayment();
        String paymentUrl = vnPayService.createPaymentUrl(payment, "http://localhost:8080", "127.0.0.1");
        Map<String, String> callbackParams = parseQuery(paymentUrl);

        callbackParams.put("vnp_Amount", "15000100");

        assertThat(vnPayService.isValidSignature(callbackParams)).isFalse();
    }

    @Test
    void isSuccessfulResponse_supportsMissingTransactionStatus() {
        Map<String, String> successNoStatus = new LinkedHashMap<>();
        successNoStatus.put("vnp_ResponseCode", "00");

        Map<String, String> successWithStatus = new LinkedHashMap<>();
        successWithStatus.put("vnp_ResponseCode", "00");
        successWithStatus.put("vnp_TransactionStatus", "00");

        Map<String, String> failed = new LinkedHashMap<>();
        failed.put("vnp_ResponseCode", "24");
        failed.put("vnp_TransactionStatus", "02");

        assertThat(vnPayService.isSuccessfulResponse(successNoStatus)).isTrue();
        assertThat(vnPayService.isSuccessfulResponse(successWithStatus)).isTrue();
        assertThat(vnPayService.isSuccessfulResponse(failed)).isFalse();
    }

    private Payment buildPayment() {
        Order order = new Order();
        order.setId(123L);
        order.setTotalPrice(BigDecimal.valueOf(150000));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(BigDecimal.valueOf(150000));
        payment.setCurrency("VND");
        payment.setPaymentReference("PAY-TEST-12345678");
        return payment;
    }

    private Map<String, String> parseQuery(String url) {
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : "";
        Map<String, String> values = new LinkedHashMap<>();

        Arrays.stream(query.split("&"))
                .filter(pair -> !pair.isBlank())
                .forEach(pair -> {
                    String[] kv = pair.split("=", 2);
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                    values.put(key, value);
                });

        return values;
    }
}
