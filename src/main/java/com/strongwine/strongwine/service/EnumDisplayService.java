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
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ xử lý";
            case PAID -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
        };
    }

    public String paymentStatus(PaymentStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ thanh toán";
            case SUCCESS -> "Thành công";
            case FAILED -> "Thất bại";
            case CANCELLED -> "Đã hủy";
        };
    }

    public String shipmentStatus(ShipmentStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING_ASSIGNMENT -> "Chờ phân công";
            case ASSIGNED -> "Đã phân công";
            case PICKED_UP -> "Đã lấy hàng";
            case DELIVERING -> "Đang giao";
            case COMPLETED -> "Hoàn tất";
            case FAILED -> "Thất bại";
        };
    }

    public String shipperStatus(ShipperStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case ACTIVE -> "Đang hoạt động";
            case INACTIVE -> "Tạm ngưng";
            case BUSY -> "Đang bận";
            case SUSPENDED -> "Tạm khóa";
        };
    }

    public String paymentMethod(PaymentMethod method) {
        if (method == null) {
            return "Không xác định";
        }
        return switch (method) {
            case STRIPE -> "Stripe";
            case PAYPAL -> "PayPal";
            case VNPAY -> "VNPay";
            case MOMO -> "MoMo";
            case COD -> "Thanh toán khi nhận hàng";
        };
    }

    public String wineType(String type) {
        if (type == null || type.isBlank()) {
            return "Không xác định";
        }
        return switch (type.trim().toUpperCase()) {
            case "RED" -> "Vang đỏ";
            case "WHITE" -> "Vang trắng";
            case "ROSE" -> "Vang hồng";
            case "SPARKLING" -> "Vang sủi";
            default -> type;
        };
    }

    public String userRole(String role) {
        if (role == null || role.isBlank()) {
            return "Không xác định";
        }
        return switch (role.trim().toUpperCase()) {
            case "ADMIN" -> "Quản trị viên";
            case "USER" -> "Khách hàng";
            case "SHIPPER" -> "Nhân viên giao hàng";
            default -> role;
        };
    }

    public String inventoryOperation(InventoryOperationType operationType) {
        if (operationType == null) {
            return "Không xác định";
        }
        return switch (operationType) {
            case IMPORT -> "Nhập kho";
            case EXPORT -> "Xuất kho";
            case ORDER -> "Giữ hàng theo đơn";
            case CANCEL -> "Hoàn kho do hủy đơn";
            case ADJUSTMENT -> "Điều chỉnh kho";
        };
    }

    public String inventoryReferenceType(String referenceType) {
        if (referenceType == null || referenceType.isBlank()) {
            return "Không xác định";
        }
        return switch (referenceType.trim().toUpperCase()) {
            case "MANUAL_IMPORT" -> "Nhập kho thủ công";
            case "MANUAL_EXPORT" -> "Xuất kho thủ công";
            case "MANUAL_ADJUST" -> "Điều chỉnh kho thủ công";
            case "ORDER" -> "Đơn hàng";
            case "PAYMENT" -> "Thanh toán";
            case "ORDER_CANCEL" -> "Hủy đơn";
            default -> referenceType;
        };
    }

    public String paymentTransactionType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            return "Không xác định";
        }
        return switch (transactionType.trim().toUpperCase()) {
            case "SESSION_CREATED" -> "Tạo phiên";
            case "STRIPE_SESSION" -> "Phiên Stripe";
            case "PAYMENT_SESSION_CREATED" -> "Tạo phiên thanh toán";
            case "STRIPE_REDIRECT" -> "Chuyển hướng Stripe";
            case "PAYMENT_RETURN" -> "Người dùng quay lại";
            case "STRIPE_WEBHOOK_EVENT" -> "Sự kiện webhook Stripe";
            case "STRIPE_WEBHOOK_PROCESS" -> "Xử lý webhook Stripe";
            default -> transactionType;
        };
    }

    public String paymentTransactionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Không xác định";
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> "Đang chờ";
            case "REDIRECT" -> "Đã chuyển hướng";
            case "RECEIVED" -> "Đã nhận";
            case "SUCCESS" -> "Thành công";
            case "FAILED" -> "Thất bại";
            case "CANCELLED" -> "Đã hủy";
            case "SKIPPED" -> "Bỏ qua";
            default -> status;
        };
    }
}
