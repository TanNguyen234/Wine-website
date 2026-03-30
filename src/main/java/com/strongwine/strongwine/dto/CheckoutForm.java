package com.strongwine.strongwine.dto;

/**
 * DTO for checkout form data
 * This data is NOT persisted to database - only used for validation and display
 */
public class CheckoutForm {
    
    private String fullName;
    private String phone;
    private String address;
    private String note;
    private String paymentMethod;
    
    public CheckoutForm() {
    }
    
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
}


