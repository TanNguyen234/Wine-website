package com.strongwine.strongwine.e2e;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.event.listener.RealtimeEventListener;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.repository.specification.WineSpecification;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Task 6.2: End-to-End Commerce Workflow Verification Test
 * Kiểm thử luồng vận hành xuyên suốt không gián đoạn:
 * 1. Excel Bulk Import (Strategy Pattern)
 * 2. Dynamic Catalog Multi-Criteria Search (Specification Pattern)
 * 3. Real-Time Shopee-Style Event Streaming (Spring ApplicationEvents -> SseEmitter)
 */
@ExtendWith(MockitoExtension.class)
class EndToEndCommerceFlowTest {

    @Mock private WineRepository wineRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private WarehouseRepository warehouseRepository;

    @Test
    @DisplayName("Luồng 1: Admin nhập hàng loạt từ Excel -> Upsert tự động -> Cập nhật CSDL")
    void testEndToEndExcelImportWorkflow() throws IOException {
        // 1. Tạo file Excel chuẩn trong bộ nhớ
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Wines");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên Sản Phẩm");
            header.createCell(1).setCellValue("Loại Rượu");
            header.createCell(2).setCellValue("Năm Sản Xuất");
            header.createCell(3).setCellValue("Giá Bán");
            header.createCell(4).setCellValue("Xuất Xứ");
            header.createCell(5).setCellValue("Mô Tả");
            header.createCell(6).setCellValue("Danh Mục");
            header.createCell(7).setCellValue("Số Lượng Kho");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("Sassicaia Tenuta San Guido 2017");
            r.createCell(1).setCellValue("Red");
            r.createCell(2).setCellValue(2017);
            r.createCell(3).setCellValue(8500000.0);
            r.createCell(4).setCellValue("Ý");
            r.createCell(5).setCellValue("Vang Ý thượng hạng Super Tuscan");
            r.createCell(6).setCellValue("Vang Ý Cao Cấp");
            r.createCell(7).setCellValue(18);

            wb.write(bos);
        }

        // Mock setup
        when(wineRepository.findByNameIgnoreCaseAndYearAndDeletedFalse("Sassicaia Tenuta San Guido 2017", 2017))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByName("Vang Ý Cao Cấp"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> {
                    Category c = inv.getArgument(0);
                    c.setId(10L);
                    return c;
                });
        when(wineRepository.save(any(Wine.class)))
                .thenAnswer(inv -> {
                    Wine w = inv.getArgument(0);
                    w.setId(100L);
                    return w;
                });
        when(warehouseRepository.findById(1L))
                .thenReturn(Optional.of(new Warehouse()));

        // Thực thi Import qua Strategy
        ExcelWineImportStrategy importStrategy = new ExcelWineImportStrategy(
                wineRepository, categoryRepository, inventoryRepository, warehouseRepository);

        ImportOptions options = ImportOptions.builder().dryRun(false).defaultWarehouseId(1L).build();
        ImportResult<Wine> result = importStrategy.importData(new ByteArrayInputStream(bos.toByteArray()), options);

        assertEquals(1, result.getSuccessCount(), "Phải nhập thành công 1 sản phẩm");
        assertEquals(0, result.getErrorCount(), "Không được có dòng lỗi");
        assertEquals(1, result.getInsertedCount(), "Phải ghi nhận 1 sản phẩm mới thêm vào CSDL");

        verify(wineRepository, times(1)).save(any(Wine.class));
        verify(categoryRepository, times(1)).save(any(Category.class));
        verify(inventoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Luồng 2: Khách hàng tìm kiếm theo Bộ lọc Đa Tiêu Chí Động (JPA Specification)")
    void testEndToEndCatalogFilterWorkflow() {
        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .keyword("Sassicaia")
                .types(List.of("Red"))
                .minPrice(BigDecimal.valueOf(5000000))
                .maxPrice(BigDecimal.valueOf(10000000))
                .countries(List.of("Ý"))
                .vintageYears(List.of(2017))
                .build();

        Specification<Wine> specification = WineSpecification.build(criteria);
        assertNotNull(specification, "Specification phải được khởi tạo thành công từ criteria");
    }

    @Test
    @DisplayName("Luồng 3: Real-Time SSE Stream phát sóng cập nhật đơn hàng & shipper tới client")
    void testEndToEndRealtimeEventStreaming() {
        SseNotificationService sseService = new SseNotificationService();
        RealtimeEventListener listener = new RealtimeEventListener(sseService);

        // Client kết nối SSE stream cho đơn hàng #888
        SseEmitter orderEmitter = sseService.subscribeOrder(888L);
        assertNotNull(orderEmitter);

        // Client shipper kết nối SSE stream cho shipper #99
        SseEmitter shipperEmitter = sseService.subscribeShipper(99L);
        assertNotNull(shipperEmitter);

        // Phát sự kiện đơn hàng thanh toán thành công
        OrderStatusUpdatedEvent orderEvent = new OrderStatusUpdatedEvent(
                this, 888L, "PAID", "Đơn hàng đã được thanh toán thành công!");
        assertDoesNotThrow(() -> listener.handleOrderStatusUpdated(orderEvent));

        // Phát sự kiện Shipper bắt đầu giao hàng kèm OTP
        ShipmentLiveStatusEvent shipmentEvent = new ShipmentLiveStatusEvent(
                this, 501L, 888L, "DELIVERING", "Nguyễn Văn Giao", "0901234567", "59A-12345");
        assertDoesNotThrow(() -> listener.handleShipmentLiveStatus(shipmentEvent));

        // Dọn dẹp
        orderEmitter.complete();
        shipperEmitter.complete();
    }
}
