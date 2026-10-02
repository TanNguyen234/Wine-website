package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ExcelPreviewResponseDto;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.report.ExcelTemplateService;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/excel")
public class AdminExcelApiController {

    private final ExcelWineImportStrategy excelImportStrategy;
    private final ExcelTemplateService excelTemplateService;

    public AdminExcelApiController(ExcelWineImportStrategy excelImportStrategy,
                                   ExcelTemplateService excelTemplateService) {
        this.excelImportStrategy = excelImportStrategy;
        this.excelTemplateService = excelTemplateService;
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
