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
            assertEquals(5, sheet.getLastRowNum());
            assertEquals("Cabernet Sauvignon", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("Hầm Rượu Trung Tâm", sheet.getRow(4).getCell(3).getStringCellValue());
            assertEquals(50.0, sheet.getRow(4).getCell(4).getNumericCellValue());
            assertEquals(5.0, sheet.getRow(4).getCell(5).getNumericCellValue());
            assertEquals(45.0, sheet.getRow(4).getCell(6).getNumericCellValue());
        }
    }

    @Test
    void testGenerateInventoryReportExcludesDeletedWinesAndSanitizesFormulas() throws IOException {
        Wine activeWine = new Wine();
        activeWine.setId(1L);
        activeWine.setName("=CMD|'/C calc'!A0");
        activeWine.setPrice(BigDecimal.valueOf(500000));
        activeWine.setDeleted(false);

        Wine deletedWine = new Wine();
        deletedWine.setId(2L);
        deletedWine.setName("Deleted Reserve");
        deletedWine.setPrice(BigDecimal.valueOf(700000));
        deletedWine.setDeleted(true);

        Warehouse wh = new Warehouse();
        wh.setName("+WarehouseEvil");

        Inventory activeInv = new Inventory();
        activeInv.setWine(activeWine);
        activeInv.setWarehouse(wh);
        activeInv.setCurrentQuantity(20);
        activeInv.setReservedQuantity(2);
        activeInv.setReorderLevel(5);

        Inventory deletedInv = new Inventory();
        deletedInv.setWine(deletedWine);
        deletedInv.setWarehouse(wh);
        deletedInv.setCurrentQuantity(99);
        deletedInv.setReservedQuantity(0);
        deletedInv.setReorderLevel(5);

        when(inventoryRepository.findAll()).thenReturn(List.of(activeInv, deletedInv));

        InventoryStockReportGenerator generator = new InventoryStockReportGenerator(inventoryRepository);
        byte[] bytes = generator.generateReport(null);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(5, sheet.getLastRowNum(), "Deleted wine row must be filtered out");
            assertEquals("'=CMD|'/C calc'!A0", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("'+WarehouseEvil", sheet.getRow(4).getCell(3).getStringCellValue());
            assertTrue(sheet.getRow(5).getCell(0).getStringCellValue().contains("1 dòng sản phẩm"));
        }
    }
}
