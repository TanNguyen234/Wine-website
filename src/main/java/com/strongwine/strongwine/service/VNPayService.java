package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class VNPayService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${vnpay.tmnCode:}")
    private String tmnCode;

    @Value("${vnpay.hashSecret:}")
    private String hashSecret;

    @Value("${vnpay.payUrl:}")
    private String payUrl;

    @Value("${vnpay.returnUrl:}")
    private String returnUrl;

    @Value("${vnpay.version:2.1.0}")
    private String version;

    @Value("${vnpay.command:pay}")
    private String command;

    @Value("${vnpay.orderType:other}")
    private String orderType;

    @Value("${vnpay.locale:vn}")
    private String locale;

    @Value("${vnpay.expireMinutes:15}")
    private int expireMinutes;

    public String createPaymentUrl(Payment payment, String baseUrl, String clientIp) {
        ensureConfigured();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", trimOrDefault(version, "2.1.0"));
        params.put("vnp_Command", trimOrDefault(command, "pay"));
        params.put("vnp_TmnCode", tmnCode.trim());
        params.put("vnp_Amount", String.valueOf(toVnPayAmount(payment.getAmount())));
        params.put("vnp_CreateDate", nowInVietNam().format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", nowInVietNam().plusMinutes(Math.max(1, expireMinutes)).format(VNPAY_DATE_FORMAT));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", normalizeClientIp(clientIp));
        params.put("vnp_Locale", trimOrDefault(locale, "vn"));
        params.put("vnp_OrderInfo", "Thanh toan don hang #" + payment.getOrder().getId());
        params.put("vnp_OrderType", trimOrDefault(orderType, "other"));
        params.put("vnp_ReturnUrl", resolveReturnUrl(baseUrl));
        params.put("vnp_TxnRef", payment.getPaymentReference());

        String canonicalQuery = toCanonicalQuery(params);
        String secureHash = hmacSha512(hashSecret.trim(), canonicalQuery);

        String normalizedPayUrl = payUrl.trim();
        String separator = normalizedPayUrl.contains("?") ? "&" : "?";
        return normalizedPayUrl + separator + canonicalQuery + "&vnp_SecureHash=" + urlEncode(secureHash);
    }

    public boolean isValidSignature(Map<String, String> callbackParams) {
        if (callbackParams == null || callbackParams.isEmpty() || hashSecret == null || hashSecret.isBlank()) {
            return false;
        }

        String receivedHash = callbackParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        Map<String, String> signData = new HashMap<>(callbackParams);
        signData.remove("vnp_SecureHash");
        signData.remove("vnp_SecureHashType");

        String expectedHash = hmacSha512(hashSecret.trim(), toCanonicalQuery(signData));
        return expectedHash.equalsIgnoreCase(receivedHash.trim());
    }

    public boolean isSuccessfulResponse(Map<String, String> callbackParams) {
        String responseCode = valueOf(callbackParams, "vnp_ResponseCode");
        String transactionStatus = valueOf(callbackParams, "vnp_TransactionStatus");
        boolean transactionOk = transactionStatus == null || transactionStatus.isBlank() || "00".equals(transactionStatus);
        return "00".equals(responseCode) && transactionOk;
    }

    public String getTxnRef(Map<String, String> callbackParams) {
        return valueOf(callbackParams, "vnp_TxnRef");
    }

    public long getAmount(Map<String, String> callbackParams) {
        String amount = valueOf(callbackParams, "vnp_Amount");
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("VNPay callback thiếu vnp_Amount");
        }
        return Long.parseLong(amount.trim());
    }

    public String compactPayload(Map<String, String> callbackParams) {
        if (callbackParams == null || callbackParams.isEmpty()) {
            return "";
        }
        return new TreeMap<>(callbackParams).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public long toVnPayAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String toCanonicalQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Khong the ky chu ky VNPay", ex);
        }
    }

    private String urlEncode(String input) {
        return URLEncoder.encode(input == null ? "" : input, StandardCharsets.UTF_8);
    }

    private void ensureConfigured() {
        if (tmnCode == null || tmnCode.isBlank()) {
            throw new IllegalStateException("VNPay TMN code chua duoc cau hinh");
        }
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalStateException("VNPay hash secret chua duoc cau hinh");
        }
        if (payUrl == null || payUrl.isBlank()) {
            throw new IllegalStateException("VNPay pay URL chua duoc cau hinh");
        }
    }

    private String resolveReturnUrl(String baseUrl) {
        if (returnUrl != null && !returnUrl.isBlank()) {
            return returnUrl.trim();
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (normalizedBaseUrl.isBlank()) {
            throw new IllegalStateException("Khong xac dinh duoc return URL cua VNPay");
        }
        return normalizedBaseUrl + "/api/payments/vnpay/return";
    }

    private LocalDateTime nowInVietNam() {
        return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "127.0.0.1";
        }
        if ("0:0:0:0:0:0:0:1".equals(clientIp)) {
            return "127.0.0.1";
        }
        return clientIp.trim();
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String valueOf(Map<String, String> callbackParams, String key) {
        if (callbackParams == null) {
            return null;
        }
        String value = callbackParams.get(key);
        return value == null ? null : value.trim();
    }
}
