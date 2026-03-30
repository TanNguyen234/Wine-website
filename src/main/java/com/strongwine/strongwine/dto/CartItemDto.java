package com.strongwine.strongwine.dto;

import java.math.BigDecimal;
import com.strongwine.strongwine.entity.Wine;

public class CartItemDto {
    private Wine wine;
    private Integer quantity;
    private Integer availableStock;
    private BigDecimal lineTotal;

    public Wine getWine() { return wine; }
    public void setWine(Wine wine) { this.wine = wine; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
