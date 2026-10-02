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
                        if (quantity != null && quantity >= 0 && options.getDefaultWarehouseId() != null) {
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
