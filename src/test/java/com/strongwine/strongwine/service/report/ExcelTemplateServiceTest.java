package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

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
        }
    }
}
