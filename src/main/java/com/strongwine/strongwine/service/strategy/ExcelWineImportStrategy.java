package com.strongwine.strongwine.service.strategy;

import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.exception.ExcelImportValidationException;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Year;
import java.util.*;

@Component
public class ExcelWineImportStrategy implements DataImportStrategy<Wine> {

    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList("Red", "White", "Rose", "Sparkling", "Traditional"));

    private static final Map<String, String> DEFAULT_IMAGE_BY_TYPE = Map.of(
            "Red", "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80",
            "White", "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80",
            "Rose", "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80",
            "Sparkling", "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80",
            "Traditional", "/images/bottles/mo-yentu.svg"
    );

    private final WineRepository wineRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private TransactionTemplate transactionTemplate;

    @Autowired
    public ExcelWineImportStrategy(WineRepository wineRepository,
                                  CategoryRepository categoryRepository,
                                  InventoryRepository inventoryRepository,
                                  WarehouseRepository warehouseRepository) {
        this(wineRepository, categoryRepository, inventoryRepository, warehouseRepository, null);
    }

    public ExcelWineImportStrategy(WineRepository wineRepository,
                                  CategoryRepository categoryRepository,
                                  InventoryRepository inventoryRepository,
                                  WarehouseRepository warehouseRepository,
                                  TransactionTemplate transactionTemplate) {
        this.wineRepository = wineRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Autowired(required = false)
    public void setPlatformTransactionManager(PlatformTransactionManager transactionManager) {
        if (this.transactionTemplate == null && transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
        }
    }

    @Override
    public boolean supportsFormat(String fileExtension) {
        return "xlsx".equalsIgnoreCase(fileExtension) || "xls".equalsIgnoreCase(fileExtension);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult<Wine> importData(InputStream inputStream, ImportOptions options) {
        ImportOptions effectiveOptions = options != null ? options : new ImportOptions();
        if (effectiveOptions.isStrictMode() && !effectiveOptions.isDryRun() && transactionTemplate != null) {
            return transactionTemplate.execute(status -> {
                ImportResult<Wine> txResult = executeImportInternal(inputStream, effectiveOptions);
                if (txResult.getErrorCount() > 0) {
                    status.setRollbackOnly();
                    throw new ExcelImportValidationException(
                            "Strict mode import failed with " + txResult.getErrorCount() + " validation error(s). All changes rolled back.",
                            txResult.getErrors());
                }
                return txResult;
            });
        }

        ImportResult<Wine> result = executeImportInternal(inputStream, effectiveOptions);
        if (effectiveOptions.isStrictMode() && !effectiveOptions.isDryRun() && result.getErrorCount() > 0) {
            throw new ExcelImportValidationException(
                    "Strict mode import failed with " + result.getErrorCount() + " validation error(s). All changes rolled back.",
                    result.getErrors());
        }
        return result;
    }

    private ImportResult<Wine> executeImportInternal(InputStream inputStream, ImportOptions options) {
        ImportResult<Wine> result = new ImportResult<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
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
                    String name = getCellString(row.getCell(0), evaluator);
                    String type = normalizeType(getCellString(row.getCell(1), evaluator));
                    Integer year = getCellInteger(row.getCell(2), evaluator);
                    BigDecimal price = getCellBigDecimal(row.getCell(3), evaluator);
                    String country = getCellString(row.getCell(4), evaluator);
                    String description = getCellString(row.getCell(5), evaluator);
                    String categoryName = getCellString(row.getCell(6), evaluator);
                    Integer quantity = getCellInteger(row.getCell(7), evaluator);
                    String imageUrl = getCellString(row.getCell(8), evaluator);

                    // Validation
                    if (name == null || name.isBlank()) {
                        result.addError(new ImportErrorItem(rowNum, "Tên Sản Phẩm", "", "Tên sản phẩm không được để trống"));
                        continue;
                    }
                    if (type == null || !ALLOWED_TYPES.contains(type)) {
                        result.addError(new ImportErrorItem(rowNum, "Loại Rượu", type, "Loại rượu phải là: Red, White, Rose, Sparkling, hoặc Traditional"));
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
                    if (imageUrl != null && !imageUrl.isBlank() && !isValidImageUrl(imageUrl.trim())) {
                        result.addError(new ImportErrorItem(rowNum, "Image URL", imageUrl.trim(), "Đường dẫn ảnh phải bắt đầu bằng https://, http://, /uploads/ hoặc /images/"));
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

                    String resolvedImageUrl = (imageUrl != null && !imageUrl.isBlank())
                            ? imageUrl.trim()
                            : DEFAULT_IMAGE_BY_TYPE.getOrDefault(type, DEFAULT_IMAGE_BY_TYPE.get("Red"));

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
                        if (imageUrl != null && !imageUrl.isBlank()) {
                            wine.setImageUrl(imageUrl.trim());
                        } else if (wine.getImageUrl() == null || wine.getImageUrl().isBlank()) {
                            wine.setImageUrl(resolvedImageUrl);
                        }
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
                        wine.setImageUrl(resolvedImageUrl);
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
        if (val.equalsIgnoreCase("vang hồng") || val.equalsIgnoreCase("rose") || val.equalsIgnoreCase("rosé")) return "Rose";
        if (val.equalsIgnoreCase("vang sủi") || val.equalsIgnoreCase("sparkling") || val.equalsIgnoreCase("champagne")) return "Sparkling";
        if (val.equalsIgnoreCase("traditional") || val.equalsIgnoreCase("truyền thống") || val.equalsIgnoreCase("rượu truyền thống")) return "Traditional";
        return val;
    }

    private boolean isValidImageUrl(String url) {
        return (url.startsWith("https://")
                || url.startsWith("http://")
                || url.startsWith("/uploads/")
                || url.startsWith("/images/"))
                && !url.contains("..");
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        CellType effectiveType = cell.getCellType();
        if (effectiveType == CellType.FORMULA && evaluator != null) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) return null;
            return switch (evaluated.getCellType()) {
                case STRING -> evaluated.getStringValue().trim();
                case NUMERIC -> String.valueOf((long) evaluated.getNumberValue());
                case BOOLEAN -> String.valueOf(evaluated.getBooleanValue());
                default -> null;
            };
        }
        if (effectiveType == CellType.STRING) return cell.getStringCellValue().trim();
        if (effectiveType == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Integer getCellInteger(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) return null;
            if (evaluated.getCellType() == CellType.NUMERIC) return (int) evaluated.getNumberValue();
            if (evaluated.getCellType() == CellType.STRING) {
                try { return Integer.parseInt(evaluated.getStringValue().trim()); } catch (NumberFormatException ignored) {}
            }
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try { return Integer.parseInt(cell.getStringCellValue().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private BigDecimal getCellBigDecimal(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) return null;
            if (evaluated.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(evaluated.getNumberValue());
            if (evaluated.getCellType() == CellType.STRING) {
                try { return new BigDecimal(evaluated.getStringValue().trim()); } catch (NumberFormatException ignored) {}
            }
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.STRING) {
            try { return new BigDecimal(cell.getStringCellValue().trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
