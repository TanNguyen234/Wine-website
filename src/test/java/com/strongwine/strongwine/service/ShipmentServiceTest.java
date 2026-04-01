package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.OtpDeliveryStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.OtpDeliveryException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @InjectMocks
    private ShipmentService shipmentService;

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
                .hasMessageContaining("60 giay");

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
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "000000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OTP khong dung");
            assertThat(shipment.getOtpAttemptCount()).isEqualTo(i);
            assertThat(shipment.getOtpLockedUntil()).isNull();
        }

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bi khoa 15 phut");

        assertThat(shipment.getOtpAttemptCount()).isEqualTo(5);
        assertThat(shipment.getOtpLockedUntil()).isNotNull();
        verify(shipmentOtpAuditService, times(5)).log(eq(shipment), eq("OTP_VERIFY"), eq("FAILED"),
                eq("OTP_MISMATCH"), isNull(), isNull());
    }

    @Test
    void completeDelivery_rejectsExpiredOtp() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        shipment.setOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("het han");

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void completeDelivery_rejectsOtpUserBindingMismatch() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        shipment.setOtpUserId(999L);
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("binding");

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void completeDelivery_withValidOtp_marksCompletedAndClearsSensitiveOtp() {
        Shipment shipment = createShipment(ShipmentStatus.DELIVERING, true);
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipperRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(shipment.getShipper()));
        when(shipperRepository.save(any(Shipper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.existsByShipperIdAndStatusIn(eq(301L), anyList())).thenReturn(false);

        shipmentService.completeDelivery(SHIPMENT_ID, "shipper-a", "123456");

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.COMPLETED);
        assertThat(shipment.getOtpVerified()).isTrue();
        assertThat(shipment.getOtpCode()).isNull();
        assertThat(shipment.getOtpAttemptCount()).isZero();
        verify(shipmentOtpAuditService).log(eq(shipment), eq("OTP_VERIFIED_COMPLETED"), eq("SUCCESS"),
                eq("SHIPPER_COMPLETE"), isNull(), eq("shipper-a"));
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
