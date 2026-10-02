package com.strongwine.strongwine.config;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoiDependencyTest {
    @Test
    void testPoiClassesAvailableInClasspath() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            assertNotNull(clazz);
        });
    }

    @Test
    void testCreateEmptyWorkbookInMemory() {
        assertDoesNotThrow(() -> {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                assertNotNull(workbook.createSheet("TestSheet"));
            }
        });
    }
}
