package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.InventoryRepository;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryStockReportGeneratorTest {

    @Mock private InventoryRepository inventoryRepository;

    @Test
    void testGenerateInventoryReport() throws IOException {
        Wine w = new Wine();
        w.setName("Cabernet Sauvignon");
        w.setPrice(BigDecimal.valueOf(500000));
        Warehouse wh = new Warehouse();
        wh.setName("Hầm Rượu Trung Tâm");

        Inventory inv = new Inventory();
        inv.setWine(w);
        inv.setWarehouse(wh);
        inv.setCurrentQuantity(50);
        inv.setReservedQuantity(5);
        inv.setReorderLevel(10);

        when(inventoryRepository.findAll()).thenReturn(List.of(inv));

        InventoryStockReportGenerator generator = new InventoryStockReportGenerator(inventoryRepository);
        byte[] bytes = generator.generateReport(null);

        assertNotNull(bytes);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Báo Cáo Tồn Kho", sheet.getSheetName());
            assertEquals(1, sheet.getLastRowNum());
            assertEquals("Cabernet Sauvignon", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(50.0, sheet.getRow(1).getCell(2).getNumericCellValue());
        }
    }
}
