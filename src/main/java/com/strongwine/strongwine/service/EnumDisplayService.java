package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.InventoryOperationType;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.ShipperStatus;
import org.springframework.stereotype.Service;

@Service
public class EnumDisplayService {

    public String orderStatus(OrderStatus status) {
        if (status == null) {
            return "Khong xac dinh";
        }
        return switch (status) {
            case PENDING -> "Cho xu ly";
            case PAID -> "Da thanh toan";
            case CANCELLED -> "Da huy";
        };
    }

    public String paymentStatus(PaymentStatus status) {
        if (status == null) {
            return "Khong xac dinh";
        }
        return switch (status) {
            case PENDING -> "Cho thanh toan";
            case SUCCESS -> "Thanh cong";
            case FAILED -> "That bai";
            case CANCELLED -> "Da huy";
        };
    }

    public String shipmentStatus(ShipmentStatus status) {
        if (status == null) {
            return "Khong xac dinh";
        }
        return switch (status) {
            case PENDING_ASSIGNMENT -> "Cho phan cong";
            case ASSIGNED -> "Da phan cong";
            case PICKED_UP -> "Da lay hang";
            case DELIVERING -> "Dang giao";
            case COMPLETED -> "Hoan tat";
            case FAILED -> "That bai";
        };
    }

    public String shipperStatus(ShipperStatus status) {
        if (status == null) {
            return "Khong xac dinh";
        }
        return switch (status) {
            case ACTIVE -> "Dang hoat dong";
            case INACTIVE -> "Tam ngung";
            case BUSY -> "Dang ban";
            case SUSPENDED -> "Tam khoa";
        };
    }

    public String paymentMethod(PaymentMethod method) {
        if (method == null) {
            return "Khong xac dinh";
        }
        return switch (method) {
            case STRIPE -> "Stripe";
            case PAYPAL -> "PayPal";
            case VNPAY -> "VNPay";
            case MOMO -> "MoMo";
            case COD -> "Thanh toan khi nhan hang";
        };
    }

    public String wineType(String type) {
        if (type == null || type.isBlank()) {
            return "Khong xac dinh";
        }
        return switch (type.trim().toUpperCase()) {
            case "RED" -> "Vang do";
            case "WHITE" -> "Vang trang";
            case "ROSE" -> "Vang hong";
            case "SPARKLING" -> "Vang sui";
            default -> type;
        };
    }

    public String userRole(String role) {
        if (role == null || role.isBlank()) {
            return "Khong xac dinh";
        }
        return switch (role.trim().toUpperCase()) {
            case "ADMIN" -> "Quan tri vien";
            case "USER" -> "Khach hang";
            case "SHIPPER" -> "Nhan vien giao hang";
            default -> role;
        };
    }

    public String inventoryOperation(InventoryOperationType operationType) {
        if (operationType == null) {
            return "Khong xac dinh";
        }
        return switch (operationType) {
            case IMPORT -> "Nhap kho";
            case EXPORT -> "Xuat kho";
            case ORDER -> "Giu hang theo don";
            case CANCEL -> "Hoan kho do huy don";
            case ADJUSTMENT -> "Dieu chinh kho";
        };
    }

    public String inventoryReferenceType(String referenceType) {
        if (referenceType == null || referenceType.isBlank()) {
            return "Khong xac dinh";
        }
        return switch (referenceType.trim().toUpperCase()) {
            case "MANUAL_IMPORT" -> "Nhap kho thu cong";
            case "MANUAL_EXPORT" -> "Xuat kho thu cong";
            case "ORDER" -> "Don hang";
            case "PAYMENT" -> "Thanh toan";
            case "ORDER_CANCEL" -> "Huy don";
            default -> referenceType;
        };
    }

    public String paymentTransactionType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            return "Khong xac dinh";
        }
        return switch (transactionType.trim().toUpperCase()) {
            case "SESSION_CREATED" -> "Tao phien";
            case "STRIPE_SESSION" -> "Phien Stripe";
            case "PAYMENT_SESSION_CREATED" -> "Tao phien thanh toan";
            case "STRIPE_REDIRECT" -> "Chuyen huong Stripe";
            case "PAYMENT_RETURN" -> "Nguoi dung quay lai";
            case "STRIPE_WEBHOOK_EVENT" -> "Su kien webhook Stripe";
            case "STRIPE_WEBHOOK_PROCESS" -> "Xu ly webhook Stripe";
            default -> transactionType;
        };
    }

    public String paymentTransactionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Khong xac dinh";
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> "Dang cho";
            case "REDIRECT" -> "Da chuyen huong";
            case "RECEIVED" -> "Da nhan";
            case "SUCCESS" -> "Thanh cong";
            case "FAILED" -> "That bai";
            case "CANCELLED" -> "Da huy";
            case "SKIPPED" -> "Bo qua";
            default -> status;
        };
    }
}
