package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ExcelPreviewResponseDto;
import com.strongwine.strongwine.dto.RevenueReportCriteria;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.report.ExcelTemplateService;
import com.strongwine.strongwine.service.report.InventoryStockReportGenerator;
import com.strongwine.strongwine.service.report.OrderReportGenerator;
import com.strongwine.strongwine.service.report.RevenueReportGenerator;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/admin/excel")
public class AdminExcelApiController {

    private final ExcelWineImportStrategy excelImportStrategy;
    private final ExcelTemplateService excelTemplateService;
    private final InventoryStockReportGenerator inventoryStockReportGenerator;
    private final OrderReportGenerator orderReportGenerator;
    private final RevenueReportGenerator revenueReportGenerator;

    public AdminExcelApiController(ExcelWineImportStrategy excelImportStrategy,
                                   ExcelTemplateService excelTemplateService,
                                   InventoryStockReportGenerator inventoryStockReportGenerator) {
        this(excelImportStrategy, excelTemplateService, inventoryStockReportGenerator, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminExcelApiController(ExcelWineImportStrategy excelImportStrategy,
                                   ExcelTemplateService excelTemplateService,
                                   InventoryStockReportGenerator inventoryStockReportGenerator,
                                   OrderReportGenerator orderReportGenerator,
                                   RevenueReportGenerator revenueReportGenerator) {
        this.excelImportStrategy = excelImportStrategy;
        this.excelTemplateService = excelTemplateService;
        this.inventoryStockReportGenerator = inventoryStockReportGenerator;
        this.orderReportGenerator = orderReportGenerator;
        this.revenueReportGenerator = revenueReportGenerator;
    }

    @GetMapping("/export/revenue")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        RevenueReportCriteria criteria = new RevenueReportCriteria(days, fromDate, toDate);
        byte[] bytes = revenueReportGenerator.generateReport(criteria);
        String filename = String.format("Bao_Cao_Doanh_Thu_StrongWine_%s.xlsx",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/export/orders")
    public ResponseEntity<byte[]> exportOrders() {
        byte[] bytes = orderReportGenerator.generateReport(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strongwine_orders_report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/export/inventory")
    public ResponseEntity<byte[]> exportInventory() {
        byte[] bytes = inventoryStockReportGenerator.generateReport(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strongwine_inventory_report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = excelTemplateService.generateProductImportTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strongwine_product_import_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/preview")
    public ResponseEntity<ExcelPreviewResponseDto> previewExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ImportOptions options = ImportOptions.builder().dryRun(true).build();
            ImportResult<Wine> result = excelImportStrategy.importData(file.getInputStream(), options);

            ExcelPreviewResponseDto dto = ExcelPreviewResponseDto.builder()
                    .totalRows(result.getTotalCount())
                    .validRowsCount(result.getSuccessCount())
                    .errorRowsCount(result.getErrorCount())
                    .insertCount(result.getInsertedCount())
                    .updateCount(result.getUpdatedCount())
                    .errors(result.getErrors())
                    .build();

            return ResponseEntity.ok(dto);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<ExcelPreviewResponseDto> executeImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "inventoryMode", defaultValue = "ADD") String inventoryModeStr,
            @RequestParam(value = "warehouseId", defaultValue = "1") Long warehouseId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ImportOptions.InventoryMode mode = "REPLACE".equalsIgnoreCase(inventoryModeStr)
                    ? ImportOptions.InventoryMode.REPLACE
                    : ImportOptions.InventoryMode.ADD;

            ImportOptions options = ImportOptions.builder()
                    .dryRun(false)
                    .inventoryMode(mode)
                    .defaultWarehouseId(warehouseId)
                    .build();

            ImportResult<Wine> result = excelImportStrategy.importData(file.getInputStream(), options);

            ExcelPreviewResponseDto dto = ExcelPreviewResponseDto.builder()
                    .totalRows(result.getTotalCount())
                    .validRowsCount(result.getSuccessCount())
                    .errorRowsCount(result.getErrorCount())
                    .insertCount(result.getInsertedCount())
                    .updateCount(result.getUpdatedCount())
                    .errors(result.getErrors())
                    .build();

            return ResponseEntity.ok(dto);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
