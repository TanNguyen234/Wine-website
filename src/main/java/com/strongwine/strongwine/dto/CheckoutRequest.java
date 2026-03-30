package com.strongwine.strongwine.dto;

import java.util.List;

public class CheckoutRequest {
    private String fullName;
    private String phone;
    private String address;
    private String note;
    private String paymentMethod;
    private String checkoutToken;
    private Double deliveryLat;
    private Double deliveryLng;
    private List<CartItemRequest> items;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCheckoutToken() {
        return checkoutToken;
    }

    public void setCheckoutToken(String checkoutToken) {
        this.checkoutToken = checkoutToken;
    }

    public Double getDeliveryLat() {
        return deliveryLat;
    }

    public void setDeliveryLat(Double deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    public Double getDeliveryLng() {
        return deliveryLng;
    }

    public void setDeliveryLng(Double deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    public List<CartItemRequest> getItems() {
        return items;
    }

    public void setItems(List<CartItemRequest> items) {
        this.items = items;
    }
}
