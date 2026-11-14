package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.CategoryDto;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryApiController {

    @Autowired
    private CategoryService categoryService;

    @Autowired(required = false)
    private com.strongwine.strongwine.service.AdminAuditLogService adminAuditLogService;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories().stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toDto(categoryService.getCategoryByIdOrThrow(id)));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        Category created = categoryService.createCategory(category);
        CategoryDto result = toDto(created);
        logAudit("CATEGORY_CREATED", created.getId(), null, result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDto dto) {
        try {
            Category category = new Category();
            category.setName(dto.getName());
            category.setDescription(dto.getDescription());
            Category updated = categoryService.updateCategory(id, category);
            CategoryDto result = toDto(updated);
            logAudit("CATEGORY_UPDATED", id, null, result);
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            logAudit("CATEGORY_DELETED", id, null, null);
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
        adminAuditLogService.log(actor, action, "Category", targetId, before, after, null);
    }

    private CategoryDto toDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        return dto;
    }
}
