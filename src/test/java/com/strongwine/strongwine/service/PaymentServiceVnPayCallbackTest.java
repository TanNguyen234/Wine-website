package com.strongwine.strongwine.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceVnPayCallbackTest {

    private static final String TXN_REF = "PAY-0123456789ABCDEF";
    private static final String AMOUNT_OK = "123400000";

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

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(paymentService, "paymentTransactionRepository", paymentTransactionRepository);
        ReflectionTestUtils.setField(paymentService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(paymentService, "orderService", orderService);
        ReflectionTestUtils.setField(paymentService, "vnPayService", VnPayTestSupport.configuredService());
        ReflectionTestUtils.setField(paymentService, "adminAuditLogService", adminAuditLogService);
        ReflectionTestUtils.setField(paymentService, "applicationEventPublisher", applicationEventPublisher);
    }

    private Payment pendingVnPayPayment(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setUsername("customer" + ownerId);

        Order order = new Order();
        order.setId(42L);
        order.setUser(owner);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalPrice(new BigDecimal("1234000.00"));

        Payment payment = new Payment();
        payment.setId(7L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("1234000.00"));
        payment.setCurrency("VND");
        payment.setPaymentReference(TXN_REF);
        payment.setGatewaySessionId(TXN_REF);
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }

    private Map<String, String> callback(String responseCode, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Thanh toan don hang 42");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", VnPayTestSupport.TMN_CODE);
        params.put("vnp_TransactionNo", "13800000");
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_PayDate", "20261111120000");
        params.put("vnp_SecureHash", VnPayTestSupport.sign(params));
        return params;
    }

    private void givenPaymentFound(Payment payment) {
        when(paymentRepository.findByPaymentReference(TXN_REF)).thenReturn(Optional.of(payment));
        lenient().when(orderRepository.findByIdWithPessimisticLock(payment.getOrder().getId()))
                .thenReturn(Optional.of(payment.getOrder()));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void ipnWithInvalidChecksumIsRejectedWithoutTouchingData() {
        Map<String, String> tampered = callback("00", AMOUNT_OK);
        tampered.put("vnp_Amount", "1");

        assertThat(paymentService.handleVnPayIpn(tampered)).containsEntry("RspCode", "97");
        verifyNoInteractions(paymentRepository, orderService, paymentTransactionRepository);
    }

    @Test
    void ipnForUnknownTransactionReturnsOrderNotFound() {
        when(paymentRepository.findByPaymentReference(TXN_REF)).thenReturn(Optional.empty());

        assertThat(paymentService.handleVnPayIpn(callback("00", AMOUNT_OK))).containsEntry("RspCode", "01");
        verify(orderService, never()).markOrderPaid(any(), any());
    }

    @Test
    void ipnWithWrongAmountReturnsInvalidAmountAndChangesNothing() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);

        assertThat(paymentService.handleVnPayIpn(callback("00", "100"))).containsEntry("RspCode", "04");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(orderService, never()).markOrderPaid(any(), any());
    }

    @Test
    void ipnSuccessConfirmsOrderOnceAndRepeatIsAcknowledgedAsAlreadyConfirmed() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);

        assertThat(paymentService.handleVnPayIpn(callback("00", AMOUNT_OK))).containsEntry("RspCode", "00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService).markOrderPaid(42L, TXN_REF);

        assertThat(paymentService.handleVnPayIpn(callback("00", AMOUNT_OK))).containsEntry("RspCode", "02");
        verify(orderService, times(1)).markOrderPaid(42L, TXN_REF);
    }

    @Test
    void returnFromAnotherCustomerDoesNotSettleTheOrder() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);

        PaymentCallbackResult result = paymentService.handleVnPayReturn(callback("00", AMOUNT_OK), 99L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(orderService, paymentTransactionRepository);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void returnFromOwnerWithSuccessCodeConfirmsPayment() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);

        PaymentCallbackResult result = paymentService.handleVnPayReturn(callback("00", AMOUNT_OK), 5L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderId()).isEqualTo(42L);
        verify(orderService).markOrderPaid(42L, TXN_REF);
    }

    @Test
    void returnWithCancelCodeCancelsOrderThroughOrderService() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);

        PaymentCallbackResult result = paymentService.handleVnPayReturn(callback("24", AMOUNT_OK), 5L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(orderService).cancelPendingOrder(42L);
    }

    @Test
    void expiryCancelsOnlyPaymentsThatAreStillPending() {
        Payment stale = pendingVnPayPayment(5L);
        Payment settled = pendingVnPayPayment(6L);
        settled.setId(8L);
        settled.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findExpirableIds(eq(PaymentMethod.VNPAY), eq(PaymentStatus.PENDING),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(7L, 8L));
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(stale));
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(settled));
        lenient().when(orderRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(stale.getOrder()));
        // The batch method calls the transactional proxy through "self"; in this unit test the service calls itself.
        ReflectionTestUtils.setField(paymentService, "self", paymentService);

        int expired = paymentService.expireStaleVnPayPayments(LocalDateTime.now().minusMinutes(60));

        assertThat(expired).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService, times(1)).cancelPendingOrder(42L);
    }

    @Test
    void returnWithNullUserIdIsRejectedWithoutSettlingOrder() {
        Payment payment = pendingVnPayPayment(5L);

        PaymentCallbackResult result = paymentService.handleVnPayReturn(callback("00", AMOUNT_OK), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(orderService, paymentTransactionRepository);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void ipnWithMismatchedTmnCodeIsRejectedWithoutModifyingPayment() {
        Map<String, String> wrongTmn = callback("00", AMOUNT_OK);
        wrongTmn.put("vnp_TmnCode", "WRONGTMN");
        wrongTmn.put("vnp_SecureHash", VnPayTestSupport.sign(wrongTmn));

        assertThat(paymentService.handleVnPayIpn(wrongTmn)).containsEntry("RspCode", "97");
        verifyNoInteractions(paymentRepository, orderService, paymentTransactionRepository);
    }

    @Test
    void ipnWithBlankTransactionStatusDoesNotConfirmOrder() {
        Payment payment = pendingVnPayPayment(5L);
        givenPaymentFound(payment);
        Map<String, String> incomplete = callback("00", AMOUNT_OK);
        incomplete.put("vnp_TransactionStatus", "");
        incomplete.put("vnp_SecureHash", VnPayTestSupport.sign(incomplete));

        paymentService.handleVnPayIpn(incomplete);

        verify(orderService, never()).markOrderPaid(any(), any());
    }

    @Test
    void ipnWithDecimal18LuxuryAmountAcquiresOrderAndPaymentLockInOrder() {
        Payment payment = pendingVnPayPayment(5L);
        BigDecimal luxuryAmount = new BigDecimal("150000000.00");
        payment.setAmount(luxuryAmount);
        payment.getOrder().setTotalPrice(luxuryAmount);
        givenPaymentFound(payment);

        Map<String, String> params = callback("00", "15000000000");
        assertThat(paymentService.handleVnPayIpn(params)).containsEntry("RspCode", "00");

        InOrder lockOrder = inOrder(orderRepository, paymentRepository, orderService);
        lockOrder.verify(orderRepository).findByIdWithPessimisticLock(42L);
        lockOrder.verify(paymentRepository).findByIdForUpdate(7L);
        lockOrder.verify(orderService).markOrderPaid(42L, TXN_REF);
    }

    @Test
    void ipnOnCancelledOrderMarksRefundRequiredAndPublishesAdminAlertWithoutResurrection() {
        Payment payment = pendingVnPayPayment(5L);
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.getOrder().setStatus(OrderStatus.CANCELLED);
        payment.getOrder().setPaymentStatus(PaymentStatus.CANCELLED);
        givenPaymentFound(payment);

        Map<String, String> result = paymentService.handleVnPayIpn(callback("00", AMOUNT_OK));

        assertThat(result).containsEntry("RspCode", "00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(payment.getOrder());
        verify(orderService, never()).markOrderPaid(any(), any());
        verify(adminAuditLogService).log(eq("system"), eq("LATE_PAYMENT_REFUND_REQUIRED"), eq("Payment"), eq(7L), any(), any(), eq(null));

        ArgumentCaptor<AdminAlertEvent> eventCaptor = ArgumentCaptor.forClass(AdminAlertEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReferenceOrderId()).isEqualTo(42L);
        assertThat(eventCaptor.getValue().getSeverity()).isEqualTo("WARN");
        verify(applicationEventPublisher, never()).publishEvent(any(OrderPaidEvent.class));

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, times(2)).save(txCaptor.capture());
        assertThat(txCaptor.getAllValues())
                .anySatisfy(tx -> {
                    assertThat(tx.getStatus()).isEqualTo("REFUND_REQUIRED");
                    assertThat(tx.getPayload()).contains("LATE_PAYMENT_AFTER_CANCELLATION");
                });

        // Replay IPN on REFUND_REQUIRED returns 02 idempotently without re-alerting
        Map<String, String> replayResult = paymentService.handleVnPayIpn(callback("00", AMOUNT_OK));
        assertThat(replayResult).containsEntry("RspCode", "02");
        verifyNoMoreInteractions(applicationEventPublisher);
    }
}
