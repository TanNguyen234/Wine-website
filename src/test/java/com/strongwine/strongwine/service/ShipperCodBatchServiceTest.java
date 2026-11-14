package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperCodBatchRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperCodBatchServiceTest {

    @Mock
    private ShipperCodBatchRepository shipperCodBatchRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipperRepository shipperRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CodSettlementPort codSettlementPort;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ShipperCodBatchService shipperCodBatchService;

    @Test
    void createBatchForShipper_createsPendingBatchAndLinksUnbatchedCodShipments() {
        Shipper shipper = createShipper(301L, "shipper-a");
        Shipment s1 = createCompletedCodShipment(11L, 1001L, shipper, new BigDecimal("500000"), null);
        Shipment s2 = createCompletedCodShipment(12L, 1002L, shipper, null, new BigDecimal("350000"));

        when(shipperRepository.findById(301L)).thenReturn(Optional.of(shipper));
        when(shipmentRepository.findAllById(List.of(11L, 12L))).thenReturn(List.of(s1, s2));
        when(shipperCodBatchRepository.save(any(ShipperCodBatch.class))).thenAnswer(invocation -> {
            ShipperCodBatch batch = invocation.getArgument(0);
            batch.setId(901L);
            return batch;
        });
        when(shipmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ShipperCodBatch batch = shipperCodBatchService.createBatchForShipper(
                301L, List.of(11L, 12L), new BigDecimal("850000"), "Evening handoff", "shipper-a");

        assertThat(batch.getId()).isEqualTo(901L);
        assertThat(batch.getStatus()).isEqualTo(CodBatchStatus.PENDING_SETTLEMENT);
        assertThat(batch.getOrderCount()).isEqualTo(2);
        assertThat(batch.getTotalExpectedAmount()).isEqualByComparingTo(new BigDecimal("850000"));
        assertThat(batch.getTotalSubmittedAmount()).isEqualByComparingTo(new BigDecimal("850000"));
        assertThat(batch.getBatchCode()).startsWith("COD-");
        assertThat(s1.getCodBatch()).isSameAs(batch);
        assertThat(s2.getCodBatch()).isSameAs(batch);
        verify(adminAuditLogService).log(eq("shipper-a"), eq("COD_BATCH_CREATED"), eq("COD_BATCH"),
                eq(901L), isNull(), any(), isNull());
    }

    @Test
    void createBatchForShipper_rejectsAlreadyBatchedShipments() {
        Shipper shipper = createShipper(301L, "shipper-a");
        Shipment batched = createCompletedCodShipment(11L, 1001L, shipper, new BigDecimal("250000"), null);
        ShipperCodBatch existingBatch = new ShipperCodBatch();
        existingBatch.setId(77L);
        batched.setCodBatch(existingBatch);

        when(shipperRepository.findById(301L)).thenReturn(Optional.of(shipper));
        when(shipmentRepository.findAllById(List.of(11L))).thenReturn(List.of(batched));

        assertThatThrownBy(() -> shipperCodBatchService.createBatchForShipper(
                301L, List.of(11L), null, "Duplicate attempt", "shipper-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã thuộc bảng kê COD");

        verify(shipperCodBatchRepository, never()).save(any());
    }

    @Test
    void createBatchForShipper_rejectsNonCodOrNotCompletedShipments() {
        Shipper shipper = createShipper(301L, "shipper-a");
        Shipment stripeShipment = createCompletedCodShipment(11L, 1001L, shipper, new BigDecimal("300000"), null);
        stripeShipment.getOrder().setPaymentMethod(PaymentMethod.STRIPE);

        when(shipperRepository.findById(301L)).thenReturn(Optional.of(shipper));
        when(shipmentRepository.findAllById(List.of(11L))).thenReturn(List.of(stripeShipment));

        assertThatThrownBy(() -> shipperCodBatchService.createBatchForShipper(
                301L, List.of(11L), null, "Invalid method", "shipper-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COD");

        verify(shipperCodBatchRepository, never()).save(any());
    }

    @Test
    void verifyBatch_transitionsToVerifiedSettlesEveryOrderAndAudits() {
        Shipper shipper = createShipper(301L, "shipper-a");
        ShipperCodBatch batch = new ShipperCodBatch();
        batch.setId(901L);
        batch.setShipper(shipper);
        batch.setBatchCode("COD-901");
        batch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
        batch.setTotalExpectedAmount(new BigDecimal("850000"));
        batch.setOrderCount(2);

        Shipment s1 = createCompletedCodShipment(11L, 1001L, shipper, new BigDecimal("500000"), null);
        Shipment s2 = createCompletedCodShipment(12L, 1002L, shipper, new BigDecimal("350000"), null);
        s1.setCodBatch(batch);
        s2.setCodBatch(batch);

        when(shipperCodBatchRepository.findById(901L)).thenReturn(Optional.of(batch));
        when(shipmentRepository.findByCodBatchId(901L)).thenReturn(List.of(s1, s2));
        when(shipperCodBatchRepository.save(any(ShipperCodBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipperCodBatch verified = shipperCodBatchService.verifyBatch(901L, "admin", "Counted cash verified");

        assertThat(verified.getStatus()).isEqualTo(CodBatchStatus.VERIFIED);
        assertThat(verified.getVerifiedBy()).isEqualTo("admin");
        assertThat(verified.getVerifiedAt()).isNotNull();
        verify(codSettlementPort).settleCodOrder(1001L, "admin");
        verify(codSettlementPort).settleCodOrder(1002L, "admin");
        verify(adminAuditLogService).log(eq("admin"), eq("COD_BATCH_VERIFIED"), eq("COD_BATCH"),
                eq(901L), any(), any(), isNull());
    }

    @Test
    void disputeBatch_transitionsToDisputedWithReasonAndDoesNotSettleOrders() {
        Shipper shipper = createShipper(301L, "shipper-a");
        ShipperCodBatch batch = new ShipperCodBatch();
        batch.setId(902L);
        batch.setShipper(shipper);
        batch.setBatchCode("COD-902");
        batch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
        batch.setTotalExpectedAmount(new BigDecimal("500000"));
        batch.setOrderCount(1);

        when(shipperCodBatchRepository.findById(902L)).thenReturn(Optional.of(batch));
        when(shipperCodBatchRepository.save(any(ShipperCodBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipperCodBatch disputed = shipperCodBatchService.disputeBatch(902L, "admin", "Short by 100,000 VND");

        assertThat(disputed.getStatus()).isEqualTo(CodBatchStatus.DISPUTED);
        assertThat(disputed.getNote()).isEqualTo("Short by 100,000 VND");
        assertThat(disputed.getVerifiedBy()).isEqualTo("admin");
        assertThat(disputed.getVerifiedAt()).isNotNull();
        verify(codSettlementPort, never()).settleCodOrder(anyLong(), any());
        verify(adminAuditLogService).log(eq("admin"), eq("COD_BATCH_DISPUTED"), eq("COD_BATCH"),
                eq(902L), any(), any(), isNull());
    }

    private Shipper createShipper(Long id, String username) {
        User user = new User();
        user.setId(id + 1000);
        user.setUsername(username);
        user.setRole("SHIPPER");

        Shipper shipper = new Shipper();
        shipper.setId(id);
        shipper.setUser(user);
        shipper.setName("Shipper " + username);
        shipper.setStatus(ShipperStatus.ACTIVE);
        return shipper;
    }

    private Shipment createCompletedCodShipment(Long shipmentId,
                                                Long orderId,
                                                Shipper shipper,
                                                BigDecimal codCollected,
                                                BigDecimal orderTotalPrice) {
        Order order = new Order();
        order.setId(orderId);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setTotalPrice(orderTotalPrice != null ? orderTotalPrice : codCollected);

        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setOrder(order);
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.COMPLETED);
        shipment.setDeliveredAt(LocalDateTime.now().minusHours(1));
        shipment.setCompletedAt(LocalDateTime.now().minusHours(1));
        shipment.setCodCollectedAmount(codCollected);
        return shipment;
    }
}
