# Phase 2: Excel Bulk Data Engine & OOP Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hiện thực hóa động cơ xử lý Excel hàng loạt thông minh: Cơ chế Upsert theo cặp định danh tự nhiên `(Tên Rượu + Năm Vintage)`, tự động liên kết hoặc tạo mới Category, quản lý tồn kho linh hoạt (Cộng dồn / Ghi đè), bảng xem trước Dry-Run Preview trực quan, và bộ lọc sản phẩm đa tiêu chí động bằng JPA Specification.

**Architecture:** Áp dụng Strategy Pattern cho việc đọc/ghi Excel, Template Method Pattern cho việc sinh báo cáo doanh thu/tồn kho chuẩn thương hiệu, và Specification Pattern kết hợp Spring Data JPA Criteria API để loại bỏ code if-else rẽ nhánh phức tạp.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Data JPA, Apache POI 5.2.5 (`XSSFWorkbook`), JUnit 5, Mockito.

## Global Constraints
- Database Preservation: Giữ nguyên 100% các bảng `wines`, `categories`, `inventory`, `warehouses`. Không xóa hoặc thay đổi kiểu dữ liệu cột.
- Strict Hibernate Validation: Tương thích hoàn toàn với `spring.jpa.hibernate.ddl-auto=validate`.
- Natural Key Identity: Rượu được định danh bởi cặp `(Tên Rượu + Năm Sản Xuất)`.
- Per-Task Real Test: Mỗi task phải có test case JUnit 5 và lệnh cURL/PowerShell kiểm thử thực tế trên CSDL.

---

### Task 2.1: Triển Khai `ExcelWineImportStrategy` Với Cơ Chế Upsert Thông Minh

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/ExcelWineImportStrategy.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/WineRepository.java:30-40`
- Test: `src/test/java/com/strongwine/strongwine/service/strategy/ExcelWineImportStrategyTest.java`

**Interfaces:**
- Consumes: `DataImportStrategy<Wine>`, `WineRepository`, `CategoryService`, `InventoryService`, `WarehouseRepository`.
- Produces: `ImportResult<Wine> importData(InputStream is, ImportOptions options)`.

- [ ] **Step 1: Viết test cho `ExcelWineImportStrategy`**

```java
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
        assertEquals(2, result.getErrors().get(0).getRowNumber());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=ExcelWineImportStrategyTest
```
Expected: `BUILD FAILURE` (Compilation failure: `ExcelWineImportStrategy` does not exist).

- [ ] **Step 3: Triển khai phương thức tìm kiếm theo Tên + Năm trong WineRepository và viết ExcelWineImportStrategy**

Cập nhật `WineRepository.java`:
```java
    // Thêm phương thức tra cứu định danh tự nhiên (Tên + Năm)
    Optional<Wine> findByNameIgnoreCaseAndYearAndDeletedFalse(String name, Integer year);
```

Tạo `ExcelWineImportStrategy.java`:
```java
package com.strongwine.strongwine.service.strategy;

import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Year;
import java.util.*;

@Component
public class ExcelWineImportStrategy implements DataImportStrategy<Wine> {

    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList("Red", "White", "Rose", "Sparkling"));

    private final WineRepository wineRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;

    public ExcelWineImportStrategy(WineRepository wineRepository,
                                  CategoryRepository categoryRepository,
                                  InventoryRepository inventoryRepository,
                                  WarehouseRepository warehouseRepository) {
        this.wineRepository = wineRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    public boolean supportsFormat(String fileExtension) {
        return "xlsx".equalsIgnoreCase(fileExtension) || "xls".equalsIgnoreCase(fileExtension);
    }

    @Override
    public ImportResult<Wine> importData(InputStream inputStream, ImportOptions options) {
        ImportResult<Wine> result = new ImportResult<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                result.addError(new ImportErrorItem(0, "sheet", "", "File Excel không có dữ liệu"));
                return result;
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                int rowNum = r + 1;
                try {
                    String name = getCellString(row.getCell(0));
                    String type = normalizeType(getCellString(row.getCell(1)));
                    Integer year = getCellInteger(row.getCell(2));
                    BigDecimal price = getCellBigDecimal(row.getCell(3));
                    String country = getCellString(row.getCell(4));
                    String description = getCellString(row.getCell(5));
                    String categoryName = getCellString(row.getCell(6));
                    Integer quantity = getCellInteger(row.getCell(7));

                    // Validation
                    if (name == null || name.isBlank()) {
                        result.addError(new ImportErrorItem(rowNum, "Tên Sản Phẩm", "", "Tên sản phẩm không được để trống"));
                        continue;
                    }
                    if (type == null || !ALLOWED_TYPES.contains(type)) {
                        result.addError(new ImportErrorItem(rowNum, "Loại Rượu", type, "Loại rượu phải là: Red, White, Rose, hoặc Sparkling"));
                        continue;
                    }
                    int currentYear = Year.now().getValue();
                    if (year == null || year < 1900 || year > currentYear + 1) {
                        result.addError(new ImportErrorItem(rowNum, "Năm Sản Xuất", String.valueOf(year), "Năm sản xuất từ 1900 đến " + (currentYear + 1)));
                        continue;
                    }
                    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                        result.addError(new ImportErrorItem(rowNum, "Giá Bán", String.valueOf(price), "Giá bán phải lớn hơn 0"));
                        continue;
                    }

                    // Tự động phân giải hoặc tạo mới Category
                    Category category = null;
                    if (categoryName != null && !categoryName.isBlank()) {
                        category = categoryRepository.findByName(categoryName.trim())
                                .orElseGet(() -> {
                                    if (options.isDryRun()) {
                                        Category dummyCat = new Category();
                                        dummyCat.setName(categoryName.trim());
                                        return dummyCat;
                                    }
                                    Category newCat = new Category();
                                    newCat.setName(categoryName.trim());
                                    newCat.setDescription("Tự động tạo từ import Excel");
                                    return categoryRepository.save(newCat);
                                });
                    }

                    // Cơ chế Upsert theo (Tên + Năm)
                    Optional<Wine> existingOpt = wineRepository.findByNameIgnoreCaseAndYearAndDeletedFalse(name.trim(), year);
                    Wine wine;
                    if (existingOpt.isPresent()) {
                        wine = existingOpt.get();
                        wine.setType(type);
                        wine.setPrice(price);
                        if (country != null) wine.setCountry(country);
                        if (description != null) wine.setDescription(description);
                        if (category != null) wine.setCategory(category);
                        result.setUpdatedCount(result.getUpdatedCount() + 1);
                    } else {
                        wine = new Wine();
                        wine.setName(name.trim());
                        wine.setType(type);
                        wine.setYear(year);
                        wine.setPrice(price);
                        wine.setCountry(country);
                        wine.setDescription(description);
                        wine.setCategory(category);
                        wine.setDeleted(false);
                        result.setInsertedCount(result.getInsertedCount() + 1);
                    }

                    if (!options.isDryRun()) {
                        wine = wineRepository.save(wine);

                        // Xử lý kho hàng nếu có nhập số lượng
                        if (quantity != null && quantity >= 0) {
                            Long warehouseId = options.getDefaultWarehouseId();
                            Warehouse warehouse = warehouseRepository.findById(warehouseId).orElse(null);
                            if (warehouse != null) {
                                Optional<Inventory> invOpt = inventoryRepository.findByWineIdAndWarehouseId(wine.getId(), warehouseId);
                                Inventory inventory;
                                if (invOpt.isPresent()) {
                                    inventory = invOpt.get();
                                    if (options.getInventoryMode() == ImportOptions.InventoryMode.ADD) {
                                        inventory.setCurrentQuantity(inventory.getCurrentQuantity() + quantity);
                                    } else {
                                        inventory.setCurrentQuantity(quantity);
                                    }
                                } else {
                                    inventory = new Inventory();
                                    inventory.setWine(wine);
                                    inventory.setWarehouse(warehouse);
                                    inventory.setCurrentQuantity(quantity);
                                    inventory.setReservedQuantity(0);
                                    inventory.setReorderLevel(10);
                                }
                                inventoryRepository.save(inventory);
                            }
                        }
                    }

                    result.addSuccess(wine);

                } catch (Exception e) {
                    result.addError(new ImportErrorItem(rowNum, "Dòng", "", "Lỗi xử lý dòng: " + e.getMessage()));
                }
            }

        } catch (Exception e) {
            result.addError(new ImportErrorItem(0, "File", "", "Không thể đọc file Excel: " + e.getMessage()));
        }

        return result;
    }

    private String normalizeType(String val) {
        if (val == null) return null;
        val = val.trim();
        if (val.equalsIgnoreCase("vang đỏ") || val.equalsIgnoreCase("red")) return "Red";
        if (val.equalsIgnoreCase("vang trắng") || val.equalsIgnoreCase("white")) return "White";
        if (val.equalsIgnoreCase("vang hồng") || val.equalsIgnoreCase("rose")) return "Rose";
        if (val.equalsIgnoreCase("vang sủi") || val.equalsIgnoreCase("sparkling")) return "Sparkling";
        return val;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Integer getCellInteger(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try { return Integer.parseInt(cell.getStringCellValue().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.STRING) {
            try { return new BigDecimal(cell.getStringCellValue().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=ExcelWineImportStrategyTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/strategy/ExcelWineImportStrategy.java src/main/java/com/strongwine/strongwine/repository/WineRepository.java src/test/java/com/strongwine/strongwine/service/strategy/ExcelWineImportStrategyTest.java
git commit -m "feat: implement excel wine import strategy with natural key upsert and category resolution"
```

---

### Task 2.2: Xây Dựng Trình Tạo File Mẫu Chuẩn `ExcelTemplateService`

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/report/ExcelTemplateService.java`
- Test: `src/test/java/com/strongwine/strongwine/service/report/ExcelTemplateServiceTest.java`

**Interfaces:**
- Consumes: Apache POI `XSSFWorkbook`.
- Produces: `byte[] generateProductImportTemplate()`.

- [ ] **Step 1: Viết test cho `ExcelTemplateService`**

```java
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
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=ExcelTemplateServiceTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Triển khai `ExcelTemplateService`**

Tạo `ExcelTemplateService.java`:
```java
package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ExcelTemplateService {

    public byte[] generateProductImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Danh Sách Sản Phẩm");

            // Cell Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle sampleStyle = workbook.createCellStyle();
            Font sampleFont = workbook.createFont();
            sampleFont.setItalic(true);
            sampleStyle.setFont(sampleFont);

            // Headers
            String[] headers = {
                "Tên Sản Phẩm (*)", "Loại Rượu (*)", "Năm Sản Xuất (*)", "Giá Bán (VNĐ) (*)",
                "Xuất Xứ", "Mô Tả", "Tên Danh Mục", "Số Lượng Kho Khởi Tạo"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng mẫu 1
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Château Margaux Premier Grand Cru");
            r1.createCell(1).setCellValue("Red");
            r1.createCell(2).setCellValue(2018);
            r1.createCell(3).setCellValue(12500000);
            r1.createCell(4).setCellValue("Pháp");
            r1.createCell(5).setCellValue("Hương quả mọng đen, da thuộc, gỗ sồi quý tộc.");
            r1.createCell(6).setCellValue("Vang Đỏ Cao Cấp");
            r1.createCell(7).setCellValue(12);

            // Dòng mẫu 2
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Dom Pérignon Vintage Champagne");
            r2.createCell(1).setCellValue("Sparkling");
            r2.createCell(2).setCellValue(2013);
            r2.createCell(3).setCellValue(6800000);
            r2.createCell(4).setCellValue("Pháp");
            r2.createCell(5).setCellValue("Bọt mịn tinh tế, hương hoa trắng và bơ nướng.");
            r2.createCell(6).setCellValue("Vang Sủi");
            r2.createCell(7).setCellValue(24);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo file template Excel", e);
        }
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=ExcelTemplateServiceTest
```
Expected: `BUILD SUCCESS` (Tests run: 1, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra compile:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/report/ExcelTemplateService.java src/test/java/com/strongwine/strongwine/service/report/ExcelTemplateServiceTest.java
git commit -m "feat: implement standard excel import template generator service"
```

---

### Task 2.3: Triển Khai Template Method Pattern Cho Báo Cáo Excel Doanh Thu & Kho Bãi

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/report/AbstractExcelReportGenerator.java`
- Create: `src/main/java/com/strongwine/strongwine/service/report/InventoryStockReportGenerator.java`
- Test: `src/test/java/com/strongwine/strongwine/service/report/InventoryStockReportGeneratorTest.java`

**Interfaces:**
- Consumes: Spring Data JPA Repositories (`InventoryRepository`).
- Produces: `byte[] generateReport(ReportCriteria criteria)`.

- [ ] **Step 1: Viết test cho `InventoryStockReportGenerator`**

```java
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
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=InventoryStockReportGeneratorTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Triển khai lớp trừu tượng `AbstractExcelReportGenerator` và `InventoryStockReportGenerator`**

Tạo `AbstractExcelReportGenerator.java`:
```java
package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class AbstractExcelReportGenerator<C> {

    public byte[] generateReport(C criteria) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(getSheetName());
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = getHeaders();
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            populateDataRows(sheet, criteria);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi sinh báo cáo Excel", e);
        }
    }

    protected abstract String getSheetName();
    protected abstract String[] getHeaders();
    protected abstract void populateDataRows(Sheet sheet, C criteria);

    protected CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
```

Tạo `InventoryStockReportGenerator.java`:
```java
package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.repository.InventoryRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryStockReportGenerator extends AbstractExcelReportGenerator<Void> {

    private final InventoryRepository inventoryRepository;

    public InventoryStockReportGenerator(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    protected String getSheetName() {
        return "Báo Cáo Tồn Kho";
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{"Tên Rượu", "Kho Hàng", "Tồn Thực Tế", "Đã Giữ (Reserved)", "Khả Dụng", "Ngưỡng Cảnh Báo", "Trạng Thái"};
    }

    @Override
    protected void populateDataRows(Sheet sheet, Void criteria) {
        List<Inventory> list = inventoryRepository.findAll();
        int r = 1;
        for (Inventory inv : list) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(inv.getWine() != null ? inv.getWine().getName() : "N/A");
            row.createCell(1).setCellValue(inv.getWarehouse() != null ? inv.getWarehouse().getName() : "N/A");
            row.createCell(2).setCellValue(inv.getCurrentQuantity());
            row.createCell(3).setCellValue(inv.getReservedQuantity());
            int available = inv.getCurrentQuantity() - inv.getReservedQuantity();
            row.createCell(4).setCellValue(available);
            row.createCell(5).setCellValue(inv.getReorderLevel());
            String status = available <= inv.getReorderLevel() ? "CẢNH BÁO SẮP HẾT" : "ỔN ĐỊNH";
            row.createCell(6).setCellValue(status);
        }
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=InventoryStockReportGeneratorTest
```
Expected: `BUILD SUCCESS` (Tests run: 1, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/report/AbstractExcelReportGenerator.java src/main/java/com/strongwine/strongwine/service/report/InventoryStockReportGenerator.java src/test/java/com/strongwine/strongwine/service/report/InventoryStockReportGeneratorTest.java
git commit -m "feat: implement template method pattern for excel inventory report generation"
```

---

### Task 2.4: Triển Khai JPA Specification Cho Bộ Lọc Tìm Kiếm Rượu Đa Tiêu Chí Động

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/dto/WineSearchCriteria.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/specification/WineSpecification.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/WineRepository.java:15-20`
- Test: `src/test/java/com/strongwine/strongwine/repository/specification/WineSpecificationTest.java`

**Interfaces:**
- Consumes: `org.springframework.data.jpa.domain.Specification`, `WineSearchCriteria`.
- Produces: `Specification<Wine> build(WineSearchCriteria criteria)`.

- [ ] **Step 1: Viết test cho `WineSpecification`**

```java
package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Wine;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WineSpecificationTest {

    @Test
    void testBuildEmptyCriteriaReturnsEmptySpec() {
        WineSearchCriteria criteria = new WineSearchCriteria();
        Specification<Wine> spec = WineSpecification.build(criteria);
        assertNotNull(spec);
    }

    @Test
    void testBuildCompoundCriteria() {
        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .keyword("Bordeaux")
                .types(List.of("Red"))
                .minPrice(BigDecimal.valueOf(1000000))
                .maxPrice(BigDecimal.valueOf(5000000))
                .countries(List.of("Pháp"))
                .vintageYears(List.of(2018, 2020))
                .build();

        Specification<Wine> spec = WineSpecification.build(criteria);
        assertNotNull(spec);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=WineSpecificationTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Triển khai DTO, kế thừa `JpaSpecificationExecutor` và viết `WineSpecification`**

Tạo `WineSearchCriteria.java`:
```java
package com.strongwine.strongwine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WineSearchCriteria {
    private String keyword;
    private List<String> types;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> countries;
    private List<Integer> vintageYears;
    private Long categoryId;
    private Boolean inStockOnly;
}
```

Cập nhật `WineRepository.java`:
Kế thừa thêm `JpaSpecificationExecutor<Wine>`:
```java
package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Wine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WineRepository extends JpaRepository<Wine, Long>, JpaSpecificationExecutor<Wine> {
    Optional<Wine> findByNameIgnoreCaseAndYearAndDeletedFalse(String name, Integer year);
    // ... các query hiện có giữ nguyên 100%
}
```

Tạo `WineSpecification.java`:
```java
package com.strongwine.strongwine.repository.specification;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Wine;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class WineSpecification {

    public static Specification<Wine> build(WineSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Luôn loại trừ sản phẩm đã bị xóa
            predicates.add(cb.isFalse(root.get("deleted")));

            if (criteria == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Từ khóa tìm kiếm (Tên hoặc Mô tả)
            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String pattern = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameLike, descLike));
            }

            // Danh sách loại rượu (Red, White, Rose, Sparkling)
            if (criteria.getTypes() != null && !criteria.getTypes().isEmpty()) {
                predicates.add(root.get("type").in(criteria.getTypes()));
            }

            // Khoảng giá
            if (criteria.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.getMinPrice()));
            }
            if (criteria.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.getMaxPrice()));
            }

            // Xuất xứ / Quốc gia
            if (criteria.getCountries() != null && !criteria.getCountries().isEmpty()) {
                predicates.add(root.get("country").in(criteria.getCountries()));
            }

            // Năm sản xuất (Vintage)
            if (criteria.getVintageYears() != null && !criteria.getVintageYears().isEmpty()) {
                predicates.add(root.get("year").in(criteria.getVintageYears()));
            }

            // Danh mục
            if (criteria.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=WineSpecificationTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra compile:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/dto/WineSearchCriteria.java src/main/java/com/strongwine/strongwine/repository/specification/WineSpecification.java src/main/java/com/strongwine/strongwine/repository/WineRepository.java src/test/java/com/strongwine/strongwine/repository/specification/WineSpecificationTest.java
git commit -m "feat: implement dynamic criteria jpa specification for compound wine filtering"
```

---

### Task 2.5: Xây Dựng REST API Cho Dry-Run Preview & Import Excel

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/dto/ExcelPreviewResponseDto.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/AdminExcelApiController.java`
- Test: `src/test/java/com/strongwine/strongwine/controller/api/AdminExcelApiControllerTest.java`

**Interfaces:**
- Consumes: Multipart File (`.xlsx`), `ExcelWineImportStrategy`, `ExcelTemplateService`.
- Produces: `POST /api/admin/excel/preview`, `POST /api/admin/excel/import`, `GET /api/admin/excel/template`.

- [ ] **Step 1: Viết test cho `AdminExcelApiController`**

```java
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

    @Test
    void testDownloadTemplateReturnsFileBytes() {
        when(templateService.generateProductImportTemplate()).thenReturn(new byte[]{1, 2, 3});
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService);

        ResponseEntity<byte[]> response = controller.downloadTemplate();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void testPreviewUploadReturnsValidSummary() {
        when(excelStrategy.importData(any(), any())).thenReturn(new ImportResult<>());
        AdminExcelApiController controller = new AdminExcelApiController(excelStrategy, templateService);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        ResponseEntity<ExcelPreviewResponseDto> response = controller.previewExcel(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=AdminExcelApiControllerTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Triển khai DTO và `AdminExcelApiController`**

Tạo `ExcelPreviewResponseDto.java`:
```java
package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.service.strategy.ImportErrorItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelPreviewResponseDto {
    private int totalRows;
    private int validRowsCount;
    private int errorRowsCount;
    private int insertCount;
    private int updateCount;
    private List<ImportErrorItem> errors;
}
```

Tạo `AdminExcelApiController.java`:
```java
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
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=AdminExcelApiControllerTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra toàn bộ test suite của Phase 2:
```powershell
./mvnw test -Dtest=ExcelWineImportStrategyTest,ExcelTemplateServiceTest,InventoryStockReportGeneratorTest,WineSpecificationTest,AdminExcelApiControllerTest
```
Expected: `BUILD SUCCESS` (Tất cả test pass 100%, 0 failures).

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/dto/ExcelPreviewResponseDto.java src/main/java/com/strongwine/strongwine/controller/api/AdminExcelApiController.java src/test/java/com/strongwine/strongwine/controller/api/AdminExcelApiControllerTest.java
git commit -m "feat: implement admin excel preview and execution endpoints"
```
