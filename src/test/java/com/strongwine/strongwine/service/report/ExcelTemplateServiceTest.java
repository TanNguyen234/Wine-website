package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.dto.RevenueReportCriteria;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.OrderRepository;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelTemplateServiceTest {

    private final ExcelTemplateService templateService = new ExcelTemplateService();

    @Test
    void testGenerateTemplateNotEmptyAndHasValidHeaders() throws IOException {
        byte[] bytes = templateService.generateProductImportTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 3000, "File template phải có dung lượng > 3KB");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Tên Sản Phẩm (*)", header.getCell(0).getStringCellValue());
            assertEquals("Loại Rượu (*)", header.getCell(1).getStringCellValue());
            assertEquals("Năm Sản Xuất (*)", header.getCell(2).getStringCellValue());
            assertEquals("Giá Bán (VNĐ) (*)", header.getCell(3).getStringCellValue());

            // Kiểm tra có ít nhất 2 dòng dữ liệu mẫu
            assertTrue(sheet.getLastRowNum() >= 2);

            Sheet guideSheet = wb.getSheetAt(1);
            StringBuilder guideText = new StringBuilder();
            for (Row row : guideSheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        guideText.append(cell.getStringCellValue()).append('\n');
                    }
                }
            }
            assertTrue(guideText.toString().contains("Traditional"),
                    "Template guide must document Traditional wine type");
        }
    }

    @Test
    void testSanitizeExcelTextNeutralizesFormulaInjectionPrefixes() {
        assertNull(AbstractExcelReportGenerator.sanitizeExcelText(null));
        assertEquals("Chateau Margaux", AbstractExcelReportGenerator.sanitizeExcelText("Chateau Margaux"));
        assertEquals("'=CMD()|'/C calc'!A0", AbstractExcelReportGenerator.sanitizeExcelText("=CMD()|'/C calc'!A0"));
        assertEquals("'+1+1", AbstractExcelReportGenerator.sanitizeExcelText("+1+1"));
        assertEquals("'-2+3", AbstractExcelReportGenerator.sanitizeExcelText("-2+3"));
        assertEquals("'@SUM(A1:A2)", AbstractExcelReportGenerator.sanitizeExcelText("@SUM(A1:A2)"));
        assertEquals("'\t=evil", AbstractExcelReportGenerator.sanitizeExcelText("\t=evil"));
        assertEquals("'\r+evil", AbstractExcelReportGenerator.sanitizeExcelText("\r+evil"));
        assertEquals("'  =1+1", AbstractExcelReportGenerator.sanitizeExcelText("  =1+1"));
    }

    @Test
    void testOrderAndRevenueReportsSanitizeFormulaTextCells() throws IOException {
        OrderRepository orderRepository = mock(OrderRepository.class);

        Wine evilWine = new Wine();
        evilWine.setId(55L);
        evilWine.setName("=HYPERLINK(\"http://bad.invalid\",\"Wine\")");
        evilWine.setType("Red");

        Order order = new Order();
        order.setId(77L);
        order.setOrderDate(LocalDateTime.of(2026, 11, 14, 9, 0));
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setShippingFullName("+CMD|'/C calc'!A0");
        order.setShippingPhone("0909123456");
        order.setTotalPrice(new BigDecimal("1200000"));

        OrderItem oi = new OrderItem();
        oi.setOrder(order);
        oi.setWine(evilWine);
        oi.setQuantity(1);
        oi.setPrice(new BigDecimal("1200000"));
        order.setOrderItems(List.of(oi));

        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(orderRepository.findPaidOrdersWithItemsBetween(any(), any())).thenReturn(List.of(order));

        OrderReportGenerator orderGen = new OrderReportGenerator(orderRepository);
        byte[] orderBytes = orderGen.generateReport(null);
        assertNoUnsafeFormulaStrings(orderBytes);

        RevenueReportGenerator revenueGen = new RevenueReportGenerator(orderRepository);
        RevenueReportCriteria criteria = new RevenueReportCriteria();
        criteria.setFromDate(LocalDate.of(2026, 11, 1));
        criteria.setToDate(LocalDate.of(2026, 11, 14));
        byte[] revBytes = revenueGen.generateReport(criteria);
        assertNoUnsafeFormulaStrings(revBytes);
    }

    private void assertNoUnsafeFormulaStrings(byte[] workbookBytes) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                for (Row row : wb.getSheetAt(s)) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            String v = cell.getStringCellValue();
                            assertFalse(v.startsWith("=") || v.startsWith("+") || v.startsWith("@"),
                                    "Unsafe formula prefix in exported workbook: " + v);
                        }
                    }
                }
            }
        }
    }
}
