package com.strongwine.strongwine.controller.api;

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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductApiController {

    @Autowired
    private WineService wineService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired(required = false)
    private com.strongwine.strongwine.service.AdminAuditLogService adminAuditLogService;

    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<Wine> wines = wineService.getAllWines();
        Map<Long, Integer> stockMap = inventoryService.getAvailableStockByWineIds(wines.stream().map(Wine::getId).toList());
        return ResponseEntity.ok(wines.stream().map(w -> toDto(w, stockMap)).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        return wineService.getWineById(id)
                .map(wine -> {
                    int available = inventoryService.getAvailableStockByWineIds(List.of(wine.getId())).getOrDefault(wine.getId(), 0);
                    return ResponseEntity.ok(toDto(wine, available));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductDto dto) {
        Wine wine = toEntity(dto);
        Wine saved = wineService.createWine(wine);
        int available = inventoryService.getAvailableStockByWineIds(List.of(saved.getId())).getOrDefault(saved.getId(), 0);
        ProductDto result = toDto(saved, available);
        logAudit("PRODUCT_CREATED", saved.getId(), null, result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDto dto) {
        try {
            Wine current = wineService.getWineByIdOrThrow(id);
            ProductDto before = toDto(current, 0);
            current.setName(dto.getName());
            current.setType(dto.getType());
            current.setYear(dto.getYear());
            current.setPrice(dto.getPrice());
            current.setDescription(dto.getDescription());
            current.setCountry(dto.getCountry());
            current.setImageUrl(dto.getImageUrl());
            current.setCategory(resolveCategory(dto.getCategoryId()));
            Wine updated = wineService.updateWine(id, current);
            int available = inventoryService.getAvailableStockByWineIds(List.of(updated.getId())).getOrDefault(updated.getId(), 0);
            ProductDto result = toDto(updated, available);
            logAudit("PRODUCT_UPDATED", id, before, result);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try {
            wineService.deleteWine(id);
            logAudit("PRODUCT_DELETED", id, null, null);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private void logAudit(String action, Long targetId, Object before, Object after) {
        if (adminAuditLogService == null) {
            return;
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null && auth.getName() != null ? auth.getName() : "ADMIN";
        adminAuditLogService.log(actor, action, "Wine", targetId, before, after, null);
    }

    private Wine toEntity(ProductDto dto) {
        Wine wine = new Wine();
        wine.setName(dto.getName());
        wine.setType(dto.getType());
        wine.setYear(dto.getYear());
        wine.setPrice(dto.getPrice());
        wine.setDescription(dto.getDescription());
        wine.setCountry(dto.getCountry());
        wine.setImageUrl(dto.getImageUrl());
        wine.setCategory(resolveCategory(dto.getCategoryId()));
        return wine;
    }

    private ProductDto toDto(Wine wine, Map<Long, Integer> stockMap) {
        return toDto(wine, stockMap.getOrDefault(wine.getId(), 0));
    }

    private ProductDto toDto(Wine wine, int availableStock) {
        ProductDto dto = new ProductDto();
        dto.setId(wine.getId());
        dto.setName(wine.getName());
        dto.setType(wine.getType());
        dto.setYear(wine.getYear());
        dto.setPrice(wine.getPrice());
        dto.setFinalPrice(wine.getPrice());
        dto.setDescription(wine.getDescription());
        dto.setCountry(wine.getCountry());
        dto.setImageUrl(wine.getImageUrl());
        dto.setCategoryId(wine.getCategory() != null ? wine.getCategory().getId() : null);
        dto.setAvailableStock(availableStock);
        return dto;
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
    }
}
