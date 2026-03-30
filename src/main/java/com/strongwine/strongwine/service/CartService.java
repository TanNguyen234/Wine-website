package com.strongwine.strongwine.service;

import com.strongwine.strongwine.dto.CartDto;
import com.strongwine.strongwine.dto.CartItemDto;
import com.strongwine.strongwine.dto.CartItemRequest;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    @Autowired
    private WineRepository wineRepository;

    @Autowired
    private InventoryService inventoryService;


    @Transactional(readOnly = true)
    public CartDto validateCheckoutCart(List<CartItemRequest> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }

        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (CartItemRequest cartItem : cartItems) {
            if (cartItem.getProductId() == null || cartItem.getProductId() <= 0) {
                throw new IllegalArgumentException("Sản phẩm không hợp lệ trong giỏ hàng");
            }
            if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm không hợp lệ");
            }
            requested.merge(cartItem.getProductId(), cartItem.getQuantity(), Integer::sum);
        }

        List<Long> wineIds = new ArrayList<>(requested.keySet());
        List<Wine> wines = wineRepository.findAllById(wineIds);
        Map<Long, Wine> wineById = new HashMap<>();
        for (Wine wine : wines) {
            wineById.put(wine.getId(), wine);
        }

        Map<Long, Integer> stockByWineId = inventoryService.getAvailableStockByWineIds(wineIds);
        CartDto cartDto = new CartDto();
        List<CartItemDto> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            Long wineId = entry.getKey();
            Integer quantity = entry.getValue();

            Wine wine = wineById.get(wineId);
            if (wine == null || wine.isDeleted()) {
                throw new IllegalStateException("Sản phẩm không còn khả dụng");
            }

            int available = stockByWineId.getOrDefault(wineId, 0);
            if (available <= 0) {
                throw new IllegalStateException("Sản phẩm " + wine.getName() + " đã hết hàng");
            }
            if (quantity > available) {
                throw new IllegalStateException("Số lượng " + wine.getName() + " vượt quá tồn kho");
            }

            CartItemDto itemDto = new CartItemDto();
            itemDto.setWine(wine);
            itemDto.setQuantity(quantity);
            itemDto.setAvailableStock(available);

            BigDecimal lineTotal = wine.getPrice().multiply(BigDecimal.valueOf(quantity));
            itemDto.setLineTotal(lineTotal);
            total = total.add(lineTotal);
            items.add(itemDto);
        }

        cartDto.setItems(items);
        cartDto.setSubtotal(total);
        cartDto.setTotal(total);
        cartDto.setEmpty(items.isEmpty());
        return cartDto;
    }

    public Map<Long, Integer> toItemMap(CartDto cartDto) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (CartItemDto item : cartDto.getItems()) {
            if (item.getWine() != null && item.getWine().getId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                result.put(item.getWine().getId(), item.getQuantity());
            }
        }
        return result;
    }
}
