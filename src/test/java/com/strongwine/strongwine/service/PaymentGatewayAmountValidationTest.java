package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayAmountValidationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StripeService stripeService;

    @Mock
    private VNPayService vnPayService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(paymentService, "paymentTransactionRepository", paymentTransactionRepository);
        ReflectionTestUtils.setField(paymentService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(paymentService, "stripeService", stripeService);
        ReflectionTestUtils.setField(paymentService, "vnPayService", vnPayService);
        ReflectionTestUtils.setField(paymentService, "self", paymentService);
    }

    @Test
    void rejectsStripeBelowMinimumBeforeCreatingPaymentOrCallingStripe() {
        Order zeroOrder = orderWithTotal("0");
        Order tinyOrder = orderWithTotal("10000");

        assertThatThrownBy(() -> paymentService.createPaymentSession(zeroOrder, "STRIPE", "http://localhost:8080", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15.000");

        assertThatThrownBy(() -> paymentService.createPaymentSession(tinyOrder, "STRIPE", "http://localhost:8080", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15.000");

        verifyNoInteractions(stripeService, paymentRepository, paymentTransactionRepository);
    }

    @Test
    void rejectsVnPayOutsideSupportedGatewayBoundsBeforeGeneratingUrl() {
        Order tooLow = orderWithTotal("4000");
        Order tooHigh = orderWithTotal("1000000000");

        assertThatThrownBy(() -> paymentService.createPaymentSession(tooLow, "VNPAY", "http://localhost:8080", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5.000");

        assertThatThrownBy(() -> paymentService.createPaymentSession(tooHigh, "VNPAY", "http://localhost:8080", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.000.000.000");

        verifyNoInteractions(vnPayService, paymentRepository, paymentTransactionRepository);
    }

    @Test
    void allowsStripeAtMinimumAndVnPayWithinRange() throws Exception {
        Order stripeMinOrder = orderWithTotal("15000");
        Order vnPayMinOrder = orderWithTotal("5000");

        when(orderRepository.findByIdWithPessimisticLock(99L)).thenReturn(Optional.of(stripeMinOrder), Optional.of(vnPayMinOrder));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(501L);
            }
            return saved;
        });
        when(paymentRepository.findById(501L)).thenAnswer(inv -> {
            Payment existing = new Payment();
            existing.setId(501L);
            existing.setOrder(stripeMinOrder);
            existing.setAmount(new BigDecimal("15000"));
            existing.setCurrency("VND");
            return Optional.of(existing);
        });
        when(vnPayService.createPaymentUrl(any(), anyString(), anyString())).thenReturn("https://sandbox.vnpayment.vn/test");

        com.stripe.model.checkout.Session session = new com.stripe.model.checkout.Session();
        session.setId("cs_test_min");
        session.setUrl("https://checkout.stripe.com/c/pay/cs_test_min");
        when(stripeService.createCheckoutSession(any(), anyString())).thenReturn(session);

        assertThat(paymentService.createPaymentSession(stripeMinOrder, "STRIPE", "http://localhost:8080", "127.0.0.1"))
                .contains("checkout.stripe.com");
        assertThat(paymentService.createPaymentSession(vnPayMinOrder, "VNPAY", "http://localhost:8080", "127.0.0.1"))
                .contains("sandbox.vnpayment.vn");
    }

    private Order orderWithTotal(String amount) {
        Order order = new Order();
        order.setId(99L);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalPrice(new BigDecimal(amount));
        return order;
    }
}
