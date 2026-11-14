package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.OrderDto;
import com.strongwine.strongwine.dto.OrderItemDto;
import com.strongwine.strongwine.dto.ProductDto;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(inventoryService.getAvailableStockByWineIds(List.of(12L))).thenReturn(Map.of(12L, 0));

        ResponseEntity<ProductDto> response = controller.createWine(product(4L));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4L, response.getBody().getCategoryId());
        assertEquals("QA category", response.getBody().getCategoryName());
        assertEquals("Vang đỏ", response.getBody().getTypeLabel());
        assertEquals("wine", response.getBody().getTypeTone());
        assertEquals("OUT_OF_STOCK", response.getBody().getStockStatus());
        assertEquals("Hết hàng", response.getBody().getStockBadgeLabel());
        assertEquals("bad", response.getBody().getStockBadgeTone());
        assertFalse(Boolean.TRUE.equals(response.getBody().getInStock()));
    }

    @Test
    void updateWineResolvesRequestedCategory() {
        Category category = category(4L);
        when(categoryRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(category));
        when(wineService.updateWine(eq(12L), any(Wine.class))).thenAnswer(invocation -> {
            Wine wine = invocation.getArgument(1);
            wine.setId(12L);
            return wine;
        });
        when(inventoryService.getAvailableStockByWineIds(List.of(12L))).thenReturn(Map.of(12L, 0));

        ResponseEntity<ProductDto> response = controller.updateWine(12L, product(4L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4L, response.getBody().getCategoryId());
    }

    @Test
    void searchWinesNormalizesTraditionalTypeAndPopulatesMetadata() {
        Wine traditionalWine = new Wine();
        traditionalWine.setId(55L);
        traditionalWine.setName("Rượu Mơ Rừng Yên Tử Thượng Hạng");
        traditionalWine.setType("Traditional");
        traditionalWine.setYear(2019);
        traditionalWine.setPrice(new BigDecimal("650000"));
        traditionalWine.setCountry("Vietnam");
        traditionalWine.setCategory(category(9L));

        when(wineService.searchWines(null, "Traditional", null, null, null, null))
                .thenReturn(List.of(traditionalWine));
        when(inventoryService.getAvailableStockByWineIds(List.of(55L))).thenReturn(Map.of(55L, 18));

        ResponseEntity<List<ProductDto>> response = controller.searchWines(
                null, "  traditional ", null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        ProductDto dto = response.getBody().get(0);
        assertEquals("Traditional", dto.getType());
        assertEquals("Rượu Truyền Thống", dto.getTypeLabel());
        assertEquals("wine", dto.getTypeTone());
        assertEquals("IN_STOCK", dto.getStockStatus());
        assertEquals("Còn hàng", dto.getStockBadgeLabel());
        assertEquals("ok", dto.getStockBadgeTone());
        assertTrue(Boolean.TRUE.equals(dto.getInStock()));
        assertEquals("QA category", dto.getCategoryName());
    }

    @Test
    void createWineNormalizesTraditionalWineTypeInput() {
        ProductDto req = product(4L);
        req.setType("  traditional  ");

        Category category = category(4L);
        when(categoryRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(category));
        when(wineService.createWine(any(Wine.class))).thenAnswer(invocation -> {
            Wine wine = invocation.getArgument(0);
            assertEquals("Traditional", wine.getType());
            wine.setId(77L);
            return wine;
        });
        when(inventoryService.getAvailableStockByWineIds(List.of(77L))).thenReturn(Map.of(77L, 4));

        ResponseEntity<ProductDto> response = controller.createWine(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Traditional", response.getBody().getType());
        assertEquals("Rượu Truyền Thống", response.getBody().getTypeLabel());
        assertEquals("LOW_STOCK", response.getBody().getStockStatus());
        assertEquals("warn", response.getBody().getStockBadgeTone());
        assertTrue(Boolean.TRUE.equals(response.getBody().getInStock()));
    }

    @Test
    void orderDtoMapsV3FieldsAndPreservesLegacyConstructor() {
        User user = new User("buyer", "pass", "USER");
        user.setId(5L);

        Warehouse wh = new Warehouse();
        wh.setId(2L);
        wh.setName("Kho Trung Tâm Hoàn Kiếm - HN");

        Order order = new Order(user, new BigDecimal("1850000"));
        order.setId(99L);
        order.setSubtotalAmount(new BigDecimal("2000000"));
        order.setDiscountAmount(new BigDecimal("185000"));
        order.setShippingFee(new BigDecimal("35000"));
        order.setVoucherCode("VIPWINE10");
        order.setAllocatedWarehouse(wh);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.VNPAY);

        OrderDto v3Dto = OrderDto.from(order);
        assertEquals(new BigDecimal("2000000"), v3Dto.subtotalAmount());
        assertEquals(new BigDecimal("185000"), v3Dto.discountAmount());
        assertEquals(new BigDecimal("35000"), v3Dto.shippingFee());
        assertEquals("VIPWINE10", v3Dto.voucherCode());
        assertEquals("Kho Trung Tâm Hoàn Kiếm - HN", v3Dto.allocatedWarehouseName());

        // Legacy 15-arg constructor must remain compatible
        OrderDto legacyDto = new OrderDto(
                10L, 5L, LocalDateTime.now(), new BigDecimal("500000"),
                OrderStatus.PENDING, PaymentStatus.PENDING, PaymentMethod.COD,
                "Nguyễn Văn A", "0901234567", "a@b.vn", "72 Lê Thánh Tôn",
                10.77, 106.70, "Ghi chú", List.<OrderItemDto>of());
        assertEquals(new BigDecimal("500000"), legacyDto.subtotalAmount());
        assertEquals(BigDecimal.ZERO, legacyDto.discountAmount());
        assertEquals(BigDecimal.ZERO, legacyDto.shippingFee());
        assertNull(legacyDto.voucherCode());
        assertNull(legacyDto.allocatedWarehouseName());
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

