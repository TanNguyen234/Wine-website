package com.strongwine.strongwine.service;

import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.exception.VnPayCallbackException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_EXPIRY_BATCH_SIZE = 200;
    private static final int MAX_GATEWAY_RESPONSE_LENGTH = 2000;
    private static final int MAX_TRANSACTION_PAYLOAD_LENGTH = 2000;
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("VND");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    @Lazy
    private PaymentService self;

    @Autowired(required = false)
    @Lazy
    private ShipmentService shipmentService;

    @Value("${stripe.webhookSecret:}")
    private String stripeWebhookSecret;

    public String createPaymentSession(Order order, String method, String baseUrl) {
        return createPaymentSession(order, method, baseUrl, "127.0.0.1");
    }

    public String createPaymentSession(Order order, String method, String baseUrl, String clientIp) {
        PaymentMethod paymentMethod = parseMethod(method);
        if (paymentMethod == PaymentMethod.COD) {
            Payment payment = self.createInitialPayment(order, paymentMethod);
            payment.setGatewayResponse("COD_PENDING");
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            saveTransaction(payment, "COD_ORDER", "PENDING", "Cash on delivery placed for order " + order.getId());

            try {
                if (shipmentService != null) {
                    shipmentService.createAutoShipmentForPaidOrder(order);
                }
            } catch (Exception ex) {
                // Ignore if fleet is busy; auto-dispatch or admin will assign
            }
            return baseUrl + "/orders/" + order.getId();
        }

        Payment payment = self.createInitialPayment(order, paymentMethod);

        try {
            return switch (paymentMethod) {
                case STRIPE -> createStripeSession(payment, baseUrl);
                case VNPAY -> createVnPaySession(payment, baseUrl, clientIp);
                default -> throw new IllegalArgumentException("Phương thức thanh toán chưa được hỗ trợ: " + paymentMethod.name());
            };
        } catch (Exception ex) {
            self.updatePaymentFailed(
                    payment.getId(),
                    paymentMethod.name() + "_SESSION",
                    paymentMethod.name() + "_SESSION_FAILED",
                    ex.getMessage());
            throw new RuntimeException("Không thể tạo phiên thanh toán " + paymentMethod.name(), ex);
        }
    }

    private String createStripeSession(Payment payment, String baseUrl) throws Exception {
        Session session = stripeService.createCheckoutSession(payment, baseUrl);
        self.updatePaymentWithSession(payment.getId(), session);
        return session.getUrl();
    }

    private String createVnPaySession(Payment payment, String baseUrl, String clientIp) {
        String paymentUrl = vnPayService.createPaymentUrl(payment, baseUrl, clientIp);
        self.updatePaymentWithVnPaySession(payment.getId(), payment.getPaymentReference(), paymentUrl);
        return paymentUrl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentWithVnPaySession(Long paymentId, String txnRef, String paymentUrl) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setGatewaySessionId(txnRef);
        payment.setGatewayResponse("VNPAY_SESSION_CREATED");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, "VNPAY_SESSION", "REDIRECT", paymentUrl);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createInitialPayment(Order order, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(paymentMethod);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalPrice());
        payment.setCurrency("VND");
        payment.setPaymentReference("PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment.setGatewaySessionId(null);
        payment.setGatewayResponse("SESSION_CREATED");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        saveTransaction(saved, "SESSION_CREATED", "PENDING", "Order " + order.getId());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentWithSession(Long paymentId, Session session) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setGatewaySessionId(session.getId());
        payment.setGatewayResponse("STRIPE_SESSION_CREATED");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, "STRIPE_SESSION", "REDIRECT", "Redirect to Stripe checkout session");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentFailed(Long paymentId, String errorMessage) {
        updatePaymentFailed(paymentId, "STRIPE_SESSION", "STRIPE_SESSION_FAILED", errorMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentFailed(Long paymentId, String transactionType, String gatewayResponse, String errorMessage) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setGatewayResponse(limitLength(gatewayResponse + ": " + errorMessage, MAX_GATEWAY_RESPONSE_LENGTH));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, transactionType, "FAILED", errorMessage);
    }

    @Transactional
    public PaymentCallbackResult validateStripeSuccessRedirect(String stripeSessionId, Long userId) {
        try {
            Session session = stripeService.retrieveSession(stripeSessionId);
            Payment payment = resolveAndValidatePayment(session, true, true, true);

            if (payment.getOrder().getUser() == null || !Objects.equals(payment.getOrder().getUser().getId(), userId)) {
                return new PaymentCallbackResult(false, "Không thể truy cập đơn hàng thanh toán", null);
            }

            boolean finalizedNow = finalizeSuccessfulPayment(payment, "STRIPE_SUCCESS_REDIRECT", session.getId());
            if (finalizedNow) {
                return new PaymentCallbackResult(true, "Thanh toán thành công", payment.getOrder().getId());
            }

            return new PaymentCallbackResult(true, "Thanh toán đã được xác nhận", payment.getOrder().getId());
        } catch (Exception ex) {
            return new PaymentCallbackResult(false, "Xác thực Stripe thất bại: " + ex.getMessage(), null);
        }
    }

    public PaymentCallbackResult handleStripeCancel() {
        return new PaymentCallbackResult(false, "Bạn đã hủy phiên thanh toán Stripe", null);
    }

    @Transactional
    public void handleStripeWebhook(String payload, String signature) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret chưa được cấu hình");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Thiếu Stripe-Signature");
        }

        try {
            Event event = Webhook.constructEvent(payload, signature, stripeWebhookSecret);
            if (paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", event.getId())) {
                return;
            }

            String eventType = event.getType();
            if (!"checkout.session.completed".equals(eventType)
                    && !"checkout.session.expired".equals(eventType)
                    && !"checkout.session.async_payment_failed".equals(eventType)) {
                return;
            }

            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (!(stripeObject instanceof Session session)) {
                return;
            }

            Payment payment = resolveAndValidatePayment(
                    session,
                    "checkout.session.completed".equals(eventType),
                    true,
                    true);
            saveTransaction(payment, "STRIPE_WEBHOOK_EVENT", "RECEIVED", event.getId());

            if ("checkout.session.completed".equals(eventType)) {
                processCompletedWebhook(payment, session, event.getId());
            } else {
                processFailedWebhook(payment, eventType, event.getId());
            }
        } catch (Exception ex) {
            throw new RuntimeException("Webhook Stripe không hợp lệ", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getRecentPayments() {
        return paymentRepository.findTop100ByOrderByCreatedAtDesc();
    }
    
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Payment> getPaymentsPage(org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public long countPendingPayments() {
        return paymentRepository.countByStatus(PaymentStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> getRecentTransactions() {
        return paymentTransactionRepository.findTop200ByOrderByCreatedAtDesc();
    }

    private PaymentMethod parseMethod(String method) {
        try {
            return PaymentMethod.valueOf(method == null ? "STRIPE" : method.toUpperCase());
        } catch (Exception e) {
            return PaymentMethod.STRIPE;
        }
    }

    private void saveTransaction(Payment payment, String type, String status, String payload) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPayment(payment);
        tx.setTransactionType(type);
        tx.setStatus(status);
        tx.setPayload(limitLength(payload, MAX_TRANSACTION_PAYLOAD_LENGTH));
        paymentTransactionRepository.save(tx);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void processCompletedWebhook(Payment payment, Session session, String eventId) {
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe session chưa ở trạng thái paid");
        }

        finalizeSuccessfulPayment(payment, "STRIPE_WEBHOOK_PROCESS", eventId);
    }

    private boolean finalizeSuccessfulPayment(Payment payment, String transactionType, String payload) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, transactionType, "SKIPPED", "ALREADY_SUCCESS:" + payload);
            orderService.markOrderPaid(payment.getOrder().getId(), payment.getPaymentReference());
            return false;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayResponse(transactionType.contains("VNPAY") ? "VNPAY_PAYMENT_SUCCESS" : "STRIPE_PAYMENT_SUCCESS");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, transactionType, "SUCCESS", payload);
        orderService.markOrderPaid(payment.getOrder().getId(), payment.getPaymentReference());
        return true;
    }

    /**
     * Handles the customer's browser redirect from VNPay. Unexpected exceptions are not caught here: they roll the
     * transaction back, so the payment and the order can never end up half-updated. The caller reports the failure.
     */
    @Transactional
    public PaymentCallbackResult handleVnPayReturn(Map<String, String> callbackParams, Long userId) {
        if (!vnPayService.isValidSignature(callbackParams)) {
            return new PaymentCallbackResult(false, "Chữ ký bảo mật VNPay không hợp lệ", null);
        }

        // Check ownership before any state change, so a valid return URL cannot settle someone else's order.
        Payment payment = resolveAndValidateVnPayPayment(callbackParams, true);
        if (!isOrderOwner(payment, userId)) {
            return new PaymentCallbackResult(false, "Không thể truy cập đơn hàng thanh toán", null);
        }

        String payload = vnPayService.compactPayload(callbackParams);
        saveTransaction(payment, "VNPAY_RETURN", "RECEIVED", payload);

        if (vnPayService.isSuccessfulResponse(callbackParams)) {
            boolean finalizedNow = finalizeSuccessfulPayment(payment, "VNPAY_RETURN", payload);
            String message = finalizedNow ? "Thanh toán qua cổng VNPay thành công" : "Thanh toán đã được xác nhận";
            return new PaymentCallbackResult(true, message, payment.getOrder().getId());
        }

        processVnPayFailure(payment, callbackParams, "VNPAY_RETURN", payload);
        return new PaymentCallbackResult(false, mapVnPayFailureMessage(callbackParams), payment.getOrder().getId());
    }

    /**
     * Server-to-server notification from VNPay. Responds with RspCode 00 (confirmed), 02 (already confirmed),
     * 01 (order not found), 04 (amount mismatch), 97 (bad checksum) or 99 (unexpected error, transaction rolled back
     * so VNPay retries the notification).
     */
    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> callbackParams) {
        if (!vnPayService.isValidSignature(callbackParams)) {
            return ipnResponse("97", "Invalid checksum");
        }

        Payment payment;
        try {
            payment = resolveAndValidateVnPayPayment(callbackParams, true);
        } catch (VnPayCallbackException ex) {
            // Rejected before any write, so there is nothing to roll back.
            return ipnResponse(ex.getRspCode(), ex.getMessage());
        }

        String payload = vnPayService.compactPayload(callbackParams);
        saveTransaction(payment, "VNPAY_IPN", "RECEIVED", payload);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, "VNPAY_IPN", "SKIPPED", "ALREADY_SUCCESS:" + payload);
            return ipnResponse("02", "Order already confirmed");
        }

        if (vnPayService.isSuccessfulResponse(callbackParams)) {
            finalizeSuccessfulPayment(payment, "VNPAY_IPN", payload);
        } else {
            processVnPayFailure(payment, callbackParams, "VNPAY_IPN", payload);
        }
        return ipnResponse("00", "Confirm Success");
    }

    private Map<String, String> ipnResponse(String rspCode, String message) {
        return Map.of("RspCode", rspCode, "Message", message);
    }

    private Payment resolveAndValidateVnPayPayment(Map<String, String> callbackParams, boolean lockForUpdate) {
        String txnRef = vnPayService.getTxnRef(callbackParams);
        if (txnRef == null || txnRef.isBlank()) {
            throw new VnPayCallbackException("01", "VNPay callback thiếu vnp_TxnRef");
        }

        Payment payment = paymentRepository.findByPaymentReference(txnRef)
                .orElseThrow(() -> new VnPayCallbackException("01", "Không tìm thấy payment theo txnRef VNPay: " + txnRef));

        if (lockForUpdate) {
            payment = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new VnPayCallbackException("01", "Payment không tồn tại khi xử lý VNPay callback"));
        }

        if (payment.getMethod() != PaymentMethod.VNPAY) {
            throw new VnPayCallbackException("01", "Payment method không phải VNPay");
        }

        // Compare with the amount that was sent to VNPay when the payment was created.
        long callbackAmount = vnPayService.getAmount(callbackParams);
        long expectedAmount = vnPayService.toVnPayAmount(payment.getAmount());
        if (callbackAmount != expectedAmount) {
            throw new VnPayCallbackException("04", "Số tiền VNPay không khớp tổng đơn hàng");
        }

        String gatewaySessionId = payment.getGatewaySessionId();
        if (gatewaySessionId == null || gatewaySessionId.isBlank()) {
            payment.setGatewaySessionId(txnRef);
            payment.setUpdatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
        } else if (!Objects.equals(gatewaySessionId, txnRef)) {
            throw new VnPayCallbackException("01", "Mismatched txnRef giữa VNPay và DB");
        }

        return payment;
    }

    /**
     * Cancels VNPay payments that never received a result within the timeout, releasing their reserved stock.
     * Returns the number of payments expired in this run. Each payment is expired in its own transaction.
     */
    public int expireStaleVnPayPayments(LocalDateTime createdBefore) {
        List<Long> candidateIds = paymentRepository.findExpirableIds(
                PaymentMethod.VNPAY, PaymentStatus.PENDING, createdBefore, PageRequest.of(0, MAX_EXPIRY_BATCH_SIZE));
        int expired = 0;
        for (Long paymentId : candidateIds) {
            try {
                if (self.expireVnPayPaymentIfPending(paymentId)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.error("Could not expire VNPay payment {}; the next run retries it", paymentId, ex);
            }
        }
        return expired;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireVnPayPaymentIfPending(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment không tồn tại: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setGatewayResponse("VNPAY_EXPIRED");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, "VNPAY_EXPIRE", "CANCELLED", "Không nhận được kết quả từ VNPay trong thời gian chờ");
        orderService.cancelPendingOrder(payment.getOrder().getId());
        return true;
    }

    private void processVnPayFailure(Payment payment, Map<String, String> callbackParams, String transactionType, String payload) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, transactionType, "SKIPPED", "ALREADY_SUCCESS:" + payload);
            return;
        }

        String responseCode = safeValue(callbackParams, "vnp_ResponseCode");
        String transactionStatus = safeValue(callbackParams, "vnp_TransactionStatus");
        boolean cancelled = "24".equals(responseCode) || "24".equals(transactionStatus);

        PaymentStatus targetStatus = cancelled ? PaymentStatus.CANCELLED : PaymentStatus.FAILED;
        payment.setStatus(targetStatus);
        payment.setGatewayResponse("VNPAY_" + (responseCode == null ? "UNKNOWN" : responseCode));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, transactionType, targetStatus.name(), payload);
        orderService.cancelPendingOrder(payment.getOrder().getId());
    }

    private boolean isOrderOwner(Payment payment, Long userId) {
        if (userId == null) {
            return true;
        }
        return payment.getOrder().getUser() != null && Objects.equals(payment.getOrder().getUser().getId(), userId);
    }

    private String mapVnPayFailureMessage(Map<String, String> callbackParams) {
        String responseCode = safeValue(callbackParams, "vnp_ResponseCode");
        if ("24".equals(responseCode)) {
            return "Bạn đã hủy giao dịch VNPay";
        }
        if ("00".equals(responseCode)) {
            return "Giao dịch VNPay không thành công";
        }
        if (responseCode == null || responseCode.isBlank()) {
            return "Thanh toán VNPay thất bại";
        }
        return "Thanh toán VNPay thất bại (mã " + responseCode + ")";
    }

    private String safeValue(Map<String, String> values, String key) {
        if (values == null) {
            return null;
        }
        String value = values.get(key);
        return value == null ? null : value.trim();
    }

    private void processFailedWebhook(Payment payment, String eventType, String eventId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, "STRIPE_WEBHOOK_PROCESS", "SKIPPED", "ALREADY_SUCCESS:" + eventId);
            return;
        }

        PaymentStatus targetStatus = "checkout.session.expired".equals(eventType)
                ? PaymentStatus.CANCELLED
                : PaymentStatus.FAILED;

        payment.setStatus(targetStatus);
        payment.setGatewayResponse("STRIPE_" + eventType.toUpperCase().replace('.', '_'));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, "STRIPE_WEBHOOK_PROCESS", targetStatus.name(), eventId);
        orderService.cancelPendingOrder(payment.getOrder().getId());
    }

    private Payment resolveAndValidatePayment(Session session,
                                             boolean requirePaid,
                                             boolean allowSessionBinding,
                                             boolean lockForUpdate) {
        if (session == null || session.getId() == null || session.getId().isBlank()) {
            throw new IllegalArgumentException("Stripe session không hợp lệ");
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) {
            throw new IllegalStateException("Thiếu metadata trong Stripe session");
        }

        String metadataOrderId = metadata.get("orderId");
        String metadataPaymentReference = metadata.get("paymentReference");
        if (metadataOrderId == null || metadataPaymentReference == null) {
            throw new IllegalStateException("Metadata Stripe thiếu thông tin bắt buộc");
        }

        Payment payment = paymentRepository.findByGatewaySessionId(session.getId())
                .orElseGet(() -> paymentRepository.findByPaymentReference(metadataPaymentReference)
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy payment theo session Stripe")));

        if (lockForUpdate) {
            payment = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment không tồn tại khi xử lý webhook"));
        }

        Long metadataOrderIdLong;
        try {
            metadataOrderIdLong = Long.parseLong(metadataOrderId);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("orderId metadata không hợp lệ");
        }

        if (!Objects.equals(metadataOrderIdLong, payment.getOrder().getId())) {
            throw new IllegalStateException("Mismatched orderId giữa Stripe metadata và DB");
        }

        if (!Objects.equals(metadataPaymentReference, payment.getPaymentReference())) {
            throw new IllegalStateException("Mismatched paymentReference giữa Stripe metadata và DB");
        }

        long expectedAmountTotal = toStripeAmount(payment.getOrder().getTotalPrice(), payment.getCurrency());
        Long stripeAmountTotal = session.getAmountTotal();
        if (stripeAmountTotal == null || stripeAmountTotal.longValue() != expectedAmountTotal) {
            throw new IllegalStateException("Số tiền Stripe không khớp tổng đơn hàng");
        }

        String stripeCurrency = session.getCurrency();
        if (stripeCurrency == null || !payment.getCurrency().equalsIgnoreCase(stripeCurrency)) {
            throw new IllegalStateException("Loại tiền Stripe không hợp lệ");
        }

        if (requirePaid && !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Phiên Stripe chưa thanh toán thành công");
        }

        String gatewaySessionId = payment.getGatewaySessionId();
        if (gatewaySessionId == null || gatewaySessionId.isBlank()) {
            if (!allowSessionBinding) {
                throw new IllegalStateException("Session Stripe không khớp với payment đã lưu");
            }
            payment.setGatewaySessionId(session.getId());
            payment.setUpdatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
        } else if (!Objects.equals(gatewaySessionId, session.getId())) {
            throw new IllegalStateException("Mismatched sessionId giữa Stripe và DB");
        }

        return payment;
    }

    private long toStripeAmount(BigDecimal amount, String currency) {
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase();
        BigDecimal stripeAmount = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)
                ? amount
                : amount.multiply(BigDecimal.valueOf(100));
        return stripeAmount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
