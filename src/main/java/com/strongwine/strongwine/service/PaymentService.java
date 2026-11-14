package com.strongwine.strongwine.service;

import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.exception.VnPayCallbackException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.service.StripeService.StripeWebhookVerificationException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private static final BigDecimal STRIPE_MIN_VND = new BigDecimal("15000");
    private static final BigDecimal VNPAY_MIN_VND = new BigDecimal("5000");
    private static final BigDecimal VNPAY_MAX_VND = new BigDecimal("1000000000");

    public static void validateGatewayAmount(String method, BigDecimal amount) {
        if (method == null || method.isBlank() || "COD".equalsIgnoreCase(method.trim())) {
            return;
        }
        try {
            validateGatewayAmount(PaymentMethod.valueOf(method.trim().toUpperCase()), amount);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Thanh toán qua ")) {
                throw ex;
            }
            throw new IllegalArgumentException("Phương thức thanh toán chưa được hỗ trợ: " + method, ex);
        }
    }

    public static void validateGatewayAmount(PaymentMethod paymentMethod, BigDecimal amount) {
        if (paymentMethod == null || paymentMethod == PaymentMethod.COD) {
            return;
        }
        BigDecimal total = amount != null ? amount : BigDecimal.ZERO;
        if (paymentMethod == PaymentMethod.STRIPE && total.compareTo(STRIPE_MIN_VND) < 0) {
            throw new IllegalArgumentException(
                    "Thanh toán qua Stripe yêu cầu tổng đơn hàng tối thiểu 15.000đ. Vui lòng chọn COD cho đơn hàng giá trị nhỏ hơn.");
        }
        if (paymentMethod == PaymentMethod.VNPAY
                && (total.compareTo(VNPAY_MIN_VND) < 0 || total.compareTo(VNPAY_MAX_VND) >= 0)) {
            throw new IllegalArgumentException(
                    "Thanh toán qua VNPay yêu cầu tổng đơn hàng từ 5.000đ đến dưới 1.000.000.000đ. Vui lòng chọn COD cho đơn hàng ngoài mức này.");
        }
    }

    private enum FinalizationOutcome {
        FINALIZED,
        ALREADY_CONFIRMED,
        REFUND_REQUIRED
    }

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

    @Autowired(required = false)
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

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

        validateGatewayAmount(paymentMethod, order != null ? order.getTotalPrice() : null);

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
        Order lockedOrder = lockOrder(order);
        if (lockedOrder.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("Đơn hàng #" + lockedOrder.getId() + " đã được thanh toán");
        }
        if (lockedOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Đơn hàng #" + lockedOrder.getId() + " đã bị hủy");
        }
        if (lockedOrder.getId() != null) {
            List<Payment> existingPayments = paymentRepository.findByOrderIdForUpdate(lockedOrder.getId());
            if (existingPayments != null) {
                LocalDateTime now = LocalDateTime.now();
                for (Payment existing : existingPayments) {
                    if (existing.getStatus() == PaymentStatus.PENDING) {
                        existing.setStatus(PaymentStatus.CANCELLED);
                        existing.setGatewayResponse("SUPERSEDED_BY_NEW_PAYMENT");
                        existing.setUpdatedAt(now);
                        paymentRepository.save(existing);
                        saveTransaction(existing, "PAYMENT_SUPERSEDED", "CANCELLED",
                                "Superseded by " + paymentMethod.name() + " for order " + lockedOrder.getId());
                    }
                }
            }
        }

        Payment payment = new Payment();
        payment.setOrder(lockedOrder);
        payment.setMethod(paymentMethod);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(lockedOrder.getTotalPrice());
        payment.setCurrency("VND");
        payment.setPaymentReference("PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment.setGatewaySessionId(null);
        payment.setGatewayResponse("SESSION_CREATED");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        saveTransaction(saved, "SESSION_CREATED", "PENDING", "Order " + lockedOrder.getId());
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
        if (userId == null) {
            return new PaymentCallbackResult(false, "Vui lòng đăng nhập để xác thực thanh toán", null);
        }
        try {
            Session session = stripeService.retrieveSession(stripeSessionId);
            LockedOrderPayment locked = resolveAndValidatePayment(session, true, true, true);
            Payment payment = locked.payment();
            Order order = locked.order();

            if (!isOrderOwner(order, userId)) {
                return new PaymentCallbackResult(false, "Không thể truy cập đơn hàng thanh toán", null);
            }

            FinalizationOutcome outcome = finalizeSuccessfulPayment(
                    payment, order, "STRIPE_SUCCESS_REDIRECT", session.getId());
            return switch (outcome) {
                case FINALIZED -> new PaymentCallbackResult(true, "Thanh toán thành công", order.getId());
                case ALREADY_CONFIRMED -> new PaymentCallbackResult(true, "Thanh toán đã được xác nhận", order.getId());
                case REFUND_REQUIRED -> new PaymentCallbackResult(
                        false,
                        "Đơn hàng đã bị hủy trước khi thanh toán hoàn tất. Giao dịch đã được ghi nhận để hoàn tiền thủ công.",
                        order.getId());
            };
        } catch (Exception ex) {
            return new PaymentCallbackResult(false, "Xác thực Stripe thất bại: " + ex.getMessage(), null);
        }
    }

    public PaymentCallbackResult handleStripeCancel() {
        return new PaymentCallbackResult(false, "Bạn đã hủy phiên thanh toán Stripe", null);
    }

    @Transactional
    public void handleStripeWebhook(String payload, String signature) {
        Event event;
        if (stripeService != null) {
            event = stripeService.constructWebhookEvent(payload, signature);
        } else {
            if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
                throw new IllegalStateException("Stripe webhook secret chưa được cấu hình");
            }
            if (signature == null || signature.isBlank()) {
                throw new StripeWebhookVerificationException("Thiếu Stripe-Signature");
            }
            try {
                event = Webhook.constructEvent(payload, signature, stripeWebhookSecret);
            } catch (Exception ex) {
                throw new StripeWebhookVerificationException("Webhook Stripe không hợp lệ", ex);
            }
        }
        handleStripeWebhookEvent(event);
    }

    @Transactional
    public void handleStripeWebhookEvent(Event event) {
        if (event == null || event.getId() == null || event.getId().isBlank()) {
            throw new StripeWebhookVerificationException("Stripe webhook event không hợp lệ");
        }
        if (paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", event.getId())) {
            return;
        }

        String eventType = event.getType();
        if (!"checkout.session.completed".equals(eventType)
                && !"checkout.session.expired".equals(eventType)
                && !"checkout.session.async_payment_failed".equals(eventType)) {
            return;
        }

        Session session = extractSessionFromEvent(event);
        LockedOrderPayment locked = resolveAndValidatePayment(
                session,
                "checkout.session.completed".equals(eventType),
                true,
                true);
        Payment payment = locked.payment();
        Order order = locked.order();
        saveTransaction(payment, "STRIPE_WEBHOOK_EVENT", "RECEIVED", event.getId());

        if ("checkout.session.completed".equals(eventType)) {
            processCompletedWebhook(payment, order, session, event.getId());
        } else {
            processFailedWebhook(payment, order, eventType, event.getId());
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
    public List<Payment> getRefundRequiredPayments() {
        return paymentRepository.findByStatus(PaymentStatus.REFUND_REQUIRED);
    }

    @Transactional(readOnly = true)
    public long countRefundRequiredPayments() {
        return paymentRepository.countByStatus(PaymentStatus.REFUND_REQUIRED);
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

    private void processCompletedWebhook(Payment payment, Order order, Session session, String eventId) {
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe session chưa ở trạng thái paid");
        }

        finalizeSuccessfulPayment(payment, order, "STRIPE_WEBHOOK_PROCESS", eventId);
    }

    private FinalizationOutcome finalizeSuccessfulPayment(Payment payment,
                                                            Order lockedOrder,
                                                            String transactionType,
                                                            String payload) {
        Order order = lockedOrder != null ? lockedOrder : lockOrder(payment.getOrder());
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, transactionType, "SKIPPED", "ALREADY_SUCCESS:" + payload);
            return FinalizationOutcome.ALREADY_CONFIRMED;
        }
        if (payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            saveTransaction(payment, transactionType, "SKIPPED",
                    "ALREADY_" + payment.getStatus().name() + ":" + payload);
            return FinalizationOutcome.REFUND_REQUIRED;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            recordLateOrDuplicatePaymentRefundRequired(
                    payment,
                    order,
                    transactionType,
                    payload,
                    "LATE_PAYMENT_REFUND_REQUIRED",
                    "LATE_PAYMENT_AFTER_CANCELLATION",
                    "Cần hoàn tiền giao dịch trễ",
                    "Đơn hàng #" + order.getId() + " đã hủy trước khi nhận thanh toán thành công");
            return FinalizationOutcome.REFUND_REQUIRED;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            recordLateOrDuplicatePaymentRefundRequired(
                    payment,
                    order,
                    transactionType,
                    payload,
                    "DUPLICATE_PAYMENT_REFUND_REQUIRED",
                    "DUPLICATE_PAYMENT_ON_" + order.getStatus().name() + "_ORDER",
                    "Cần hoàn tiền giao dịch trùng lặp",
                    "Đơn hàng #" + order.getId() + " đã ở trạng thái " + order.getStatus().name()
                            + " nhưng phát sinh giao dịch thanh toán thành công thứ hai");
            return FinalizationOutcome.REFUND_REQUIRED;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayResponse(transactionType.contains("VNPAY") ? "VNPAY_PAYMENT_SUCCESS" : "STRIPE_PAYMENT_SUCCESS");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, transactionType, "SUCCESS", payload);
        orderService.markOrderPaid(order.getId(), payment.getPaymentReference());
        return FinalizationOutcome.FINALIZED;
    }

    private void recordLateOrDuplicatePaymentRefundRequired(Payment payment,
                                                            Order order,
                                                            String transactionType,
                                                            String payload,
                                                            String auditAction,
                                                            String reasonPrefix,
                                                            String alertTitlePrefix,
                                                            String alertDetail) {
        Map<String, Object> beforeState = paymentStateSnapshot(payment, order);
        String gatewayPrefix = transactionType.contains("VNPAY") ? "VNPAY" : "STRIPE";

        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setGatewayResponse(limitLength(gatewayPrefix + "_" + reasonPrefix, MAX_GATEWAY_RESPONSE_LENGTH));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            order.setPaymentStatus(PaymentStatus.REFUND_REQUIRED);
            if (orderRepository != null) {
                orderRepository.save(order);
            }
        }

        saveTransaction(payment, transactionType, "REFUND_REQUIRED",
                reasonPrefix + ":" + (payload == null ? "" : payload));

        if (adminAuditLogService != null) {
            adminAuditLogService.log(
                    "system",
                    auditAction,
                    "Payment",
                    payment.getId(),
                    beforeState,
                    paymentStateSnapshot(payment, order),
                    null
            );
        }

        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new AdminAlertEvent(
                    this,
                    alertTitlePrefix + " #" + payment.getId(),
                    alertDetail + " (" + payment.getMethod().name() + " - " + payment.getPaymentReference() + ").",
                    "/admin/payments",
                    "WARN",
                    order.getId()
            ));
        }
    }

    @Transactional
    public Payment markPaymentRefunded(Long paymentId,
                                       String actorUsername,
                                       String externalRefundRef,
                                       String refundNote,
                                       String ipAddress) {
        Payment lookup = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch #" + paymentId));

        Order lockedOrder = lockOrder(lookup.getOrder());
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch #" + paymentId));

        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalStateException("Chỉ có thể đánh dấu hoàn tiền cho giao dịch ở trạng thái REFUND_REQUIRED");
        }

        Map<String, Object> beforeState = paymentStateSnapshot(payment, lockedOrder);
        String cleanRef = (externalRefundRef == null || externalRefundRef.isBlank())
                ? "MANUAL_ADMIN_REFUND"
                : externalRefundRef.trim();
        String cleanNote = refundNote == null ? "" : refundNote.trim();

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setGatewayResponse(limitLength("MANUAL_REFUND:" + cleanRef, MAX_GATEWAY_RESPONSE_LENGTH));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        if (lockedOrder.getPaymentStatus() == PaymentStatus.REFUND_REQUIRED) {
            lockedOrder.setPaymentStatus(PaymentStatus.REFUNDED);
            if (orderRepository != null) {
                orderRepository.save(lockedOrder);
            }
        }

        String txDetail = "REF=" + cleanRef + (cleanNote.isEmpty() ? "" : "; NOTE=" + cleanNote);
        saveTransaction(payment, "MANUAL_REFUND", "REFUNDED", txDetail);

        String actor = (actorUsername == null || actorUsername.isBlank()) ? "admin" : actorUsername.trim();
        if (adminAuditLogService != null) {
            Map<String, Object> afterState = new LinkedHashMap<>(paymentStateSnapshot(payment, lockedOrder));
            afterState.put("externalRefundRef", cleanRef);
            if (!cleanNote.isEmpty()) {
                afterState.put("refundNote", cleanNote);
            }
            adminAuditLogService.log(
                    actor,
                    "RESOLVE_PAYMENT_REFUND",
                    "Payment",
                    payment.getId(),
                    beforeState,
                    afterState,
                    ipAddress
            );
        }
        return payment;
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
        if (userId == null) {
            return new PaymentCallbackResult(false, "Không thể truy cập đơn hàng thanh toán", null);
        }

        // Check ownership before any state change, so a valid return URL cannot settle someone else's order.
        LockedOrderPayment locked = resolveAndValidateVnPayPayment(callbackParams, true);
        Payment payment = locked.payment();
        Order order = locked.order();
        if (!isOrderOwner(order, userId)) {
            return new PaymentCallbackResult(false, "Không thể truy cập đơn hàng thanh toán", null);
        }

        String payload = vnPayService.compactPayload(callbackParams);
        saveTransaction(payment, "VNPAY_RETURN", "RECEIVED", payload);

        if (vnPayService.isSuccessfulResponse(callbackParams)) {
            FinalizationOutcome outcome = finalizeSuccessfulPayment(payment, order, "VNPAY_RETURN", payload);
            return switch (outcome) {
                case FINALIZED -> new PaymentCallbackResult(true, "Thanh toán qua cổng VNPay thành công", order.getId());
                case ALREADY_CONFIRMED -> new PaymentCallbackResult(true, "Thanh toán đã được xác nhận", order.getId());
                case REFUND_REQUIRED -> new PaymentCallbackResult(
                        false,
                        "Đơn hàng đã bị hủy trước khi thanh toán hoàn tất. Giao dịch đã được ghi nhận để hoàn tiền thủ công.",
                        order.getId());
            };
        }

        processVnPayFailure(payment, order, callbackParams, "VNPAY_RETURN", payload);
        return new PaymentCallbackResult(false, mapVnPayFailureMessage(callbackParams), order.getId());
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

        LockedOrderPayment locked;
        try {
            locked = resolveAndValidateVnPayPayment(callbackParams, true);
        } catch (VnPayCallbackException ex) {
            // Rejected before any write, so there is nothing to roll back.
            return ipnResponse(ex.getRspCode(), ex.getMessage());
        }

        Payment payment = locked.payment();
        Order order = locked.order();
        String payload = vnPayService.compactPayload(callbackParams);
        saveTransaction(payment, "VNPAY_IPN", "RECEIVED", payload);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            saveTransaction(payment, "VNPAY_IPN", "SKIPPED", "ALREADY_SUCCESS:" + payload);
            return ipnResponse("02", "Order already confirmed");
        }
        if (payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            saveTransaction(payment, "VNPAY_IPN", "SKIPPED", "ALREADY_" + payment.getStatus().name() + ":" + payload);
            return ipnResponse("02", "Order already confirmed");
        }

        if (vnPayService.isSuccessfulResponse(callbackParams)) {
            finalizeSuccessfulPayment(payment, order, "VNPAY_IPN", payload);
        } else {
            processVnPayFailure(payment, order, callbackParams, "VNPAY_IPN", payload);
        }
        return ipnResponse("00", "Confirm Success");
    }

    private Map<String, String> ipnResponse(String rspCode, String message) {
        return Map.of("RspCode", rspCode, "Message", message);
    }

    private LockedOrderPayment resolveAndValidateVnPayPayment(Map<String, String> callbackParams, boolean lockForUpdate) {
        String txnRef = vnPayService.getTxnRef(callbackParams);
        if (txnRef == null || txnRef.isBlank()) {
            throw new VnPayCallbackException("01", "VNPay callback thiếu vnp_TxnRef");
        }

        Payment payment = paymentRepository.findByPaymentReference(txnRef)
                .orElseThrow(() -> new VnPayCallbackException("01", "Không tìm thấy payment theo txnRef VNPay: " + txnRef));

        Order lockedOrder = lockOrderForVnPay(payment.getOrder());

        if (lockForUpdate) {
            payment = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new VnPayCallbackException("01", "Payment không tồn tại khi xử lý VNPay callback"));
        }

        if (payment.getMethod() != PaymentMethod.VNPAY) {
            throw new VnPayCallbackException("01", "Payment method không phải VNPay");
        }

        // Compare DECIMAL(18,2) BigDecimal values cleanly without float drift.
        long callbackAmountMinor = vnPayService.getAmount(callbackParams);
        BigDecimal callbackAmount = BigDecimal.valueOf(callbackAmountMinor, 2);
        BigDecimal expectedPaymentAmount = normalizeMoney(payment.getAmount());
        if (callbackAmount.compareTo(expectedPaymentAmount) != 0) {
            throw new VnPayCallbackException("04", "Số tiền VNPay không khớp tổng đơn hàng");
        }
        if (lockedOrder.getTotalPrice() != null
                && callbackAmount.compareTo(normalizeMoney(lockedOrder.getTotalPrice())) != 0) {
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

        return new LockedOrderPayment(lockedOrder, payment);
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

    /**
     * Cancels stale Stripe checkout payments that never received completion within the timeout, releasing stock.
     */
    public int expireStaleStripePayments(LocalDateTime createdBefore) {
        List<Long> candidateIds = paymentRepository.findExpirableIds(
                PaymentMethod.STRIPE, PaymentStatus.PENDING, createdBefore, PageRequest.of(0, MAX_EXPIRY_BATCH_SIZE));
        int expired = 0;
        for (Long paymentId : candidateIds) {
            try {
                if (self.expireStripePaymentIfPending(paymentId)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.error("Could not expire Stripe payment {}; the next run retries it", paymentId, ex);
            }
        }
        return expired;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireVnPayPaymentIfPending(Long paymentId) {
        return expirePaymentIfPending(
                paymentId,
                "VNPAY_EXPIRED",
                "VNPAY_EXPIRE",
                "Không nhận được kết quả từ VNPay trong thời gian chờ");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireStripePaymentIfPending(Long paymentId) {
        return expirePaymentIfPending(
                paymentId,
                "STRIPE_EXPIRED",
                "STRIPE_EXPIRE",
                "Phiên thanh toán Stripe đã quá hạn chờ xác nhận");
    }

    private boolean expirePaymentIfPending(Long paymentId,
                                           String gatewayResponse,
                                           String transactionType,
                                           String reason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment không tồn tại: " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        Order order = lockOrder(payment.getOrder());
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setGatewayResponse(gatewayResponse);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        saveTransaction(payment, transactionType, "CANCELLED", reason);
        orderService.cancelPendingOrder(order.getId());
        return true;
    }

    private void processVnPayFailure(Payment payment,
                                     Order order,
                                     Map<String, String> callbackParams,
                                     String transactionType,
                                     String payload) {
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            saveTransaction(payment, transactionType, "SKIPPED", "ALREADY_" + payment.getStatus().name() + ":" + payload);
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
        orderService.cancelPendingOrder(order.getId());
    }

    private boolean isOrderOwner(Payment payment, Long userId) {
        return payment != null && isOrderOwner(payment.getOrder(), userId);
    }

    private boolean isOrderOwner(Order order, Long userId) {
        if (userId == null || order == null || order.getUser() == null) {
            return false;
        }
        return Objects.equals(order.getUser().getId(), userId);
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

    private void processFailedWebhook(Payment payment, Order order, String eventType, String eventId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            saveTransaction(payment, "STRIPE_WEBHOOK_PROCESS", "SKIPPED",
                    "ALREADY_" + payment.getStatus().name() + ":" + eventId);
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
        orderService.cancelPendingOrder(order.getId());
    }

    private Session extractSessionFromEvent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);
        if (stripeObject == null) {
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw new StripeWebhookVerificationException(
                        "Không thể đọc session từ Stripe webhook event " + event.getId(), ex);
            }
        }
        if (!(stripeObject instanceof Session session)) {
            throw new StripeWebhookVerificationException(
                    "Stripe webhook event " + event.getId() + " không chứa checkout session");
        }
        return session;
    }

    private LockedOrderPayment resolveAndValidatePayment(Session session,
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

        Order lockedOrder = lockOrder(payment.getOrder());
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

        if (!Objects.equals(metadataOrderIdLong, lockedOrder.getId())) {
            throw new IllegalStateException("Mismatched orderId giữa Stripe metadata và DB");
        }

        if (!Objects.equals(metadataPaymentReference, payment.getPaymentReference())) {
            throw new IllegalStateException("Mismatched paymentReference giữa Stripe metadata và DB");
        }

        BigDecimal receivedStripeAmount = fromStripeAmount(session.getAmountTotal(), payment.getCurrency());
        BigDecimal expectedOrderAmount = normalizeMoney(lockedOrder.getTotalPrice());
        BigDecimal expectedPaymentAmount = normalizeMoney(payment.getAmount());
        if (receivedStripeAmount.compareTo(expectedOrderAmount) != 0
                || receivedStripeAmount.compareTo(expectedPaymentAmount) != 0) {
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

        return new LockedOrderPayment(lockedOrder, payment);
    }

    private Order lockOrder(Order order) {
        if (order == null) {
            throw new IllegalStateException("Payment không gắn với đơn hàng hợp lệ");
        }
        if (orderRepository == null || order.getId() == null) {
            return order;
        }
        return orderRepository.findByIdWithPessimisticLock(order.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng #" + order.getId()));
    }

    private Order lockOrderForVnPay(Order order) {
        if (order == null) {
            throw new VnPayCallbackException("01", "Payment không gắn với đơn hàng hợp lệ");
        }
        if (orderRepository == null || order.getId() == null) {
            return order;
        }
        return orderRepository.findByIdWithPessimisticLock(order.getId())
                .orElseThrow(() -> new VnPayCallbackException("01", "Không tìm thấy đơn hàng #" + order.getId()));
    }

    private Map<String, Object> paymentStateSnapshot(Payment payment, Order order) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("paymentId", payment.getId());
        state.put("paymentStatus", payment.getStatus() != null ? payment.getStatus().name() : null);
        state.put("orderId", order.getId());
        state.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : null);
        state.put("orderPaymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        return state;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Thiếu giá trị số tiền hợp lệ");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal fromStripeAmount(Long amountTotal, String currency) {
        if (amountTotal == null) {
            throw new IllegalStateException("Số tiền Stripe không khớp tổng đơn hàng");
        }
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase();
        if (ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)) {
            return BigDecimal.valueOf(amountTotal).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amountTotal, 2);
    }

    private record LockedOrderPayment(Order order, Payment payment) {
    }
}
