package com.strongwine.strongwine.service;

import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test-only helpers. The signer is written independently of VNPayService, following VNPay's published sample:
 * sort the parameter names, URL-encode names and values, join with '&', then HMAC-SHA512 in hex.
 */
final class VnPayTestSupport {

    static final String HASH_SECRET = "unit-test-hash-secret";
    static final String TMN_CODE = "TESTTMN1";
    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    private VnPayTestSupport() {
    }

    static VNPayService configuredService() {
        VNPayService service = new VNPayService();
        ReflectionTestUtils.setField(service, "tmnCode", TMN_CODE);
        ReflectionTestUtils.setField(service, "hashSecret", HASH_SECRET);
        ReflectionTestUtils.setField(service, "payUrl", PAY_URL);
        ReflectionTestUtils.setField(service, "returnUrl", "http://localhost:8080/api/payments/vnpay/return");
        ReflectionTestUtils.setField(service, "version", "2.1.0");
        ReflectionTestUtils.setField(service, "command", "pay");
        ReflectionTestUtils.setField(service, "orderType", "other");
        ReflectionTestUtils.setField(service, "locale", "vn");
        ReflectionTestUtils.setField(service, "expireMinutes", 15);
        return service;
    }

    static String sign(Map<String, String> params) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key) || value == null || value.isEmpty()) {
                continue;
            }
            if (data.length() > 0) {
                data.append('&');
            }
            data.append(encode(key)).append('=').append(encode(value));
        }
        return hmacSha512(HASH_SECRET, data.toString());
    }

    static Map<String, String> parseQuery(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
