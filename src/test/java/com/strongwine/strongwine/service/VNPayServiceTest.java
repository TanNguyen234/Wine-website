package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.strongwine.strongwine.service.VnPayTestSupport.configuredService;
import static com.strongwine.strongwine.service.VnPayTestSupport.parseQuery;
import static com.strongwine.strongwine.service.VnPayTestSupport.sign;
import static org.assertj.core.api.Assertions.assertThat;

class VNPayServiceTest {

    private static final String TXN_REF = "PAY-0123456789ABCDEF";

    private Payment payment(String amount) {
        Order order = new Order();
        order.setId(42L);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentReference(TXN_REF);
        return payment;
    }

    @Test
    void paymentUrlIsSignedAndUsesVndInMinorUnitsWithoutSpecialCharactersInOrderInfo() {
        String url = configuredService().createPaymentUrl(payment("1234000.00"), "http://localhost:8080", "203.0.113.7");

        assertThat(url).startsWith(VnPayTestSupport.PAY_URL + "?");
        Map<String, String> params = parseQuery(url);
        assertThat(params.get("vnp_Amount")).isEqualTo("123400000");
        assertThat(params.get("vnp_TxnRef")).isEqualTo(TXN_REF);
        assertThat(params.get("vnp_OrderInfo")).isEqualTo("Thanh toan don hang 42");
        assertThat(params.get("vnp_IpAddr")).isEqualTo("203.0.113.7");

        String receivedHash = params.remove("vnp_SecureHash");
        assertThat(receivedHash).isEqualTo(sign(params));
    }

    @Test
    void callbackSignedWithTheSecretIsAcceptedAndTamperedCallbackIsRejected() {
        VNPayService service = configuredService();
        Map<String, String> callback = new HashMap<>();
        callback.put("vnp_Amount", "123400000");
        callback.put("vnp_TxnRef", TXN_REF);
        callback.put("vnp_ResponseCode", "00");
        callback.put("vnp_TransactionStatus", "00");
        callback.put("vnp_OrderInfo", "Thanh toan don hang 42");
        callback.put("vnp_BankCode", "NCB");
        callback.put("vnp_SecureHash", sign(callback));

        assertThat(service.isValidSignature(callback)).isTrue();

        callback.put("vnp_Amount", "100");
        assertThat(service.isValidSignature(callback)).isFalse();
    }

    @Test
    void successIsOnlyReportedForResponseCodeAndTransactionStatusZero() {
        VNPayService service = configuredService();
        Map<String, String> ok = Map.of("vnp_ResponseCode", "00", "vnp_TransactionStatus", "00");
        Map<String, String> cancelled = Map.of("vnp_ResponseCode", "24", "vnp_TransactionStatus", "24");
        Map<String, String> missingStatus = Map.of("vnp_ResponseCode", "00");
        Map<String, String> blankStatus = Map.of("vnp_ResponseCode", "00", "vnp_TransactionStatus", " ");

        assertThat(service.isSuccessfulResponse(ok)).isTrue();
        assertThat(service.isSuccessfulResponse(cancelled)).isFalse();
        assertThat(service.isSuccessfulResponse(missingStatus)).isFalse();
        assertThat(service.isSuccessfulResponse(blankStatus)).isFalse();
    }

    @Test
    void createDateAndExpireDateUseExactExpireMinutesFromSingleTimestamp() {
        String url = configuredService().createPaymentUrl(payment("150000000.00"), "http://localhost:8080", "127.0.0.1");
        Map<String, String> params = parseQuery(url);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime created = LocalDateTime.parse(params.get("vnp_CreateDate"), fmt);
        LocalDateTime expired = LocalDateTime.parse(params.get("vnp_ExpireDate"), fmt);

        assertThat(expired).isEqualTo(created.plusMinutes(15));
        assertThat(params.get("vnp_Amount")).isEqualTo("15000000000");
    }

    @Test
    void tmnCodeValidationAcceptsConfiguredCodeAndRejectsMismatch() {
        VNPayService service = configuredService();
        Map<String, String> valid = new HashMap<>();
        valid.put("vnp_TmnCode", VnPayTestSupport.TMN_CODE);
        valid.put("vnp_Amount", "123400000");
        valid.put("vnp_TxnRef", TXN_REF);
        valid.put("vnp_SecureHash", sign(valid));

        Map<String, String> mismatch = new HashMap<>();
        mismatch.put("vnp_TmnCode", "ATTACKER_TMN");
        mismatch.put("vnp_Amount", "123400000");
        mismatch.put("vnp_TxnRef", TXN_REF);
        mismatch.put("vnp_SecureHash", sign(mismatch));

        assertThat(service.isValidTmnCode(valid)).isTrue();
        assertThat(service.isValidTmnCode(mismatch)).isFalse();
        assertThat(service.isValidSignature(mismatch)).isFalse();
    }
}
