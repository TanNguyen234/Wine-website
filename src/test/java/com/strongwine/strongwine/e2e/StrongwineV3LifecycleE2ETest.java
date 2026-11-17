package com.strongwine.strongwine.e2e;

import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.repository.*;
import com.strongwine.strongwine.service.*;
import com.strongwine.strongwine.service.invoice.InvoiceFormat;
import com.strongwine.strongwine.service.invoice.OrderInvoiceService;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integrated V3 commerce lifecycle verification:
 * Haversine shipping fee -> Voucher DECIMAL(18,2) quote & redemption ->
 * Encrypted v1: AES-GCM OTP -> COD batch verification ->
 * OpenPDF/Apache POI invoice export -> Customer Return, Refund & Restock + Audit Log trail.
 */
@ExtendWith(MockitoExtension.class)
class StrongwineV3LifecycleE2ETest {

    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherUsageRepository voucherUsageRepository;
    @Mock private AdminAuditLogRepository adminAuditLogRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipperCodBatchRepository shipperCodBatchRepository;
    @Mock private ShipperRepository shipperRepository;
    @Mock private UserRepository userRepository;
    @Mock private CodSettlementPort codSettlementPort;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy
    private AdminAuditLogService adminAuditLogService = new AdminAuditLogService(null);

    @InjectMocks
    private ReturnRequestService returnRequestService;

    @BeforeEach
    void wireRealAuditService() {
        adminAuditLogService = new AdminAuditLogService(adminAuditLogRepository);
        // Inject real AdminAuditLogService spy instance into field-injected ReturnRequestService
        try {
            var field = ReturnRequestService.class.getDeclaredField("adminAuditLogService");
            field.setAccessible(true);
            field.set(returnRequestService, adminAuditLogService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Full V3 commerce lifecycle: Haversine + Voucher -> Encrypted OTP -> COD Batch -> PDF/XLSX -> Return & Audit")
    void fullV3CommerceLifecycle() throws Exception {
        ShippingFeeService shippingFeeService = new ShippingFeeService();
        ShipmentOtpCryptoService otpCryptoService = new ShipmentOtpCryptoService("e2e-v3-aes-gcm-secret-key-2026");
        VoucherService voucherService = new VoucherService(voucherRepository, voucherUsageRepository, adminAuditLogService);
        OrderInvoiceService invoiceService = new OrderInvoiceService();
        ShipperCodBatchService codBatchService = new ShipperCodBatchService(
                shipperCodBatchRepository, shipmentRepository, shipperRepository,
                userRepository, codSettlementPort, adminAuditLogService, eventPublisher);

        User customer = new User();
        customer.setId(42L);
        customer.setUsername("minhquan");
        customer.setFullName("Nguyễn Minh Quân");
        customer.setEmail("quan.v3@strongwine.vn");
        customer.setRole("USER");

        Warehouse hcmWarehouse = new Warehouse();
        hcmWarehouse.setId(1L);
        hcmWarehouse.setName("Hầm Kho Quận 1");
        hcmWarehouse.setLocation("15 Lê Lợi, Quận 1, TP.HCM");
        hcmWarehouse.setLatitude(10.7745);
        hcmWarehouse.setLongitude(106.7010);
        hcmWarehouse.setActive(true);

        Wine traditionalWine = new Wine();
        traditionalWine.setId(88L);
        traditionalWine.setName("Rượu Mơ Yên Tử Thượng Hạng");
        traditionalWine.setType("Traditional");
        traditionalWine.setPrice(new BigDecimal("950000.00"));

        // 1. Haversine shipping fee under threshold (< 3M, ~7.8 km -> 45,000 VND tier)
        BigDecimal subtotal = new BigDecimal("1900000.00");
        BigDecimal calculatedShippingFee = shippingFeeService.calculateFeeForCoordinates(
                10.8350, 106.6650, hcmWarehouse.getLatitude(), hcmWarehouse.getLongitude(), subtotal);
        assertThat(calculatedShippingFee).isEqualByComparingTo("45000");

        // 2. Voucher validation & quote (10% of 1,900,000 -> 190,000 discount)
        Voucher voucher = new Voucher();
        voucher.setId(7L);
        voucher.setCode("MAISON10");
        voucher.setType(VoucherType.PERCENT);
        voucher.setValue(new BigDecimal("10.00"));
        voucher.setMinOrderValue(new BigDecimal("1000000.00"));
        voucher.setUsageLimit(100);
        voucher.setUsedCount(0);
        voucher.setActive(true);
        voucher.setStartsAt(LocalDateTime.now().minusDays(1));
        voucher.setExpiresAt(LocalDateTime.now().plusDays(14));

        when(voucherRepository.findByCodeIgnoreCase("MAISON10")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByVoucherIdAndUserId(7L, 42L)).thenReturn(false);

        VoucherValidationResult quote = voucherService.validateAndQuote("MAISON10", 42L, subtotal, calculatedShippingFee);
        assertThat(quote.valid()).isTrue();
        assertThat(quote.discountAmount()).isEqualByComparingTo("190000.00");
        assertThat(quote.finalShippingFee()).isEqualByComparingTo("45000.00");
        assertThat(quote.finalTotal()).isEqualByComparingTo("1755000.00");

        // 3. Build persisted Order snapshot with DECIMAL(18,2) precision
        Order order = new Order();
        order.setId(9001L);
        order.setUser(customer);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setAllocatedWarehouse(hcmWarehouse);
        order.setShippingFullName(customer.getFullName());
        order.setShippingPhone("0909888777");
        order.setShippingAddress("88 Quang Trung, Gò Vấp, TP.HCM");
        order.setVoucherCode("MAISON10");
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(quote.discountAmount());
        order.setShippingFee(quote.finalShippingFee());
        order.setTotalPrice(quote.finalTotal());

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setWine(traditionalWine);
        item.setQuantity(2);
        item.setPrice(traditionalWine.getPrice());
        order.setOrderItems(new ArrayList<>(List.of(item)));

        // 4. Atomic voucher redemption
        when(voucherRepository.findByCodeIgnoreCaseWithLock("MAISON10")).thenReturn(Optional.of(voucher));
        voucherService.redeemForOrder("MAISON10", customer, order);
        assertThat(voucher.getUsedCount()).isEqualTo(1);
        verify(voucherUsageRepository).save(any(VoucherUsage.class));

        // 5. Encrypted v1: AES-GCM OTP round-trip & masking guarantee
        String plainOtp = "684219";
        String cipherOtp = otpCryptoService.encrypt(plainOtp);
        assertThat(cipherOtp).startsWith("v1:").doesNotContain(plainOtp);
        assertThat(otpCryptoService.decrypt(cipherOtp)).contains(plainOtp);

        // 6. COD Batch creation + Admin settlement verification
        Shipper shipper = new Shipper();
        shipper.setId(5L);
        shipper.setName("Trần Minh Giao");
        shipper.setStatus(ShipperStatus.ACTIVE);

        Shipment shipment = new Shipment();
        shipment.setId(701L);
        shipment.setOrder(order);
        shipment.setShipper(shipper);
        shipment.setStatus(ShipmentStatus.COMPLETED);
        shipment.setOtpCode(cipherOtp);
        shipment.setOtpVerified(true);
        shipment.setDeliveredAt(LocalDateTime.now().minusMinutes(10));
        shipment.setCompletedAt(LocalDateTime.now().minusMinutes(5));
        shipment.setCodCollectedAmount(quote.finalTotal());

        when(shipperRepository.findById(5L)).thenReturn(Optional.of(shipper));
        when(shipmentRepository.findAllById(List.of(701L))).thenReturn(List.of(shipment));
        when(shipperCodBatchRepository.save(any(ShipperCodBatch.class))).thenAnswer(inv -> {
            ShipperCodBatch b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(301L);
            }
            return b;
        });
        when(shipmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(shipperCodBatchRepository.findById(301L)).thenAnswer(inv -> {
            ShipperCodBatch existing = new ShipperCodBatch();
            existing.setId(301L);
            existing.setShipper(shipper);
            existing.setTotalExpectedAmount(quote.finalTotal());
            existing.setOrderCount(1);
            existing.setStatus(CodBatchStatus.PENDING_SETTLEMENT);
            return Optional.of(existing);
        });
        when(shipmentRepository.findByCodBatchId(301L)).thenReturn(List.of(shipment));

        ShipperCodBatch batch = codBatchService.createBatchForShipper(
                5L, List.of(701L), quote.finalTotal(), "Nộp cuối ca chiều", "shipper-a");
        assertThat(batch.getTotalExpectedAmount()).isEqualByComparingTo("1755000.00");
        org.mockito.Mockito.doAnswer(inv -> {
            order.setStatus(OrderStatus.PAID);
            order.setPaymentStatus(PaymentStatus.SUCCESS);
            return null;
        }).when(codSettlementPort).settleCodOrder(9001L, "admin");
        ShipperCodBatch verifiedBatch = codBatchService.verifyBatch(301L, "admin", "Đã đối soát đủ tiền mặt");
        assertThat(verifiedBatch.getStatus()).isEqualTo(CodBatchStatus.VERIFIED);
        verify(codSettlementPort).settleCodOrder(9001L, "admin");

        // 7. Invoice generation (PDF + XLSX binary verification)
        byte[] pdfBytes = invoiceService.generateInvoice(order, InvoiceFormat.PDF);
        assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PdfReader reader = new PdfReader(pdfBytes)) {
            String pageText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(pageText).contains("9001").contains("MAISON10");
        }

        byte[] xlsxBytes = invoiceService.generateInvoice(order, InvoiceFormat.XLSX);
        assertThat(xlsxBytes).startsWith(new byte[]{'P', 'K'});
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
        }

        // 8. Return Request -> Approve -> Refund & Restock + Audit log verification
        ReturnRequest storedReturn = new ReturnRequest();
        storedReturn.setId(601L);
        storedReturn.setOrder(order);
        storedReturn.setUser(customer);
        storedReturn.setReason("Chai có dấu hiệu rỉ nút bần khi mở hộp");
        storedReturn.setStatus(ReturnStatus.REQUESTED);
        storedReturn.setRefundAmount(order.getTotalPrice());

        when(orderRepository.findByIdWithPessimisticLock(9001L)).thenReturn(Optional.of(order));
        when(shipmentOrderLifecyclePort.getShipmentByOrderId(9001L)).thenReturn(Optional.of(shipment));
        when(returnRequestRepository.existsByOrderIdAndStatusIn(9001L, List.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED)))
                .thenReturn(false);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenReturn(storedReturn);
        when(returnRequestRepository.findById(601L)).thenReturn(Optional.of(storedReturn));

        ReturnRequest requested = returnRequestService.requestReturn(9001L, 42L, storedReturn.getReason());
        assertThat(requested.getStatus()).isEqualTo(ReturnStatus.REQUESTED);

        returnRequestService.approveReturn(601L, "admin", "Đồng ý thu hồi kiểm tra");
        assertThat(storedReturn.getStatus()).isEqualTo(ReturnStatus.APPROVED);

        Payment paidRecord = new Payment();
        paidRecord.setId(55L);
        paidRecord.setOrder(order);
        paidRecord.setMethod(PaymentMethod.COD);
        paidRecord.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderIdForUpdate(9001L)).thenReturn(List.of(paidRecord));

        ReturnRequest refunded = returnRequestService.completeRefund(601L, "admin", "Đã hoàn tiền và nhập kho");
        assertThat(refunded.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        verify(inventoryService).importStock(eq(88L), eq(1L), eq(2), eq("admin"), anyString());

        verify(adminAuditLogRepository, atLeast(4)).save(any(AdminAuditLog.class));
    }
}
