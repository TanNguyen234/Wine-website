package com.strongwine.strongwine.service.strategy;

import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelWineImportStrategyTest {

    @Mock private WineRepository wineRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private WarehouseRepository warehouseRepository;

    private ExcelWineImportStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ExcelWineImportStrategy(wineRepository, categoryRepository, inventoryRepository, warehouseRepository);
    }

    private byte[] createSampleExcel(boolean includeInvalidRow) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Wines");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên Sản Phẩm");
            header.createCell(1).setCellValue("Loại Rượu");
            header.createCell(2).setCellValue("Năm Sản Xuất");
            header.createCell(3).setCellValue("Giá Bán");
            header.createCell(4).setCellValue("Xuất Xứ");
            header.createCell(5).setCellValue("Mô Tả");
            header.createCell(6).setCellValue("Danh Mục");
            header.createCell(7).setCellValue("Số Lượng Kho");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Chateau Margaux 2018");
            row1.createCell(1).setCellValue("Red");
            row1.createCell(2).setCellValue(2018);
            row1.createCell(3).setCellValue(2500000.0);
            row1.createCell(4).setCellValue("Pháp");
            row1.createCell(5).setCellValue("Hương gỗ sồi đậm đà");
            row1.createCell(6).setCellValue("Vang Pháp");
            row1.createCell(7).setCellValue(24);

            if (includeInvalidRow) {
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("Invalid Wine");
                row2.createCell(1).setCellValue("Unknown"); // Sai loại rượu
                row2.createCell(2).setCellValue(1800); // Năm sai
                row2.createCell(3).setCellValue(-500); // Giá âm
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void testImportValidExcel_DryRun() throws IOException {
        byte[] excelBytes = createSampleExcel(false);
        when(wineRepository.findByNameIgnoreCaseAndYearAndDeletedFalse("Chateau Margaux 2018", 2018))
                .thenReturn(Optional.empty());

        ImportOptions options = ImportOptions.builder().dryRun(true).build();
        ImportResult<Wine> result = strategy.importData(new ByteArrayInputStream(excelBytes), options);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getInsertedCount());
        verify(wineRepository, never()).save(any(Wine.class)); // Dry run không lưu
    }

    @Test
    void testImportInvalidExcel_CollectsErrors() throws IOException {
        byte[] excelBytes = createSampleExcel(true);
        ImportOptions options = ImportOptions.builder().dryRun(true).build();
        ImportResult<Wine> result = strategy.importData(new ByteArrayInputStream(excelBytes), options);

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getErrorCount() >= 1);
        assertEquals(3, result.getErrors().get(0).getRowNumber());
    }
}
