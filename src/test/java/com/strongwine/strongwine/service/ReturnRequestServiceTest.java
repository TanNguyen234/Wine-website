package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.entity.ReturnRequest;
import com.strongwine.strongwine.entity.ReturnStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.PaymentTransactionRepository;
import com.strongwine.strongwine.repository.ReturnRequestRepository;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ReturnRequestService returnRequestService;

    private Order deliveredOrder(Long orderId, Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setUsername("customer" + ownerId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setName("HCM Vault");

        Wine wine = new Wine();
        wine.setId(21L);
        wine.setName("Opus One 2018");
        wine.setPrice(new BigDecimal("4500000.00"));

        Order order = new Order();
        order.setId(orderId);
        order.setUser(owner);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.STRIPE);
        order.setTotalPrice(new BigDecimal("9000000.00"));
        order.setAllocatedWarehouse(warehouse);

        order.getOrderItems().add(new OrderItem(order, wine, 2, new BigDecimal("4500000.00")));
        return order;
    }

    @Test
    void requestReturn_byOwnerOnDeliveredOrder_createsRequestTransitionsOrderAndPublishesAlert() {
        Order order = deliveredOrder(100L, 5L);
        Shipment shipment = new Shipment();
        shipment.setStatus(ShipmentStatus.COMPLETED);

        when(orderRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(order));
        when(shipmentOrderLifecyclePort.getShipmentByOrderId(100L)).thenReturn(Optional.of(shipment));
        when(returnRequestRepository.existsByOrderIdAndStatusIn(100L, List.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED)))
                .thenReturn(false);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest rr = inv.getArgument(0);
            rr.setId(501L);
            return rr;
        });

        ReturnRequest created = returnRequestService.requestReturn(100L, 5L, "Chai vang bị nứt nút bần");

        assertThat(created.getId()).isEqualTo(501L);
        assertThat(created.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(created.getRefundAmount()).isEqualByComparingTo("9000000.00");
        assertThat(created.getReason()).isEqualTo("Chai vang bị nứt nút bần");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);

        verify(orderRepository).save(order);
        verify(applicationEventPublisher).publishEvent(any(AdminAlertEvent.class));
    }

    @Test
    void requestReturn_rejectsNonOwnerUndeliveredOrDuplicateOpenRequest() {
        Order order = deliveredOrder(101L, 5L);
        when(orderRepository.findByIdWithPessimisticLock(101L)).thenReturn(Optional.of(order));

        // Wrong owner
        assertThatThrownBy(() -> returnRequestService.requestReturn(101L, 999L, "Lý do"))
                .isInstanceOf(SecurityException.class);

        // Shipment not completed yet
        Shipment activeShipment = new Shipment();
        activeShipment.setStatus(ShipmentStatus.DELIVERING);
        when(shipmentOrderLifecyclePort.getShipmentByOrderId(101L)).thenReturn(Optional.of(activeShipment));
        assertThatThrownBy(() -> returnRequestService.requestReturn(101L, 5L, "Lý do"))
                .isInstanceOf(IllegalStateException.class);

        // Duplicate open request
        Shipment completed = new Shipment();
        completed.setStatus(ShipmentStatus.COMPLETED);
        when(shipmentOrderLifecyclePort.getShipmentByOrderId(101L)).thenReturn(Optional.of(completed));
        when(returnRequestRepository.existsByOrderIdAndStatusIn(101L, List.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED)))
                .thenReturn(true);

        assertThatThrownBy(() -> returnRequestService.requestReturn(101L, 5L, "Trùng lặp"))
                .isInstanceOf(IllegalStateException.class);

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void approveAndRejectReturn_updateStatusesRestoreOrderOnRejectAndAudit() {
        Order order = deliveredOrder(102L, 5L);
        order.setStatus(OrderStatus.RETURN_REQUESTED);

        ReturnRequest pending = new ReturnRequest();
        pending.setId(601L);
        pending.setOrder(order);
        pending.setUser(order.getUser());
        pending.setStatus(ReturnStatus.REQUESTED);
        pending.setRefundAmount(new BigDecimal("9000000.00"));
        pending.setReason("Vỡ chai");

        when(returnRequestRepository.findById(601L)).thenReturn(Optional.of(pending));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnRequest approved = returnRequestService.approveReturn(601L, "admin01", "Đã kiểm tra ảnh");
        assertThat(approved.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(approved.getResolvedBy()).isEqualTo("admin01");
        assertThat(approved.getResolvedAt()).isNotNull();
        verify(adminAuditLogService).log(eq("admin01"), eq("APPROVE_RETURN"), eq("RETURN_REQUEST"), eq(601L), any(), any(), isNull());

        // Separate requested item for reject flow
        Order order2 = deliveredOrder(103L, 5L);
        order2.setStatus(OrderStatus.RETURN_REQUESTED);
        ReturnRequest toReject = new ReturnRequest();
        toReject.setId(602L);
        toReject.setOrder(order2);
        toReject.setUser(order2.getUser());
        toReject.setStatus(ReturnStatus.REQUESTED);
        toReject.setRefundAmount(new BigDecimal("4500000.00"));

        when(returnRequestRepository.findById(602L)).thenReturn(Optional.of(toReject));
        when(orderRepository.findByIdWithPessimisticLock(103L)).thenReturn(Optional.of(order2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnRequest rejected = returnRequestService.rejectReturn(602L, "admin01", "Quá hạn đổi trả");
        assertThat(rejected.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(rejected.getAdminNote()).isEqualTo("Quá hạn đổi trả");
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(adminAuditLogService).log(eq("admin01"), eq("REJECT_RETURN"), eq("RETURN_REQUEST"), eq(602L), any(), any(), isNull());
    }

    @Test
    void completeRefund_restocksInventoryUpdatesPaymentAndOrderRecordsTransactionAndPublishesEvent() {
        Order order = deliveredOrder(104L, 5L);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);

        ReturnRequest approved = new ReturnRequest();
        approved.setId(603L);
        approved.setOrder(order);
        approved.setUser(order.getUser());
        approved.setStatus(ReturnStatus.APPROVED);
        approved.setRefundAmount(new BigDecimal("9000000.00"));

        Payment payment = new Payment();
        payment.setId(77L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.STRIPE);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("9000000.00"));
        payment.setPaymentReference("PAY-104");

        when(returnRequestRepository.findById(603L)).thenReturn(Optional.of(approved));
        when(orderRepository.findByIdWithPessimisticLock(104L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(104L)).thenReturn(List.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnRequest refunded = returnRequestService.completeRefund(603L, "admin01", "Hoàn tiền thành công");

        assertThat(refunded.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(refunded.getResolvedBy()).isEqualTo("admin01");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        verify(inventoryService).importStock(eq(21L), eq(3L), eq(2), eq("admin01"), any(String.class));

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo("REFUND");
        assertThat(txCaptor.getValue().getStatus()).isEqualTo("SUCCESS");

        verify(adminAuditLogService).log(eq("admin01"), eq("REFUND_RETURN"), eq("RETURN_REQUEST"), eq(603L), any(), any(), isNull());
        verify(applicationEventPublisher).publishEvent(any(OrderStatusUpdatedEvent.class));
    }
}
