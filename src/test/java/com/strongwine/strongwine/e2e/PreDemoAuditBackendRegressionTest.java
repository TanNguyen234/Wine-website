package com.strongwine.strongwine.e2e;

import com.strongwine.strongwine.controller.AdminController;
import com.strongwine.strongwine.controller.OrderController;
import com.strongwine.strongwine.controller.VoucherAdminController;
import com.strongwine.strongwine.controller.api.AdminExcelApiController;
import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.ShipperCodBatchRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.security.UserDetailsServiceImpl;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperCodBatchService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.VoucherService;
import com.strongwine.strongwine.service.WineService;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.report.ExcelTemplateService;
import com.strongwine.strongwine.service.report.InventoryStockReportGenerator;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused RED -> GREEN regression suite for backend defects uncovered during
 * the 2026-11-18 pre-demo audit & live browser E2E QA.
 */
class PreDemoAuditBackendRegressionTest {

    @Test
    void excelExportRecoversWhenPoiTempDirectoryWasDeletedAtRuntime() throws Exception {
        ExcelTemplateService templateService = new ExcelTemplateService();
        // Prime POI singleton temp dir once, then delete it to reproduce Windows Temp/poifiles cleanup
        byte[] initial = templateService.generateProductImportTemplate();
        assertTrue(initial.length > 0);

        Path poiDir = Path.of(System.getProperty("java.io.tmpdir"), "poifiles");
        if (Files.exists(poiDir)) {
            try (var walk = Files.walk(poiDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }

        byte[] recovered = templateService.generateProductImportTemplate();
        assertNotNull(recovered);
        assertTrue(recovered.length > 100, "SXSSFWorkbook must recreate missing Temp/poifiles directory");
    }

    @Test
    void newTransientInventoryLeavesVersionNullForSpringDataJpaPersist() {
        InventoryRepository inventoryRepository = mock(InventoryRepository.class);
        WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
        WineRepository wineRepository = mock(WineRepository.class);

        Wine wine = new Wine();
        wine.setId(99L);
        wine.setName("Château Test");
        wine.setDeleted(false);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Main Warehouse");
        warehouse.setActive(true);
        warehouse.setIsDefault(true);

        when(wineRepository.findById(99L)).thenReturn(Optional.of(wine));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(inventoryRepository.findByWineIdAndWarehouseId(99L, 1L)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryService service = new InventoryService();
        ReflectionTestUtils.setField(service, "inventoryRepository", inventoryRepository);
        ReflectionTestUtils.setField(service, "warehouseRepository", warehouseRepository);
        ReflectionTestUtils.setField(service, "wineRepository", wineRepository);

        service.getOrCreateInventory(99L, 1L);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertNull(captor.getValue().getVersion(),
                "Transient Inventory (id == null) must leave @Version null so Spring Data JPA 4 calls persist() instead of merge()");
    }

    @Test
    void verifyCodBatchDoesNotReSettleAlreadySettledCodOrder() {
        ShipperCodBatchRepository batchRepository = mock(ShipperCodBatchRepository.class);
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipperRepository shipperRepository = mock(ShipperRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        Order settledCodOrder = new Order();
        settledCodOrder.setId(501L);
        settledCodOrder.setPaymentMethod(PaymentMethod.COD);
        settledCodOrder.setPaymentStatus(PaymentStatus.SUCCESS);
        settledCodOrder.setStatus(OrderStatus.PAID);

        Shipment shipment = new Shipment();
        shipment.setId(601L);
        shipment.setOrder(settledCodOrder);

        ShipperCodBatch batch = new ShipperCodBatch();
        batch.setId(701L);
        batch.setStatus(CodBatchStatus.PENDING_SETTLEMENT);

        when(batchRepository.findById(701L)).thenReturn(Optional.of(batch));
        when(shipmentRepository.findByCodBatchId(701L)).thenReturn(List.of(shipment));
        when(batchRepository.save(any(ShipperCodBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        CodSettlementPort strictSettlementPort = (orderId, actor) -> {
            throw new IllegalStateException("Should not re-settle already SUCCESS COD order #" + orderId);
        };

        ShipperCodBatchService service = new ShipperCodBatchService(
                batchRepository, shipmentRepository, shipperRepository, userRepository,
                strictSettlementPort, null, null
        );

        ShipperCodBatch verified = service.verifyBatch(701L, "admin", "Đối soát hợp lệ");
        assertEquals(CodBatchStatus.VERIFIED, verified.getStatus());
    }

    @Test
    void orderConfirmationRedirectsToOrdersInsteadOfHttp500OnPageRefreshWithoutFlash() throws Exception {
        OrderService orderService = mock(OrderService.class);
        UserRepository userRepository = mock(UserRepository.class);

        User customer = new User();
        customer.setId(10L);
        customer.setUsername("nguyen.minh.quan");
        customer.setRole("USER");
        when(userRepository.findByUsername("nguyen.minh.quan")).thenReturn(Optional.of(customer));

        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mockMvc.perform(get("/order-confirmation")
                        .principal(new UsernamePasswordAuthenticationToken("nguyen.minh.quan", "N/A", List.of())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
    }

    @Test
    void adminDashboardPopulatesQuickWineTableAndRawVndRevenueChartSeries() {
        WineService wineService = mock(WineService.class);
        UserService userService = mock(UserService.class);
        OrderService orderService = mock(OrderService.class);
        ReviewService reviewService = mock(ReviewService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PaymentService paymentService = mock(PaymentService.class);
        ShipperService shipperService = mock(ShipperService.class);
        ShipmentService shipmentService = mock(ShipmentService.class);

        Wine w1 = new Wine();
        w1.setId(1L);
        w1.setName("Château Margaux");
        w1.setType("Red");
        w1.setPrice(new BigDecimal("8990000"));

        Order paidToday = new Order();
        paidToday.setId(100L);
        paidToday.setStatus(OrderStatus.PAID);
        paidToday.setPaymentStatus(PaymentStatus.SUCCESS);
        paidToday.setOrderDate(LocalDateTime.now());
        paidToday.setTotalPrice(new BigDecimal("18500000"));

        when(wineService.countWines()).thenReturn(1L);
        when(wineService.getAllWines()).thenReturn(List.of(w1));
        when(inventoryService.getAvailableStockByWineIds(List.of(1L))).thenReturn(Map.of(1L, 24));
        when(orderService.getTotalRevenue()).thenReturn(18_500_000d);
        when(orderService.getAllOrders()).thenReturn(List.of(paidToday));
        when(orderService.getAllOrdersPage(any())).thenReturn(new PageImpl<>(List.of(paidToday)));
        when(shipmentService.getShipmentStatusStats()).thenReturn(Map.of());

        AdminController controller = new AdminController(
                wineService, userService, orderService, reviewService,
                inventoryService, paymentService, shipperService, shipmentService
        );
        Model model = new ConcurrentModel();
        controller.dashboard(7, null, null, model);

        @SuppressWarnings("unchecked")
        List<Wine> dashboardWines = (List<Wine>) model.getAttribute("wines");
        assertNotNull(dashboardWines, "admin-dashboard.html expects 'wines' in Model");
        assertEquals(1, dashboardWines.size());

        String revenueValuesJson = (String) model.getAttribute("revenueValuesJson");
        assertNotNull(revenueValuesJson);
        assertTrue(revenueValuesJson.contains("18500000") || revenueValuesJson.contains("1.85E7"),
                "Revenue chart series must match VND scale expected by admin-charts.js, actual: " + revenueValuesJson);
    }

    @Test
    void excelBulkImportTreatsAbsoluteOptionAsReplaceInventoryMode() throws Exception {
        ExcelWineImportStrategy strategy = mock(ExcelWineImportStrategy.class);
        ExcelTemplateService templateService = mock(ExcelTemplateService.class);
        InventoryStockReportGenerator invReport = mock(InventoryStockReportGenerator.class);

        when(strategy.importData(any(InputStream.class), any(ImportOptions.class)))
                .thenReturn(new ImportResult<>());

        AdminExcelApiController controller = new AdminExcelApiController(strategy, templateService, invReport);
        MockMultipartFile file = new MockMultipartFile("file", "wines.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        controller.executeImport(file, "ABSOLUTE", 1L, false, null);

        ArgumentCaptor<ImportOptions> captor = ArgumentCaptor.forClass(ImportOptions.class);
        verify(strategy).importData(any(InputStream.class), captor.capture());
        assertEquals(ImportOptions.InventoryMode.REPLACE, captor.getValue().getInventoryMode());
    }

    @Test
    void voucherAdminSaveUncheckedCheckboxPassesActiveFalse() throws Exception {
        VoucherService voucherService = mock(VoucherService.class);
        when(voucherService.updateVoucher(eq(5L), any(Voucher.class), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        VoucherAdminController controller = new VoucherAdminController(voucherService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/admin/vouchers")
                        .param("id", "5")
                        .param("code", "MAISON10")
                        .param("type", "PERCENT")
                        .param("value", "10"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherService).updateVoucher(eq(5L), captor.capture(), any(), any());
        assertEquals(Boolean.FALSE, captor.getValue().getActive(),
                "Unchecked 'active' checkbox on /admin/vouchers must pass Boolean.FALSE so admins can deactivate vouchers");
    }

    @Test
    void userDetailsServiceAllowsLoginByEmailAddress() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId(7L);
        user.setUsername("nguyen.minh.quan");
        user.setEmail("quan.nguyen@strongwine.vn");
        user.setPassword("$2a$10$encoded");
        user.setRole("USER");

        when(userRepository.findByUsername("quan.nguyen@strongwine.vn")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("quan.nguyen@strongwine.vn")).thenReturn(Optional.of(user));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);

        UserDetails details = service.loadUserByUsername("quan.nguyen@strongwine.vn");
        assertEquals("nguyen.minh.quan", details.getUsername());
    }

    @Test
    void cancelPendingOrderMarksPendingPaymentCancelled() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        Order pendingOrder = new Order();
        pendingOrder.setId(77L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setPaymentStatus(PaymentStatus.PENDING);
        pendingOrder.setPaymentMethod(PaymentMethod.VNPAY);

        Payment pendingPayment = new Payment();
        pendingPayment.setId(82L);
        pendingPayment.setOrder(pendingOrder);
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment.setMethod(PaymentMethod.VNPAY);

        when(orderRepository.findByIdWithPessimisticLock(77L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByOrderIdForUpdate(77L)).thenReturn(List.of(pendingPayment));

        OrderService orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(orderService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(orderService, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(orderService, "applicationEventPublisher", eventPublisher);

        orderService.cancelPendingOrder(77L, "nguyen.minh.quan", "CUSTOMER_CANCELLED");

        assertEquals(PaymentStatus.CANCELLED, pendingPayment.getStatus(),
                "Cancelling an unpaid order must transition its PENDING Payment record to CANCELLED");
    }

    @Test
    void newShipperDefaultsToConcurrentCapacityFiveSoLiveAssignmentSucceeds() {
        com.strongwine.strongwine.entity.Shipper shipper = new com.strongwine.strongwine.entity.Shipper();
        assertTrue(shipper.getMaxConcurrentShipments() != null && shipper.getMaxConcurrentShipments() >= 5,
                "Shipper default maxConcurrentShipments must be >= 5 so active shippers with 1 order remain assignable");
    }

    @Test
    void inventoryServiceRejectsNullWineIdWithIllegalArgumentException() {
        InventoryService service = new InventoryService();
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.getOrCreateInventory(null, 1L)
        );
        assertEquals("wineId is required", ex.getMessage());
    }

    @Test
    void terminalCodShipmentFailureReleasesOrderVoucher() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipperRepository shipperRepository = mock(ShipperRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        com.strongwine.strongwine.service.ShipmentOtpEmailService emailService = mock(com.strongwine.strongwine.service.ShipmentOtpEmailService.class);
        com.strongwine.strongwine.service.ShipmentOtpAuditService otpAuditService = mock(com.strongwine.strongwine.service.ShipmentOtpAuditService.class);
        com.strongwine.strongwine.service.ShipmentStatusHistoryService historyService = mock(com.strongwine.strongwine.service.ShipmentStatusHistoryService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort = mock(com.strongwine.strongwine.service.port.VoucherPricingPort.class);

        User shipperUser = new User();
        shipperUser.setId(20L);
        shipperUser.setUsername("shipper1");
        shipperUser.setRole("SHIPPER");

        com.strongwine.strongwine.entity.Shipper shipper = new com.strongwine.strongwine.entity.Shipper();
        shipper.setId(2L);
        shipper.setUser(shipperUser);
        shipper.setStatus(com.strongwine.strongwine.entity.ShipperStatus.ACTIVE);

        Order codOrder = new Order();
        codOrder.setId(888L);
        codOrder.setPaymentMethod(PaymentMethod.COD);
        codOrder.setStatus(OrderStatus.PENDING);
        codOrder.setPaymentStatus(PaymentStatus.PENDING);
        codOrder.setVoucherCode("MAISON10");

        Shipment delivering = new Shipment();
        delivering.setId(999L);
        delivering.setOrder(codOrder);
        delivering.setShipper(shipper);
        delivering.setStatus(com.strongwine.strongwine.entity.ShipmentStatus.DELIVERING);

        when(userRepository.findByUsername("shipper1")).thenReturn(Optional.of(shipperUser));
        when(shipperRepository.findByUserId(20L)).thenReturn(Optional.of(shipper));
        when(shipmentRepository.findByIdForUpdate(999L)).thenReturn(Optional.of(delivering));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        ShipmentService shipmentService = new ShipmentService(
                shipmentRepository, shipperRepository, orderRepository, userRepository,
                emailService, otpAuditService, historyService
        );
        shipmentService.setInventoryService(inventoryService);
        ReflectionTestUtils.setField(shipmentService, "voucherPricingPort", voucherPricingPort);

        shipmentService.markFailed(999L, "shipper1", "Khách không nghe máy sau 3 lần gọi");

        verify(voucherPricingPort).releaseForOrder(888L);
    }
}
