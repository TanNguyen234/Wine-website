package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.service.EnumDisplayService;
import com.strongwine.strongwine.util.AddressTextUtils;

import com.strongwine.strongwine.dto.ProductDto;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.WineService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Wine operations
 */
@RestController
@RequestMapping("/api/wines")
public class WineApiController {

    @Autowired
    private WineService wineService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * Get all wines
     */
    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllWines() {
        List<Wine> wines = wineService.getAllWines();
        Map<Long, Integer> stockMap = inventoryService.getAvailableStockByWineIds(wines.stream().map(Wine::getId).toList());
        return ResponseEntity.ok(wines.stream().map(w -> toDto(w, stockMap)).toList());
    }

    /**
     * Get wine by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getWineById(@PathVariable Long id) {
        Optional<Wine> wine = wineService.getWineById(id);
        return wine.map(w -> {
                    int available = inventoryService.getAvailableStockByWineIds(List.of(w.getId())).getOrDefault(w.getId(), 0);
                    return ResponseEntity.ok(toDto(w, available));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search wines
     */
    private static final EnumDisplayService DEFAULT_ENUM_DISPLAY_SERVICE = new EnumDisplayService();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EnumDisplayService enumDisplayService;

    @GetMapping("/search")
    public ResponseEntity<List<ProductDto>> searchWines(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        String normalizedType = normalizeWineType(type, true);
        List<Wine> wines = wineService.searchWines(name, normalizedType, country, year, minPrice, maxPrice);
        Map<Long, Integer> stockMap = inventoryService.getAvailableStockByWineIds(wines.stream().map(Wine::getId).toList());
        return ResponseEntity.ok(wines.stream().map(w -> toDto(w, stockMap)).toList());
    }

    /**
     * Create a new wine (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductDto> createWine(@Valid @RequestBody ProductDto dto) {
        Wine wine = fromDto(dto);
        Wine createdWine = wineService.createWine(wine);
        int available = inventoryService.getAvailableStockByWineIds(List.of(createdWine.getId())).getOrDefault(createdWine.getId(), 0);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(createdWine, available));
    }

    /**
     * Update a wine (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateWine(@PathVariable Long id, @Valid @RequestBody ProductDto dto) {
        Wine updatedWine = wineService.updateWine(id, fromDto(dto));
        int available = inventoryService.getAvailableStockByWineIds(List.of(updatedWine.getId())).getOrDefault(updatedWine.getId(), 0);
        return ResponseEntity.ok(toDto(updatedWine, available));
    }

    /**
     * Delete a wine (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWine(@PathVariable Long id) {
        wineService.deleteWine(id);
        return ResponseEntity.noContent().build();
    }

    private ProductDto toDto(Wine wine, Map<Long, Integer> stockMap) {
        return toDto(wine, stockMap.getOrDefault(wine.getId(), 0));
    }

    private ProductDto toDto(Wine wine, int availableStock) {
        EnumDisplayService display = enumDisplayService != null ? enumDisplayService : DEFAULT_ENUM_DISPLAY_SERVICE;
        String effectiveType = normalizeWineType(wine.getType(), false);
        int safeStock = Math.max(0, availableStock);
        String stockStatus = safeStock <= 0
                ? "OUT_OF_STOCK"
                : (safeStock <= EnumDisplayService.LOW_STOCK_THRESHOLD ? "LOW_STOCK" : "IN_STOCK");

        ProductDto dto = new ProductDto();
        dto.setId(wine.getId());
        dto.setName(wine.getName());
        dto.setType(effectiveType != null ? effectiveType : wine.getType());
        dto.setYear(wine.getYear());
        dto.setPrice(wine.getPrice());
        dto.setFinalPrice(wine.getPrice());
        dto.setDescription(wine.getDescription());
        dto.setCountry(wine.getCountry());
        dto.setImageUrl(wine.getImageUrl());
        dto.setCategoryId(wine.getCategory() != null ? wine.getCategory().getId() : null);
        dto.setCategoryName(wine.getCategory() != null ? wine.getCategory().getName() : null);
        dto.setAvailableStock(safeStock);
        dto.setTypeLabel(display.wineType(dto.getType()));
        dto.setTypeTone(display.wineTypeTone(dto.getType()));
        dto.setStockStatus(stockStatus);
        dto.setStockBadgeLabel(display.stockStatusLabel(safeStock));
        dto.setStockBadgeTone(display.stockStatusTone(safeStock));
        dto.setInStock(safeStock > 0);
        return dto;
    }

    private Wine fromDto(ProductDto dto) {
        Wine wine = new Wine();
        wine.setName(dto.getName());
        wine.setType(normalizeWineType(dto.getType(), false));
        wine.setYear(dto.getYear());
        wine.setPrice(dto.getPrice());
        wine.setDescription(dto.getDescription());
        wine.setCountry(dto.getCountry());
        wine.setImageUrl(dto.getImageUrl());
        wine.setCategory(resolveCategory(dto.getCategoryId()));
        return wine;
    }

    private String normalizeWineType(String rawType, boolean blankToNull) {
        String normalized = AddressTextUtils.normalizeText(rawType);
        if (normalized == null || normalized.isEmpty()) {
            return blankToNull ? null : normalized;
        }
        String asciiLower = AddressTextUtils.stripAccents(normalized).toLowerCase(java.util.Locale.ROOT);
        return switch (asciiLower) {
            case "red", "vang do" -> "Red";
            case "white", "vang trang" -> "White";
            case "rose", "vang hong" -> "Rose";
            case "sparkling", "vang sui" -> "Sparkling";
            case "traditional", "ruou truyen thong", "traditional wine" -> "Traditional";
            default -> normalized;
        };
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
    }
}





