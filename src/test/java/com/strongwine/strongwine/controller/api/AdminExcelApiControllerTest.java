package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ExcelPreviewResponseDto;
import com.strongwine.strongwine.service.AdminAuditLogService;
import com.strongwine.strongwine.service.report.ExcelTemplateService;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminExcelApiControllerTest {

    @Mock private ExcelWineImportStrategy excelStrategy;
    @Mock private ExcelTemplateService templateService;
    @Mock private com.strongwine.strongwine.service.report.InventoryStockReportGenerator inventoryStockReportGenerator;
    @Mock private com.strongwine.strongwine.service.report.OrderReportGenerator orderReportGenerator;

    @Test
    void testDownloadTemplateReturnsFileBytes() {
        when(templateService.generateProductImportTemplate()).thenReturn(new byte[]{1, 2, 3});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator, orderReportGenerator);

        ResponseEntity<byte[]> response = controller.downloadTemplate();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void testPreviewUploadReturnsValidSummary() {
        when(excelStrategy.importData(any(), any())).thenReturn(new ImportResult<>());
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator, orderReportGenerator);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        ResponseEntity<ExcelPreviewResponseDto> response = controller.previewExcel(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testExportInventoryReturnsValidExcelBytes() {
        when(inventoryStockReportGenerator.generateReport(any())).thenReturn(new byte[]{4, 5, 6});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator, orderReportGenerator);

        ResponseEntity<byte[]> response = controller.exportInventory();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void testExportOrdersReturnsValidExcelBytes() {
        when(orderReportGenerator.generateReport(any())).thenReturn(new byte[]{7, 8, 9});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService, inventoryStockReportGenerator, orderReportGenerator);

        ResponseEntity<byte[]> response = controller.exportOrders();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void testExecuteImportForwardsStrictModeAndWritesAdminAuditLog() {
        AdminAuditLogService auditLogService = mock(AdminAuditLogService.class);
        AdminExcelApiController controller = new AdminExcelApiController(
                excelStrategy, templateService, inventoryStockReportGenerator, orderReportGenerator);
        ReflectionTestUtils.setField(controller, "adminAuditLogService", auditLogService);

        ImportResult<com.strongwine.strongwine.entity.Wine> importResult = new ImportResult<>();
        importResult.setInsertedCount(2);
        importResult.addSuccess(new com.strongwine.strongwine.entity.Wine());
        importResult.addSuccess(new com.strongwine.strongwine.entity.Wine());
        when(excelStrategy.importData(any(), any(ImportOptions.class))).thenReturn(importResult);

        MockMultipartFile file = new MockMultipartFile(
                "file", "wines.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3, 4});
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("admin1", "n/a");

        ResponseEntity<ExcelPreviewResponseDto> response = controller.executeImport(file, "ADD", 1L, true, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ImportOptions> optionsCaptor = ArgumentCaptor.forClass(ImportOptions.class);
        verify(excelStrategy).importData(any(), optionsCaptor.capture());
        assertTrue(optionsCaptor.getValue().isStrictMode());
        assertFalse(optionsCaptor.getValue().isDryRun());
        verify(auditLogService).log(eq("admin1"), eq("EXCEL_IMPORT_WINES"), eq("WINE_IMPORT"), isNull(), isNull(), any(), any());
    }
}
