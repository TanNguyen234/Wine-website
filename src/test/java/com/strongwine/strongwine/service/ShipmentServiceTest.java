package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.OtpDeliveryStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentOtpAuditLog;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.ShipmentStatusHistory;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.exception.OtpDeliveryException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipmentOtpAuditLogRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipmentStatusHistoryRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final Long SHIPMENT_ID = 101L;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipperRepository shipperRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipmentOtpEmailService shipmentOtpEmailService;

    @Mock
    private ShipmentOtpAuditService shipmentOtpAuditService;

    @Mock
    private ShipmentStatusHistoryService shipmentStatusHistoryService;

    @InjectMocks
    private ShipmentService shipmentService;

    @Test
    void paidOrderEventHandlerStartsANewTransactionAfterCommit() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource attributeSource = new AnnotationTransactionAttributeSource();
        TransactionAttribute attribute = attributeSource.getTransactionAttribute(
                ShipmentService.class.getMethod("handleOrderPaidEvent", Long.class, String.class),
                ShipmentService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void otpDeliveryFailuresCommitFailureStateForEverySendEntryPoint() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource attributeSource = new AnnotationTransactionAttributeSource();
        List<java.lang.reflect.Method> sendMethods = List.of(
                ShipmentService.class.getMethod("handleOrderPaidEvent", Long.class, String.class),
                ShipmentService.class.getMethod("sendOtpForShipment", Long.class, String.class, String.class),
                ShipmentService.class.getMethod("resendOtpForShipmentByAdmin", Long.class, String.class, String.class),
                ShipmentService.class.getMethod("resendOtpForShipmentByShipper", Long.class, String.class, String.class),
                ShipmentService.class.getMethod("regenerateOtpForShipment", Long.class));

        for (java.lang.reflect.Method method : sendMethods) {
            TransactionAttribute attribute = attributeSource.getTransactionAttribute(method, ShipmentService.class);

            assertThat(attribute).as(method.getName()).isNotNull();
            assertThat(attribute.rollbackOn(new OtpDeliveryException("SMTP unavailable")))
                    .as(method.getName())
                    .isFalse();
        }
    }

    @Test
    void completeDeliveryByAdminOverride_setsOverrideFlagsAndClearsOtp() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, false);
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment saved = shipmentService.completeDeliveryByAdminOverride(SHIPMENT_ID, "admin", "manual verification fallback");

        assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.COMPLETED);
        assertThat(saved.getAdminOverride()).isTrue();
        assertThat(saved.getAdminOverrideReason()).isEqualTo("manual verification fallback");
        assertThat(saved.getOtpVerified()).isFalse();
        assertThat(saved.getOtpCode()).isNull();
        verify(shipmentOtpAuditService).log(eq(saved), eq("ADMIN_OVERRIDE_COMPLETED"), eq("SUCCESS"),
                eq("manual verification fallback"), eq("source=admin-status"), eq("admin"));
    }

    @Test
    void resendOtp_blocksWhenLastSuccessfulSendIsWithinCooldown() {
        Shipment shipment = createShipment(ShipmentStatus.ASSIGNED, false);
        shipment.setOtpSentAt(LocalDateTime.now().minusSeconds(30));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.resendOtpForShipmentByAdmin(SHIPMENT_ID, "admin", "ADMIN_RESEND"))
                .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("60 giây");

        verify(shipmentOtpEmailService, never()).sendShipmentOtp(any(Shipment.class));
    }

    @Test
    void resendOtp_afterFailedDeliveryCanRetryImmediately() {
        Shipment shipment = createShipment(ShipmentStatus.ASSIGNED, false);
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentOtpEmailService.sendShipmentOtp(any(Shipment.class)))
                .thenReturn(OtpDeliveryResult.failed(3, "customer@example.com", "smtp down"))
                .thenReturn(OtpDeliveryResult.sent(1, "customer@example.com"));

        assertThatThrownBy(() -> shipmentService.resendOtpForShipmentByAdmin(SHIPMENT_ID, "admin", "ADMIN_RESEND"))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessageContaining("smtp down");

        Shipment resent = shipmentService.resendOtpForShipmentByAdmin(SHIPMENT_ID, "admin", "ADMIN_RESEND");

        assertThat(resent.getOtpDeliveryStatus()).isEqualTo(OtpDeliveryStatus.SENT);
        assertThat(resent.getOtpSentAt()).isNotNull();
        verify(shipmentOtpEmailService, times(2)).sendShipmentOtp(any(Shipment.class));
    }

    @Test
    void completeDelivery_locksOtpAfterFiveWrongAttempts() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "000000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OTP không đúng");
            assertThat(shipment.getOtpAttemptCount()).isEqualTo(i);
            assertThat(shipment.getOtpLockedUntil()).isNull();
        }

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "000000"))
                .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bị khóa 15 phút");

        assertThat(shipment.getOtpAttemptCount()).isEqualTo(5);
        assertThat(shipment.getOtpLockedUntil()).isNotNull();
        verify(shipmentOtpAuditService, times(5)).log(eq(shipment), eq("OTP_VERIFY"), eq("FAILED"),
                eq("OTP_MISMATCH"), isNull(), isNull());
    }

    @Test
    void completeDelivery_rejectsExpiredOtp() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        shipment.setOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456"))
                .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hết hạn");

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void completeDelivery_rejectsOtpUserBindingMismatch() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        shipment.setOtpUserId(999L);
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("binding");

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void completeDelivery_withValidOtp_marksCompletedAndClearsSensitiveOtp() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipperRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(shipment.getShipper()));
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.countByShipperIdAndStatusIn(eq(301L), anyList())).thenReturn(0L);

        shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456");

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.COMPLETED);
        assertThat(shipment.getOtpVerified()).isTrue();
        assertThat(shipment.getOtpCode()).isNull();
        assertThat(shipment.getOtpAttemptCount()).isZero();
        verify(shipmentOtpAuditService).log(eq(shipment), eq("OTP_VERIFIED_COMPLETED"), eq("SUCCESS"),
                eq("SHIPPER_COMPLETE"), isNull(), eq("shipper-a"));
    }

        @Test
        void dispatchAutoShipmentQueue_assignsWhenCapacityAvailableEvenIfAvailabilityFlagIsStale() {
        Shipment pendingShipment = createShipment(ShipmentStatus.PENDING_ASSIGNMENT, false);

        Shipper activeShipper = new Shipper();
        activeShipper.setId(301L);
        activeShipper.setStatus(ShipperStatus.ACTIVE);
        activeShipper.setIsAvailable(false);
        activeShipper.setMaxConcurrentShipments(1);

        when(shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT))
            .thenReturn(Optional.of(pendingShipment))
            .thenReturn(Optional.empty());
        when(shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE))
            .thenReturn(List.of(activeShipper));
        when(shipperRepository.findByIdForUpdate(301L))
            .thenReturn(Optional.of(activeShipper))
            .thenReturn(Optional.of(activeShipper));
        when(shipmentRepository.countByShipperIdAndStatusIn(eq(301L), anyList()))
            .thenReturn(0L)
            .thenReturn(1L);
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = shipmentService.dispatchAutoShipmentQueue();

        assertThat(processed).isEqualTo(1);
        assertThat(pendingShipment.getStatus()).isEqualTo(ShipmentStatus.ASSIGNED);
        assertThat(pendingShipment.getShipper()).isNotNull();
        assertThat(pendingShipment.getShipper().getId()).isEqualTo(301L);
        verify(shipperRepository, atLeastOnce()).findByStatusOrderByIdAsc(ShipperStatus.ACTIVE);
        }

        @Test
        void ensureShipmentForPaidOrder_returnsExistedWhenShipmentAlreadyPresent() {
        Order order = new Order();
        order.setId(9001L);
        order.setStatus(OrderStatus.PAID);

        Shipment existing = new Shipment();
        existing.setId(8001L);
        existing.setOrder(order);

        when(shipmentRepository.findByOrderId(9001L)).thenReturn(Optional.of(existing));

        ShipmentService.EnsureShipmentResult result = shipmentService.ensureShipmentForPaidOrder(order);

        assertThat(result).isEqualTo(ShipmentService.EnsureShipmentResult.EXISTED);
        verify(shipmentRepository, never()).save(any(Shipment.class));
        }

        @Test
        void backfillMissingShipmentsForPaidOrders_createsForPaidSuccessAndSkipsOthers() {
        Order paidSuccess = new Order();
        paidSuccess.setId(9101L);
        paidSuccess.setStatus(OrderStatus.PAID);
        paidSuccess.setPaymentStatus(PaymentStatus.SUCCESS);
        paidSuccess.setShippingFullName("Customer Backfill");
        paidSuccess.setShippingPhone("0909999999");
        paidSuccess.setShippingAddress("Backfill Address");

        Order paidPending = new Order();
        paidPending.setId(9102L);
        paidPending.setStatus(OrderStatus.PAID);
        paidPending.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findOldestOrdersWithoutShipmentByStatus(eq(OrderStatus.PAID), any()))
            .thenReturn(List.of(paidSuccess, paidPending))
            .thenReturn(List.of());
        when(shipmentRepository.findByOrderId(9101L))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty());
        when(shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE))
            .thenReturn(List.of());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipmentService.BackfillSummary summary = shipmentService.backfillMissingShipmentsForPaidOrders();

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(shipmentRepository).save(any(Shipment.class));
        }

        @Test
        void createAutoShipmentForPaidOrder_assignsImmediatelyWhenFreeShipperExists() {
        Order order = new Order();
        order.setId(9301L);
        order.setStatus(OrderStatus.PAID);
        order.setShippingFullName("Customer Auto");
        order.setShippingPhone("0901234567");
        order.setShippingAddress("123 Auto Street");

        Shipper shipper = new Shipper();
        shipper.setId(801L);
        shipper.setStatus(ShipperStatus.ACTIVE);
        shipper.setIsAvailable(false);
        shipper.setMaxConcurrentShipments(1);

        when(shipmentRepository.findByOrderId(9301L)).thenReturn(Optional.empty());
        when(shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE))
            .thenReturn(List.of(shipper));
        when(shipperRepository.findByIdForUpdate(801L))
            .thenReturn(Optional.of(shipper))
            .thenReturn(Optional.of(shipper));
        when(shipmentRepository.countByShipperIdAndStatusIn(eq(801L), anyList()))
            .thenReturn(0L)
            .thenReturn(1L);
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment created = shipmentService.createAutoShipmentForPaidOrder(order);

        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.ASSIGNED);
        assertThat(created.getShipper()).isNotNull();
        assertThat(created.getShipper().getId()).isEqualTo(801L);
        }

    @Test
    void otpStateService_recordFailedAttemptUsesRequiresNewPropagation() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource attributeSource = new AnnotationTransactionAttributeSource();
        TransactionAttribute attribute = attributeSource.getTransactionAttribute(
                ShipmentOtpStateService.class.getMethod("recordFailedOtpAttempt", Long.class),
                ShipmentOtpStateService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void verifyOtp_delegatesFailedAttemptToRequiresNewStateServiceAndThrows() {
        ShipmentOtpStateService otpStateService = mock(ShipmentOtpStateService.class);
        ShipmentOtpCryptoService cryptoService = new ShipmentOtpCryptoService("strongwine-test-secret-key-2026");
        ShipmentService service = new ShipmentService(
                shipmentRepository,
                shipperRepository,
                orderRepository,
                userRepository,
                shipmentOtpEmailService,
                shipmentOtpAuditService,
                shipmentStatusHistoryService,
                otpStateService,
                cryptoService,
                new ShippingFeeService(),
                null,
                null,
                null
        );

        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        shipment.setOtpCode(null);
        shipment.setOtpEncrypted(cryptoService.encrypt("654321"));

        Shipment updatedAttempt = new Shipment();
        updatedAttempt.setId(SHIPMENT_ID);
        updatedAttempt.setOtpAttemptCount(1);

        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(otpStateService.recordFailedOtpAttempt(SHIPMENT_ID)).thenReturn(updatedAttempt);

        assertThatThrownBy(() -> service.verifyOtp(SHIPMENT_ID, "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP không đúng");

        verify(otpStateService).recordFailedOtpAttempt(SHIPMENT_ID);
        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void generateAndSendOtp_encryptsIntoOtpEncryptedWithoutStoringPlaintextOtpCode() {
        ShipmentOtpCryptoService cryptoService = new ShipmentOtpCryptoService("strongwine-test-secret-key-2026");
        ShipmentService service = new ShipmentService(
                shipmentRepository,
                shipperRepository,
                orderRepository,
                userRepository,
                shipmentOtpEmailService,
                shipmentOtpAuditService,
                shipmentStatusHistoryService,
                null,
                cryptoService,
                new ShippingFeeService(),
                null,
                null,
                null
        );

        Shipment shipment = createShipment(ShipmentStatus.ASSIGNED, true);
        shipment.setOtpCode(null);
        shipment.setOtpEncrypted(null);
        shipment.setOtpDeliveryStatus(OtpDeliveryStatus.PENDING);

        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentOtpEmailService.sendShipmentOtp(any(Shipment.class)))
                .thenReturn(OtpDeliveryResult.sent(1, "customer@example.com"));

        Shipment saved = service.sendOtpForShipment(SHIPMENT_ID, "admin", "SEND_V3");

        assertThat(saved.getOtpCode()).isNull();
        assertThat(saved.getOtpEncrypted()).isNotBlank();
        Optional<String> decrypted = ((ShipmentOrderLifecyclePort) service).decryptOtpForOrderView(saved);
        assertThat(decrypted).isPresent();
        assertThat(decrypted.get()).matches("\\d{6}");

        Shipment corrupt = new Shipment();
        corrupt.setOtpEncrypted("not-valid-base64-cipher");
        assertThat(((ShipmentOrderLifecyclePort) service).decryptOtpForOrderView(corrupt)).isEmpty();
    }

    @Test
    void auditAndStatusHistoryServices_maskRawSixDigitOtpSecrets() {
        ShipmentOtpAuditLogRepository auditRepo = mock(ShipmentOtpAuditLogRepository.class);
        ShipmentStatusHistoryRepository historyRepo = mock(ShipmentStatusHistoryRepository.class);
        when(auditRepo.save(any(ShipmentOtpAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepo.save(any(ShipmentStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipmentOtpAuditService auditService = new ShipmentOtpAuditService(auditRepo, userRepository);
        ShipmentStatusHistoryService historyService = new ShipmentStatusHistoryService(historyRepo, userRepository);
        Shipment shipment = createShipment(ShipmentStatus.ASSIGNED, true);

        auditService.log(shipment, "OTP_VERIFY", "FAILED", "Submitted otp=482910", "raw=482910", "shipper-a");
        historyService.logTransition(shipment, ShipmentStatus.ASSIGNED, ShipmentStatus.COMPLETED,
                "Verified code 482910", "otp=482910", "shipper-a");

        ArgumentCaptor<ShipmentOtpAuditLog> auditCaptor = ArgumentCaptor.forClass(ShipmentOtpAuditLog.class);
        verify(auditRepo).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getReason()).doesNotContain("482910");
        assertThat(auditCaptor.getValue().getMetadata()).doesNotContain("482910");

        ArgumentCaptor<ShipmentStatusHistory> historyCaptor = ArgumentCaptor.forClass(ShipmentStatusHistory.class);
        verify(historyRepo).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getReason()).doesNotContain("482910");
        assertThat(historyCaptor.getValue().getMetadata()).doesNotContain("482910");
    }

    @Test
    void cancelShipmentForOrder_cancelsNonDeliveredClearsOtpClampsCountAndIsIdempotent() {
        AdminAuditLogService adminAuditLogService = mock(AdminAuditLogService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ShipmentOrderLifecyclePort port = new ShipmentService(
                shipmentRepository,
                shipperRepository,
                orderRepository,
                userRepository,
                shipmentOtpEmailService,
                shipmentOtpAuditService,
                shipmentStatusHistoryService,
                null,
                new ShipmentOtpCryptoService("strongwine-test-secret-key-2026"),
                new ShippingFeeService(),
                adminAuditLogService,
                null,
                eventPublisher
        );

        Shipment activeShipment = createShipment(ShipmentStatus.ASSIGNED, true);
        activeShipment.setOtpEncrypted("cipher-text");
        activeShipment.getShipper().setActiveShipmentCount(0); // already 0 -> verify clamping >= 0
        when(shipmentRepository.findByOrderId(401L)).thenReturn(Optional.of(activeShipment));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(activeShipment));
        when(shipperRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(activeShipment.getShipper()));
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        port.cancelShipmentForOrder(401L, "customer-a", "Customer cancelled order");

        assertThat(activeShipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(activeShipment.getOtpCode()).isNull();
        assertThat(activeShipment.getOtpEncrypted()).isNull();
        assertThat(activeShipment.getOtpExpiresAt()).isNull();
        assertThat(activeShipment.getShipper().getActiveShipmentCount()).isZero();
        assertThat(activeShipment.getShipper().getIsAvailable()).isTrue();
        verify(shipmentStatusHistoryService).logTransition(eq(activeShipment), eq(ShipmentStatus.ASSIGNED),
                eq(ShipmentStatus.CANCELLED), any(), any(), eq("customer-a"));
        verify(adminAuditLogService).log(eq("customer-a"), eq("SHIPMENT_CANCELLED"), eq("SHIPMENT"),
                eq(SHIPMENT_ID), any(), any(), isNull());

        ArgumentCaptor<ShipmentLiveStatusEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentLiveStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("CANCELLED");

        // Already COMPLETED shipment must never be cancelled
        Shipment completedShipment = createShipment(ShipmentStatus.COMPLETED, true);
        when(shipmentRepository.findByOrderId(402L)).thenReturn(Optional.of(completedShipment));
        port.cancelShipmentForOrder(402L, "customer-a", "Too late");
        assertThat(completedShipment.getStatus()).isEqualTo(ShipmentStatus.COMPLETED);
    }

    @Test
    void dispatchAutoShipmentQueue_prefersClosestShipperByHaversineAndFallsBackOnNullGps() {
        Warehouse hanoiWarehouse = new Warehouse();
        hanoiWarehouse.setId(1L);
        hanoiWarehouse.setLatitude(21.0285);
        hanoiWarehouse.setLongitude(105.8542);

        Shipment nearOrderShipment = createShipment(ShipmentStatus.PENDING_ASSIGNMENT, false);
        nearOrderShipment.getOrder().setAllocatedWarehouse(hanoiWarehouse);
        nearOrderShipment.setShippingLatitude(21.0300);
        nearOrderShipment.setShippingLongitude(105.8550);

        Shipper farShipper = new Shipper();
        farShipper.setId(1L);
        farShipper.setStatus(ShipperStatus.ACTIVE);
        farShipper.setIsAvailable(true);
        farShipper.setMaxConcurrentShipments(3);
        farShipper.setActiveShipmentCount(0);
        farShipper.setCurrentLatitude(10.7769); // HCMC
        farShipper.setCurrentLongitude(106.7009);

        Shipper nearShipper = new Shipper();
        nearShipper.setId(2L);
        nearShipper.setStatus(ShipperStatus.ACTIVE);
        nearShipper.setIsAvailable(true);
        nearShipper.setMaxConcurrentShipments(3);
        nearShipper.setActiveShipmentCount(0);
        nearShipper.setCurrentLatitude(21.0278); // Hanoi
        nearShipper.setCurrentLongitude(105.8342);

        when(shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT))
                .thenReturn(Optional.of(nearOrderShipment))
                .thenReturn(Optional.empty());
        when(shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE))
                .thenReturn(List.of(farShipper, nearShipper));
        when(shipperRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(nearShipper));
        when(shipmentRepository.countByShipperIdAndStatusIn(eq(2L), anyList())).thenReturn(0L).thenReturn(1L);
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = shipmentService.dispatchAutoShipmentQueue();

        assertThat(processed).isEqualTo(1);
        assertThat(nearOrderShipment.getShipper()).isSameAs(nearShipper);

        // Null-GPS fallback chooses lower active load shipper without NPE
        Shipment noGpsShipment = createShipment(ShipmentStatus.PENDING_ASSIGNMENT, false);
        noGpsShipment.setShippingLatitude(null);
        noGpsShipment.setShippingLongitude(null);
        noGpsShipment.getOrder().setShippingLatitude(null);
        noGpsShipment.getOrder().setShippingLongitude(null);

        Shipper busyShipper = new Shipper();
        busyShipper.setId(10L);
        busyShipper.setStatus(ShipperStatus.ACTIVE);
        busyShipper.setIsAvailable(true);
        busyShipper.setMaxConcurrentShipments(3);
        busyShipper.setActiveShipmentCount(2);

        Shipper idleShipper = new Shipper();
        idleShipper.setId(11L);
        idleShipper.setStatus(ShipperStatus.ACTIVE);
        idleShipper.setIsAvailable(true);
        idleShipper.setMaxConcurrentShipments(3);
        idleShipper.setActiveShipmentCount(0);

        when(shipmentRepository.findFirstByStatusOrderByCreatedAtAsc(ShipmentStatus.PENDING_ASSIGNMENT))
                .thenReturn(Optional.of(noGpsShipment))
                .thenReturn(Optional.empty());
        when(shipperRepository.findByStatusOrderByIdAsc(ShipperStatus.ACTIVE))
                .thenReturn(List.of(busyShipper, idleShipper));
        when(shipperRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(idleShipper));
        when(shipmentRepository.countByShipperIdAndStatusIn(eq(11L), anyList())).thenReturn(0L).thenReturn(1L);

        int fallbackProcessed = shipmentService.dispatchAutoShipmentQueue();

        assertThat(fallbackProcessed).isEqualTo(1);
        assertThat(noGpsShipment.getShipper()).isSameAs(idleShipper);
    }

    @Test
    void recordProofOfDelivery_storesProofUrlUnderShipperOwnershipCheck() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment updated = shipmentService.recordProofOfDelivery(
                SHIPMENT_ID, "shipper-a", "/uploads/pod-proof.jpg", "Left with concierge");

        assertThat(updated.getProofOfDeliveryUrl()).isEqualTo("/uploads/pod-proof.jpg");
        assertThat(updated.getStatusReason()).contains("Left with concierge");

        assertThatThrownBy(() -> shipmentService.recordProofOfDelivery(
                SHIPMENT_ID, "other-shipper", "/uploads/pod-proof.jpg", "Unauthorized"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không thuộc shipper hiện tại");
    }

    @Test
    void shipmentOrderLifecyclePort_exposesLookupMethods() {
        Shipment shipment = createShipment(ShipmentStatus.ASSIGNED, true);
        when(shipmentRepository.findByOrderIdWithShipper(401L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.findByOrderIdInWithShipper(List.of(401L))).thenReturn(List.of(shipment));

        ShipmentOrderLifecyclePort port = (ShipmentOrderLifecyclePort) shipmentService;
        assertThat(port.getShipmentByOrderId(401L)).contains(shipment);
        Map<Long, Shipment> map = port.getShipmentMapByOrderIds(List.of(401L));
        assertThat(map).containsEntry(401L, shipment);
    }

    private Shipment createShipment(ShipmentStatus status, boolean withShipper) {
        User customer = new User();
        customer.setId(201L);
        customer.setUsername("customer-a");
        customer.setEmail("customer@example.com");

        Order order = new Order();
        order.setId(401L);
        order.setUser(customer);
        order.setStatus(OrderStatus.PAID);
        order.setShippingFullName("Customer A");
        order.setShippingPhone("0900000000");
        order.setShippingAddress("123 Main Street");

        Shipment shipment = new Shipment();
        shipment.setId(SHIPMENT_ID);
        shipment.setOrder(order);
        shipment.setStatus(status);
        shipment.setShippingName("Customer A");
        shipment.setShippingPhone("0900000000");
        shipment.setShippingAddress("123 Main Street");
        shipment.setOtpCode("123456");
        shipment.setOtpCreatedAt(LocalDateTime.now().minusMinutes(1));
        shipment.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        shipment.setOtpAttemptCount(0);
        shipment.setOtpDeliveryStatus(OtpDeliveryStatus.SENT);
        shipment.setOtpUserId(customer.getId());
        shipment.setOtpVerified(false);

        if (withShipper) {
            User shipperUser = new User();
            shipperUser.setId(501L);
            shipperUser.setUsername("shipper-a");

            Shipper shipper = new Shipper();
            shipper.setId(301L);
            shipper.setUser(shipperUser);
            shipper.setStatus(ShipperStatus.ACTIVE);
            shipper.setIsAvailable(false);

            shipment.setShipper(shipper);
        }

        return shipment;
    }
}
