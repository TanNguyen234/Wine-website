package com.strongwine.strongwine.service;

import com.strongwine.strongwine.controller.AdminOrderController;
import com.strongwine.strongwine.controller.OrderController;
import com.strongwine.strongwine.controller.api.OrderApiController;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.event.OrderPaidEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCodSettlementTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void orderServiceImplementsCodSettlementPort() {
        assertThat(orderService).isInstanceOf(CodSettlementPort.class);
    }

    @Test
    void settleCodOrder_pendingOrderWithExistingPayment_updatesOrderPaymentInventoryAuditAndPublishesEvent() {
        User customer = new User();
        customer.setId(7L);
        customer.setUsername("khachhang7");

        Order order = new Order();
        order.setId(10L);
        order.setUser(customer);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalPrice(new BigDecimal("1500000.00"));

        Payment payment = new Payment();
        payment.setId(55L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("1500000.00"));
        payment.setPaymentReference("COD-REF-10");

        when(orderRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(List.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.settleCodOrder(10L, "shipper01");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getPaymentReference()).isEqualTo("COD-REF-10");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository).save(payment);

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo("COD_COLLECTED");
        assertThat(txCaptor.getValue().getStatus()).isEqualTo("SUCCESS");

        verify(inventoryService).confirmOrderPayment(order);
        verify(adminAuditLogService).log(eq("shipper01"), eq("SETTLE_COD"), eq("ORDER"), eq(10L), any(), any(), isNull());

        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderStatusUpdatedEvent.class);
        assertThat(eventCaptor.getValue()).isNotInstanceOf(OrderPaidEvent.class);
    }

    @Test
    void settleCodOrder_whenNoExistingPaymentRecord_createsNewCodPaymentAndTransaction() {
        User customer = new User();
        customer.setId(7L);
        Order order = new Order();
        order.setId(11L);
        order.setUser(customer);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalPrice(new BigDecimal("2200000.00"));

        when(orderRepository.findByIdWithPessimisticLock(11L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(11L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.settleCodOrder(11L, "admin01");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment created = paymentCaptor.getValue();
        assertThat(created.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(created.getAmount()).isEqualByComparingTo("2200000.00");
        assertThat(created.getPaymentReference()).startsWith("COD-");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(inventoryService).confirmOrderPayment(order);
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void settleCodOrder_rejectsNonCodAlreadySettledRefundedCancelledAndMissingOrders() {
        Order stripeOrder = new Order();
        stripeOrder.setId(12L);
        stripeOrder.setPaymentMethod(PaymentMethod.STRIPE);
        stripeOrder.setStatus(OrderStatus.PENDING);
        stripeOrder.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdWithPessimisticLock(12L)).thenReturn(Optional.of(stripeOrder));

        assertThatThrownBy(() -> orderService.settleCodOrder(12L, "admin01"))
                .isInstanceOf(IllegalStateException.class);

        Order paidCod = new Order();
        paidCod.setId(13L);
        paidCod.setPaymentMethod(PaymentMethod.COD);
        paidCod.setStatus(OrderStatus.PAID);
        paidCod.setPaymentStatus(PaymentStatus.SUCCESS);
        when(orderRepository.findByIdWithPessimisticLock(13L)).thenReturn(Optional.of(paidCod));

        assertThatThrownBy(() -> orderService.settleCodOrder(13L, "admin01"))
                .isInstanceOf(IllegalStateException.class);

        Order cancelledCod = new Order();
        cancelledCod.setId(14L);
        cancelledCod.setPaymentMethod(PaymentMethod.COD);
        cancelledCod.setStatus(OrderStatus.CANCELLED);
        cancelledCod.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdWithPessimisticLock(14L)).thenReturn(Optional.of(cancelledCod));

        assertThatThrownBy(() -> orderService.settleCodOrder(14L, "admin01"))
                .isInstanceOf(IllegalStateException.class);

        when(orderRepository.findByIdWithPessimisticLock(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.settleCodOrder(999L, "admin01"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryService, never()).confirmOrderPayment(any());
        verify(paymentRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void orderDetailBindsDecryptedOtpOnlyForOwnerCustomerNeverAdmin_andAdminSettleCodDelegatesCleanly() {
        User owner = new User();
        owner.setId(5L);
        owner.setUsername("owner5");
        owner.setRole("USER");

        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin1");
        admin.setRole("ADMIN");

        Order order = new Order();
        order.setId(42L);
        order.setUser(owner);

        Shipment shipment = new Shipment();
        shipment.setId(77L);
        shipment.setOrder(order);

        OrderService mockedOrderService = mock(OrderService.class);
        UserRepository mockedUserRepo = mock(UserRepository.class);
        ShipmentOrderLifecyclePort mockedShipmentPort = mock(ShipmentOrderLifecyclePort.class);

        when(mockedUserRepo.findByUsername("owner5")).thenReturn(Optional.of(owner));
        when(mockedUserRepo.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(mockedOrderService.getOrderByIdForUser(42L, 5L)).thenReturn(Optional.of(order));
        when(mockedOrderService.getOrderById(42L)).thenReturn(Optional.of(order));
        when(mockedShipmentPort.getShipmentByOrderId(42L)).thenReturn(Optional.of(shipment));
        when(mockedShipmentPort.decryptOtpForOrderView(shipment)).thenReturn(Optional.of("654321"));

        OrderController orderController = new OrderController();
        ReflectionTestUtils.setField(orderController, "orderService", mockedOrderService);
        ReflectionTestUtils.setField(orderController, "userRepository", mockedUserRepo);
        ReflectionTestUtils.setField(orderController, "shipmentOrderLifecyclePort", mockedShipmentPort);

        ExtendedModelMap customerModel = new ExtendedModelMap();
        String customerView = orderController.orderDetail(42L, new TestingAuthenticationToken("owner5", "pw", "ROLE_USER"), customerModel);
        assertThat(customerView).isEqualTo("order-detail");
        assertThat(customerModel.getAttribute("isAdmin")).isEqualTo(false);
        assertThat(customerModel.getAttribute("decryptedOtp")).isEqualTo("654321");

        ExtendedModelMap adminModel = new ExtendedModelMap();
        String adminView = orderController.orderDetail(42L, new TestingAuthenticationToken("admin1", "pw", "ROLE_ADMIN"), adminModel);
        assertThat(adminView).isEqualTo("order-detail");
        assertThat(adminModel.getAttribute("isAdmin")).isEqualTo(true);
        assertThat(adminModel.containsAttribute("decryptedOtp")).isFalse();

        AdminOrderController adminOrderController = new AdminOrderController(mockedOrderService);
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String redirect = adminOrderController.settleCodOrder(42L, new TestingAuthenticationToken("admin1", "pw", "ROLE_ADMIN"), flash);
        assertThat(redirect).isEqualTo("redirect:/admin/orders");
        verify(mockedOrderService).settleCodOrder(42L, "admin1");

        when(mockedOrderService.reorderOrder(42L, 5L)).thenReturn(
                new OrderService.ReorderSummary(42L, 1, Map.of(10L, 2), List.of(), List.of("Old Wine"))
        );
        RedirectAttributesModelMap reorderFlash = new RedirectAttributesModelMap();
        String reorderView = orderController.reorderOrder(42L, new TestingAuthenticationToken("owner5", "pw", "ROLE_USER"), null, reorderFlash);
        assertThat(reorderView).isEqualTo("redirect:/cart");
        assertThat(reorderFlash.getFlashAttributes()).containsKey("message").containsKey("warning");

        OrderApiController apiController = new OrderApiController();
        ReflectionTestUtils.setField(apiController, "orderService", mockedOrderService);
        ReflectionTestUtils.setField(apiController, "userRepository", mockedUserRepo);
        ResponseEntity<Map<String, Object>> apiResponse = apiController.reorderOrder(42L, new TestingAuthenticationToken("owner5", "pw", "ROLE_USER"));
        assertThat(apiResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(apiResponse.getBody()).containsEntry("addedCount", 1).containsEntry("skippedCount", 1);
    }
}
