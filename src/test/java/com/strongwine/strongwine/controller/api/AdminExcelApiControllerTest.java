package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ExcelPreviewResponseDto;
import com.strongwine.strongwine.service.report.ExcelTemplateService;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminExcelApiControllerTest {

    @Mock private ExcelWineImportStrategy excelStrategy;
    @Mock private ExcelTemplateService templateService;
    @Mock private com.strongwine.strongwine.service.report.InventoryStockReportGenerator inventoryStockReportGenerator;

    @Test
    void testDownloadTemplateReturnsFileBytes() {
        when(templateService.generateProductImportTemplate()).thenReturn(new byte[]{1, 2, 3});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator);

        ResponseEntity<byte[]> response = controller.downloadTemplate();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void testPreviewUploadReturnsValidSummary() {
        when(excelStrategy.importData(any(), any())).thenReturn(new ImportResult<>());
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        ResponseEntity<ExcelPreviewResponseDto> response = controller.previewExcel(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testExportInventoryReturnsValidExcelBytes() {
        when(inventoryStockReportGenerator.generateReport(any())).thenReturn(new byte[]{4, 5, 6});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator);

        ResponseEntity<byte[]> response = controller.exportInventory();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }
}
