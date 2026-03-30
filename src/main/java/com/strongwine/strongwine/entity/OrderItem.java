package com.strongwine.strongwine.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * OrderItem entity representing an item in an order
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wine_id", nullable = false)
    private Wine wine;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // Price at the time of order
    
    // Constructors
    public OrderItem() {
    }
    
    public OrderItem(Order order, Wine wine, Integer quantity, BigDecimal price) {
        this.order = order;
        this.wine = wine;
        this.quantity = quantity;
        this.price = price;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public Wine getWine() {
        return wine;
    }
    
    public void setWine(Wine wine) {
        this.wine = wine;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}





