package com.strongwine.strongwine.service;

import com.strongwine.strongwine.controller.AdminPaymentController;
import com.strongwine.strongwine.controller.PaymentController;
import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import com.strongwine.strongwine.service.StripeService.StripeNotConfiguredException;
import com.strongwine.strongwine.service.StripeService.StripeWebhookVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookAndRefundRequiredTest {

    private static final String WEBHOOK_SECRET = "whsec_test_strongwine_secret_123";
    private static final String STRIPE_SECRET = "sk_test_strongwine_secret_123";
    private static final String PAYMENT_REF = "PAY-STRIPE0000123456";
    private static final String SESSION_ID = "cs_test_a1b2c3d4";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StripeService mockStripeService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(paymentService, "paymentTransactionRepository", paymentTransactionRepository);
        ReflectionTestUtils.setField(paymentService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(paymentService, "orderService", orderService);
        ReflectionTestUtils.setField(paymentService, "stripeService", mockStripeService);
        ReflectionTestUtils.setField(paymentService, "adminAuditLogService", adminAuditLogService);
        ReflectionTestUtils.setField(paymentService, "applicationEventPublisher", applicationEventPublisher);
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", WEBHOOK_SECRET);
    }

    @Test
    @DisplayName("StripeService.init() does not crash when stripe.secretKey is blank and sets configured=false")
    void stripeInitWithBlankSecretKeySetsConfiguredFalseWithoutCrashing() {
        StripeService stripeService = new StripeService();
        ReflectionTestUtils.setField(stripeService, "stripeSecretKey", "   ");
        ReflectionTestUtils.setField(stripeService, "stripeWebhookSecret", "");

        assertThatCode(stripeService::init).doesNotThrowAnyException();
        assertThat(stripeService.isConfigured()).isFalse();

        Payment payment = stripePayment(OrderStatus.PENDING, PaymentStatus.PENDING, new BigDecimal("1500000.00"));
        assertThatThrownBy(() -> stripeService.createCheckoutSession(payment, "http://localhost:8080"))
                .isInstanceOf(StripeNotConfiguredException.class);
        assertThatThrownBy(() -> stripeService.retrieveSession(SESSION_ID))
                .isInstanceOf(StripeNotConfiguredException.class);
        assertThatThrownBy(() -> stripeService.constructWebhookEvent("{}", "t=1,v1=abc"))
                .isInstanceOf(StripeNotConfiguredException.class);
    }

    @Test
    @DisplayName("StripeService.constructWebhookEvent verifies real signature and rejects bad signature or malformed JSON")
    void stripeConstructWebhookEventVerifiesSignatureAndRejectsInvalidInputs() {
        StripeService stripeService = configuredRealStripeService();
        String payload = checkoutCompletedEventJson("evt_real_1", SESSION_ID, 42L, PAYMENT_REF, 1500000L, "paid");
        String validSig = signStripePayload(payload, WEBHOOK_SECRET);

        Event event = stripeService.constructWebhookEvent(payload, validSig);
        assertThat(event.getId()).isEqualTo("evt_real_1");
        assertThat(event.getType()).isEqualTo("checkout.session.completed");

        assertThatThrownBy(() -> stripeService.constructWebhookEvent(payload, "t=1700000000,v1=deadbeef"))
                .isInstanceOf(StripeWebhookVerificationException.class);

        String malformed = "{invalid-json";
        String sigForMalformed = signStripePayload(malformed, WEBHOOK_SECRET);
        assertThatThrownBy(() -> stripeService.constructWebhookEvent(malformed, sigForMalformed))
                .isInstanceOf(StripeWebhookVerificationException.class);
    }

    @Test
    @DisplayName("POST /payment/webhook returns 400 on missing/invalid signature or malformed JSON and 200 on valid event")
    void paymentControllerWebhookValidatesSignatureAndPayload() throws Exception {
        StripeService realStripeService = configuredRealStripeService();
        PaymentService mockPaymentService = org.mockito.Mockito.mock(PaymentService.class);

        PaymentController controller = new PaymentController();
        ReflectionTestUtils.setField(controller, "paymentService", mockPaymentService);
        ReflectionTestUtils.setField(controller, "stripeService", realStripeService);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String validPayload = checkoutCompletedEventJson("evt_ctrl_1", SESSION_ID, 42L, PAYMENT_REF, 1500000L, "paid");
        String validSig = signStripePayload(validPayload, WEBHOOK_SECRET);

        // Missing Stripe-Signature -> 400
        mockMvc.perform(post("/payment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isBadRequest());

        // Blank Stripe-Signature -> 400
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isBadRequest());

        // Invalid Stripe-Signature -> 400
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", "t=1700000000,v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isBadRequest());

        // Malformed JSON even with signature -> 400
        String malformed = "{bad_json";
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", signStripePayload(malformed, WEBHOOK_SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformed))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mockPaymentService);

        // Valid event -> 200 ok
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", validSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(mockPaymentService, times(1)).handleStripeWebhookEvent(any(Event.class));
    }

    @Test
    @DisplayName("Stripe checkout.session.completed acquires pessimistic locks (Order then Payment), compares DECIMAL(18,2), and is idempotent")
    void stripeWebhookSuccessLocksOrderAndPaymentAndIsIdempotent() {
        BigDecimal luxuryTotal = new BigDecimal("150000000.00");
        Payment payment = stripePayment(OrderStatus.PENDING, PaymentStatus.PENDING, luxuryTotal);
        Order order = payment.getOrder();

        String payload = checkoutCompletedEventJson("evt_paid_1", SESSION_ID, 42L, PAYMENT_REF, 150000000L, "paid");
        Event event = configuredRealStripeService().constructWebhookEvent(payload, signStripePayload(payload, WEBHOOK_SECRET));
        when(mockStripeService.constructWebhookEvent(payload, "valid-sig")).thenReturn(event);
        when(paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", "evt_paid_1"))
                .thenReturn(false);
        when(paymentRepository.findByGatewaySessionId(SESSION_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));

        paymentService.handleStripeWebhook(payload, "valid-sig");

        InOrder lockOrder = inOrder(orderRepository, paymentRepository, orderService);
        lockOrder.verify(orderRepository).findByIdWithPessimisticLock(42L);
        lockOrder.verify(paymentRepository).findByIdForUpdate(7L);
        lockOrder.verify(orderService).markOrderPaid(42L, PAYMENT_REF);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Second webhook with a different event id while payment is already SUCCESS must be idempotent
        String replayPayload = checkoutCompletedEventJson("evt_paid_2", SESSION_ID, 42L, PAYMENT_REF, 150000000L, "paid");
        Event replayEvent = configuredRealStripeService().constructWebhookEvent(replayPayload, signStripePayload(replayPayload, WEBHOOK_SECRET));
        when(paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", "evt_paid_2"))
                .thenReturn(false);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);

        paymentService.handleStripeWebhookEvent(replayEvent);

        verify(orderService, times(1)).markOrderPaid(42L, PAYMENT_REF);
    }

    @Test
    @DisplayName("Stripe webhook rejects DECIMAL(18,2) amount mismatch cleanly without marking paid")
    void stripeWebhookRejectsAmountMismatch() {
        Payment payment = stripePayment(OrderStatus.PENDING, PaymentStatus.PENDING, new BigDecimal("150000000.00"));
        Order order = payment.getOrder();

        String payload = checkoutCompletedEventJson("evt_bad_amt", SESSION_ID, 42L, PAYMENT_REF, 1500000L, "paid");
        Event event = configuredRealStripeService().constructWebhookEvent(payload, signStripePayload(payload, WEBHOOK_SECRET));
        when(paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", "evt_bad_amt"))
                .thenReturn(false);
        when(paymentRepository.findByGatewaySessionId(SESSION_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.handleStripeWebhookEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Số tiền Stripe");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderService, never()).markOrderPaid(any(), any());
    }

    @Test
    @DisplayName("Late Stripe webhook on CANCELLED order marks REFUND_REQUIRED, keeps CANCELLED, logs audit & publishes AdminAlertEvent without stock deduction")
    void lateStripeWebhookOnCancelledOrderMarksRefundRequiredWithoutResurrection() {
        Payment payment = stripePayment(OrderStatus.CANCELLED, PaymentStatus.CANCELLED, new BigDecimal("2500000.00"));
        Order order = payment.getOrder();

        String payload = checkoutCompletedEventJson("evt_late_1", SESSION_ID, 42L, PAYMENT_REF, 2500000L, "paid");
        Event event = configuredRealStripeService().constructWebhookEvent(payload, signStripePayload(payload, WEBHOOK_SECRET));
        when(paymentTransactionRepository.existsByTransactionTypeAndPayload("STRIPE_WEBHOOK_EVENT", "evt_late_1"))
                .thenReturn(false);
        when(paymentRepository.findByGatewaySessionId(SESSION_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));

        paymentService.handleStripeWebhookEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
        verify(orderService, never()).markOrderPaid(any(), any());

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, times(2)).save(txCaptor.capture());
        assertThat(txCaptor.getAllValues())
                .anySatisfy(tx -> {
                    assertThat(tx.getStatus()).isEqualTo("REFUND_REQUIRED");
                    assertThat(tx.getPayload()).contains("LATE_PAYMENT_AFTER_CANCELLATION");
                });

        verify(adminAuditLogService).log(
                eq("system"),
                eq("LATE_PAYMENT_REFUND_REQUIRED"),
                eq("Payment"),
                eq(7L),
                any(),
                any(),
                eq(null)
        );

        ArgumentCaptor<AdminAlertEvent> eventCaptor = ArgumentCaptor.forClass(AdminAlertEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        AdminAlertEvent alert = eventCaptor.getValue();
        assertThat(alert.getReferenceOrderId()).isEqualTo(42L);
        assertThat(alert.getSeverity()).isEqualTo("WARN");
        verify(applicationEventPublisher, never()).publishEvent(any(OrderPaidEvent.class));
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("Late Stripe redirect on CANCELLED order marks REFUND_REQUIRED and returns failure PaymentCallbackResult")
    void lateStripeRedirectOnCancelledOrderMarksRefundRequired() throws Exception {
        Payment payment = stripePayment(OrderStatus.CANCELLED, PaymentStatus.CANCELLED, new BigDecimal("2500000.00"));
        Order order = payment.getOrder();

        Session session = new Session();
        session.setId(SESSION_ID);
        session.setAmountTotal(2500000L);
        session.setCurrency("vnd");
        session.setPaymentStatus("paid");
        session.setMetadata(Map.of("orderId", "42", "paymentReference", PAYMENT_REF));

        when(mockStripeService.retrieveSession(SESSION_ID)).thenReturn(session);
        when(paymentRepository.findByGatewaySessionId(SESSION_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));

        PaymentCallbackResult result = paymentService.validateStripeSuccessRedirect(SESSION_ID, 5L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOrderId()).isEqualTo(42L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        verify(orderService, never()).markOrderPaid(any(), any());
        verify(applicationEventPublisher).publishEvent(any(AdminAlertEvent.class));
    }

    @Test
    @DisplayName("Admin manual refund resolution transitions REFUND_REQUIRED -> REFUNDED and writes audit log")
    void adminManualRefundResolutionMarksRefundedAndAudits() {
        Payment payment = stripePayment(OrderStatus.CANCELLED, PaymentStatus.REFUND_REQUIRED, new BigDecimal("2500000.00"));
        Order order = payment.getOrder();
        order.setPaymentStatus(PaymentStatus.REFUND_REQUIRED);

        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));

        AdminPaymentController adminController = new AdminPaymentController(paymentService);
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = adminController.markPaymentRefunded(
                7L,
                "REFUND-BANK-999",
                "Da chuyen khoan hoan tien cho khach",
                new UsernamePasswordAuthenticationToken("admin.ops", "pw"),
                null,
                flash
        );

        assertThat(view).isEqualTo("redirect:/admin/payments");
        assertThat(flash.getFlashAttributes()).containsKey("success");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(adminAuditLogService).log(
                eq("admin.ops"),
                eq("RESOLVE_PAYMENT_REFUND"),
                eq("Payment"),
                eq(7L),
                any(),
                any(),
                eq(null)
        );
    }

    @Test
    @DisplayName("Scheduler expires both stale VNPay and Stripe payments without crashing on single failure")
    void schedulerExpiresBothVnPayAndStripePaymentsSafely() {
        PaymentService mockService = org.mockito.Mockito.mock(PaymentService.class);
        doThrow(new RuntimeException("transient DB error"))
                .when(mockService).expireStaleVnPayPayments(any(LocalDateTime.class));
        when(mockService.expireStaleStripePayments(any(LocalDateTime.class))).thenReturn(2);

        com.strongwine.strongwine.config.VnPayPendingPaymentExpiryScheduler scheduler =
                new com.strongwine.strongwine.config.VnPayPendingPaymentExpiryScheduler(mockService);

        assertThatCode(scheduler::expireAbandonedVnPayPayments).doesNotThrowAnyException();
        verify(mockService).expireStaleVnPayPayments(any(LocalDateTime.class));
        verify(mockService).expireStaleStripePayments(any(LocalDateTime.class));
    }

    private StripeService configuredRealStripeService() {
        StripeService stripeService = new StripeService();
        ReflectionTestUtils.setField(stripeService, "stripeSecretKey", STRIPE_SECRET);
        ReflectionTestUtils.setField(stripeService, "stripeWebhookSecret", WEBHOOK_SECRET);
        stripeService.init();
        return stripeService;
    }

    private Payment stripePayment(OrderStatus orderStatus, PaymentStatus paymentStatus, BigDecimal total) {
        User owner = new User();
        owner.setId(5L);
        owner.setUsername("customer5");

        Order order = new Order();
        order.setId(42L);
        order.setUser(owner);
        order.setStatus(orderStatus);
        order.setPaymentStatus(paymentStatus);
        order.setTotalPrice(total);

        Payment payment = new Payment();
        payment.setId(7L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.STRIPE);
        payment.setStatus(paymentStatus);
        payment.setAmount(total);
        payment.setCurrency("VND");
        payment.setPaymentReference(PAYMENT_REF);
        payment.setGatewaySessionId(SESSION_ID);
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }

    private String checkoutCompletedEventJson(String eventId,
                                            String sessionId,
                                            Long orderId,
                                            String paymentRef,
                                            long amountTotal,
                                            String paymentStatus) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2023-10-16",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "amount_total": %d,
                      "currency": "vnd",
                      "payment_status": "%s",
                      "metadata": {
                        "orderId": "%d",
                        "paymentReference": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, sessionId, amountTotal, paymentStatus, orderId, paymentRef);
    }

    private String signStripePayload(String payload, String secret) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
