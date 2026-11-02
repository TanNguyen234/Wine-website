package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ProductDto;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.WineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WineApiControllerTest {

    @Mock
    private WineService wineService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private WineApiController controller;

    @Test
    void createWineResolvesRequestedCategory() {
        Category category = category(4L);
        when(categoryRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(category));
        when(wineService.createWine(any(Wine.class))).thenAnswer(invocation -> {
            Wine wine = invocation.getArgument(0);
            wine.setId(12L);
            return wine;
        });
        when(inventoryService.getAvailableStockByWineIds(java.util.List.of(12L))).thenReturn(Map.of(12L, 0));

        ResponseEntity<ProductDto> response = controller.createWine(product(4L));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(4L, response.getBody().getCategoryId());
    }

    @Test
    void updateWineResolvesRequestedCategory() {
        Category category = category(4L);
        when(categoryRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(category));
        when(wineService.updateWine(org.mockito.ArgumentMatchers.eq(12L), any(Wine.class))).thenAnswer(invocation -> {
            Wine wine = invocation.getArgument(1);
            wine.setId(12L);
            return wine;
        });
        when(inventoryService.getAvailableStockByWineIds(java.util.List.of(12L))).thenReturn(Map.of(12L, 0));

        ResponseEntity<ProductDto> response = controller.updateWine(12L, product(4L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4L, response.getBody().getCategoryId());
    }

    private ProductDto product(Long categoryId) {
        ProductDto dto = new ProductDto();
        dto.setName("QA API wine");
        dto.setType("Red");
        dto.setYear(2020);
        dto.setPrice(new BigDecimal("125000"));
        dto.setCategoryId(categoryId);
        return dto;
    }

    private Category category(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("QA category");
        return category;
    }
}
