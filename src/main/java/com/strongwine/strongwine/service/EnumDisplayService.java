package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.InventoryOperationType;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.OtpDeliveryStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.ReturnStatus;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.util.AddressTextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class EnumDisplayService {

    @Autowired(required = false)
    private VietnamGeoValidationService geoValidationService;

    public String orderStatus(OrderStatus status) {
        return orderStatus(status, VIETNAMESE);
    }

    public String orderStatus(OrderStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case PENDING -> en ? "Pending" : "Chờ xử lý";
            case PAID -> en ? "Paid" : "Đã thanh toán";
            case CANCELLED -> en ? "Cancelled" : "Đã hủy";
            case RETURN_REQUESTED -> en ? "Return Requested" : "Yêu cầu hoàn trả";
            case RETURNED -> en ? "Returned" : "Đã hoàn trả";
        };
    }

    public String orderStatusTone(OrderStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case PENDING, RETURN_REQUESTED -> "warn";
            case PAID -> "ok";
            case CANCELLED -> "bad";
            case RETURNED -> "neutral";
        };
    }

    public String orderStatusBadgeClass(OrderStatus status) {
        return "status " + orderStatusTone(status);
    }

    public DisplayBadge orderStatusBadge(OrderStatus status) {
        return new DisplayBadge(orderStatus(status), orderStatusTone(status), orderStatusBadgeClass(status));
    }

    public String paymentStatus(PaymentStatus status) {
        return paymentStatus(status, VIETNAMESE);
    }

    public String paymentStatus(PaymentStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case PENDING -> en ? "Pending Payment" : "Chờ thanh toán";
            case SUCCESS -> en ? "Succeeded" : "Thành công";
            case FAILED -> en ? "Failed" : "Thất bại";
            case CANCELLED -> en ? "Cancelled" : "Đã hủy";
            case REFUND_REQUIRED -> en ? "Refund Required" : "Chờ hoàn tiền";
            case REFUNDED -> en ? "Refunded" : "Đã hoàn tiền";
        };
    }

    public String paymentStatusTone(PaymentStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case PENDING, REFUND_REQUIRED -> "warn";
            case SUCCESS -> "ok";
            case FAILED, CANCELLED -> "bad";
            case REFUNDED -> "info";
        };
    }

    public String paymentStatusBadgeClass(PaymentStatus status) {
        return "status " + paymentStatusTone(status);
    }

    public DisplayBadge paymentStatusBadge(PaymentStatus status) {
        return new DisplayBadge(paymentStatus(status), paymentStatusTone(status), paymentStatusBadgeClass(status));
    }

    public String shipmentStatus(ShipmentStatus status) {
        return shipmentStatus(status, VIETNAMESE);
    }

    public String shipmentStatus(ShipmentStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case PENDING_ASSIGNMENT -> en ? "Pending Assignment" : "Chờ phân công";
            case ASSIGNED -> en ? "Assigned" : "Đã phân công";
            case PICKED_UP -> en ? "Picked Up" : "Đã lấy hàng";
            case DELIVERING -> en ? "Delivering" : "Đang giao";
            case COMPLETED -> en ? "Completed" : "Hoàn tất";
            case FAILED -> en ? "Failed" : "Thất bại";
            case CANCELLED -> en ? "Cancelled" : "Đã hủy";
        };
    }

    public String shipmentStatusTone(ShipmentStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case PENDING_ASSIGNMENT -> "warn";
            case ASSIGNED, PICKED_UP, DELIVERING -> "info";
            case COMPLETED -> "ok";
            case FAILED, CANCELLED -> "bad";
        };
    }

    public String shipmentStatusBadgeClass(ShipmentStatus status) {
        return "status " + shipmentStatusTone(status);
    }

    public DisplayBadge shipmentStatusBadge(ShipmentStatus status) {
        return new DisplayBadge(shipmentStatus(status), shipmentStatusTone(status), shipmentStatusBadgeClass(status));
    }

    public String voucherType(VoucherType type) {
        return voucherType(type, VIETNAMESE);
    }

    public String voucherType(VoucherType type, Locale locale) {
        boolean en = isEnglish(locale);
        if (type == null) {
            return unknownLabel(en);
        }
        return switch (type) {
            case PERCENT -> en ? "Percentage Discount" : "Giảm theo phần trăm";
            case FIXED -> en ? "Fixed Discount" : "Giảm số tiền cố định";
            case FREESHIP -> en ? "Free Shipping" : "Miễn phí vận chuyển";
        };
    }

    public String voucherTypeTone(VoucherType type) {
        if (type == null) {
            return "neutral";
        }
        return switch (type) {
            case PERCENT -> "wine";
            case FIXED -> "gold";
            case FREESHIP -> "ok";
        };
    }

    public String voucherTypeBadgeClass(VoucherType type) {
        return "badge badge-" + voucherTypeTone(type);
    }

    public DisplayBadge voucherTypeBadge(VoucherType type) {
        return new DisplayBadge(voucherType(type), voucherTypeTone(type), voucherTypeBadgeClass(type));
    }

    public String returnStatus(ReturnStatus status) {
        return returnStatus(status, VIETNAMESE);
    }

    public String returnStatus(ReturnStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case REQUESTED -> en ? "Requested" : "Yêu cầu hoàn trả";
            case APPROVED -> en ? "Approved" : "Đã duyệt hoàn trả";
            case REJECTED -> en ? "Rejected" : "Từ chối hoàn trả";
            case REFUNDED -> en ? "Refunded" : "Đã hoàn tiền";
        };
    }

    public String returnStatusTone(ReturnStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case REQUESTED -> "warn";
            case APPROVED -> "info";
            case REJECTED -> "bad";
            case REFUNDED -> "ok";
        };
    }

    public String returnStatusBadgeClass(ReturnStatus status) {
        return "status " + returnStatusTone(status);
    }

    public DisplayBadge returnStatusBadge(ReturnStatus status) {
        return new DisplayBadge(returnStatus(status), returnStatusTone(status), returnStatusBadgeClass(status));
    }

    public String codBatchStatus(CodBatchStatus status) {
        return codBatchStatus(status, VIETNAMESE);
    }

    public String codBatchStatus(CodBatchStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case PENDING_SETTLEMENT -> en ? "Pending Settlement" : "Chờ đối soát";
            case VERIFIED -> en ? "Verified" : "Đã đối soát";
            case DISPUTED -> en ? "Disputed" : "Sai lệch đối soát";
        };
    }

    public String codBatchStatusTone(CodBatchStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case PENDING_SETTLEMENT -> "warn";
            case VERIFIED -> "ok";
            case DISPUTED -> "bad";
        };
    }

    public String codBatchStatusBadgeClass(CodBatchStatus status) {
        return "status " + codBatchStatusTone(status);
    }

    public DisplayBadge codBatchStatusBadge(CodBatchStatus status) {
        return new DisplayBadge(codBatchStatus(status), codBatchStatusTone(status), codBatchStatusBadgeClass(status));
    }

    public String otpDeliveryStatus(OtpDeliveryStatus status) {
        return otpDeliveryStatus(status, VIETNAMESE);
    }

    public String otpDeliveryStatus(OtpDeliveryStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case PENDING -> en ? "Pending OTP" : "Chờ gửi OTP";
            case SENT -> en ? "OTP Sent" : "Đã gửi OTP";
            case FAILED -> en ? "OTP Failed" : "Gửi OTP thất bại";
        };
    }

    public String otpDeliveryStatusTone(OtpDeliveryStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case PENDING -> "warn";
            case SENT -> "ok";
            case FAILED -> "bad";
        };
    }

    public String otpDeliveryStatusBadgeClass(OtpDeliveryStatus status) {
        return "status " + otpDeliveryStatusTone(status);
    }

    public DisplayBadge otpDeliveryStatusBadge(OtpDeliveryStatus status) {
        return new DisplayBadge(otpDeliveryStatus(status), otpDeliveryStatusTone(status), otpDeliveryStatusBadgeClass(status));
    }

    /**
     * Generates a valid, robust Google Maps navigation URL.
     * Strips legacy [GPS: ...] tags and ensures driving navigation opens cleanly.
     */
    public String googleMapsUrl(Double lat, Double lng, String rawAddress) {
        // 1. If valid coordinates are provided, prioritize direct GPS coordinates
        if (AddressTextUtils.isValidCoordinatePair(lat, lng)
                && (Math.abs(lat) > 0.0001 || Math.abs(lng) > 0.0001)) {
            return String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", lat, lng);
        }

        // 2. Check if address contains legacy GPS format [GPS: lat, lng]
        if (rawAddress != null && !rawAddress.isBlank()) {
            Optional<AddressTextUtils.Coordinates> coords =
                    AddressTextUtils.extractLegacyCoordinates(rawAddress);
            if (coords.isPresent()) {
                return String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving",
                        coords.get().latitude(), coords.get().longitude());
            }

            // 3. Clean address by stripping [GPS: ...] suffix
            String cleanAddress = AddressTextUtils.stripLegacyGpsSuffix(rawAddress);
            if (cleanAddress != null && !cleanAddress.trim().isEmpty() && !cleanAddress.trim().equals(".")) {
                if (geoValidationService != null) {
                    double[] resolved = geoValidationService.resolveDefaultCoordinates(cleanAddress);
                    if (resolved != null && resolved.length == 2 && Math.abs(resolved[0]) > 0.0001) {
                        return String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", resolved[0], resolved[1]);
                    }
                }
                String encoded = URLEncoder.encode(cleanAddress.trim(), StandardCharsets.UTF_8).replace("+", "%20");
                return "https://www.google.com/maps/dir/?api=1&destination=" + encoded + "&travelmode=driving";
            }
        }

        // Default to Ho Chi Minh City center
        return "https://www.google.com/maps/dir/?api=1&destination=10.776900,106.700900&travelmode=driving";
    }

    public String shipperStatus(ShipperStatus status) {
        return shipperStatus(status, VIETNAMESE);
    }

    public String shipperStatus(ShipperStatus status, Locale locale) {
        boolean en = isEnglish(locale);
        if (status == null) {
            return unknownLabel(en);
        }
        return switch (status) {
            case ACTIVE -> en ? "Active" : "Đang hoạt động";
            case INACTIVE -> en ? "Inactive" : "Tạm ngưng";
            case BUSY -> en ? "Busy" : "Đang bận";
            case SUSPENDED -> en ? "Suspended" : "Tạm khóa";
        };
    }

    public String shipperStatusTone(ShipperStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case ACTIVE -> "ok";
            case INACTIVE -> "neutral";
            case BUSY -> "warn";
            case SUSPENDED -> "bad";
        };
    }

    public String shipperStatusBadgeClass(ShipperStatus status) {
        return "status " + shipperStatusTone(status);
    }

    public DisplayBadge shipperStatusBadge(ShipperStatus status) {
        return new DisplayBadge(shipperStatus(status), shipperStatusTone(status), shipperStatusBadgeClass(status));
    }

    public String paymentMethod(PaymentMethod method) {
        return paymentMethod(method, VIETNAMESE);
    }

    public String paymentMethod(PaymentMethod method, Locale locale) {
        boolean en = isEnglish(locale);
        if (method == null) {
            return unknownLabel(en);
        }
        return switch (method) {
            case STRIPE -> en ? "Stripe International Card (Visa, Mastercard)" : "Thẻ quốc tế Stripe (Visa, Mastercard)";
            case PAYPAL -> "PayPal";
            case VNPAY -> en ? "VNPay Payment Gateway (QR, ATM, Visa)" : "Cổng thanh toán VNPay (VNPAY-QR, Thẻ ATM, Visa)";
            case MOMO -> en ? "MoMo E-Wallet" : "Ví điện tử MoMo";
            case COD -> en ? "Cash on Delivery (COD)" : "Thanh toán khi nhận hàng (COD)";
        };
    }

    public String paymentMethodTone(PaymentMethod method) {
        if (method == null) {
            return "neutral";
        }
        return switch (method) {
            case STRIPE, PAYPAL, VNPAY -> "info";
            case MOMO -> "wine";
            case COD -> "gold";
        };
    }

    public String paymentMethodBadgeClass(PaymentMethod method) {
        return "badge badge-" + paymentMethodTone(method);
    }

    public DisplayBadge paymentMethodBadge(PaymentMethod method) {
        return new DisplayBadge(paymentMethod(method), paymentMethodTone(method), paymentMethodBadgeClass(method));
    }

    public static final int LOW_STOCK_THRESHOLD = 10;

    public record DisplayBadge(String label, String tone, String cssClass) {
    }

    public String wineType(String type) {
        return wineType(type, VIETNAMESE);
    }

    public String wineType(String type, Locale locale) {
        boolean en = isEnglish(locale);
        String normalized = AddressTextUtils.normalizeText(type);
        if (normalized == null || normalized.isEmpty()) {
            return unknownLabel(en);
        }
        String asciiLower = AddressTextUtils.stripAccents(normalized).toLowerCase(Locale.ROOT);
        return switch (asciiLower) {
            case "red", "vang do" -> en ? "Red Wine" : "Vang đỏ";
            case "white", "vang trang" -> en ? "White Wine" : "Vang trắng";
            case "rose", "vang hong" -> en ? "Rosé Wine" : "Vang hồng";
            case "sparkling", "vang sui" -> en ? "Sparkling Wine" : "Vang sủi";
            case "traditional", "ruou truyen thong", "traditional wine" -> en ? "Traditional Wine" : "Rượu Truyền Thống";
            default -> normalized;
        };
    }

    public String wineTypeTone(String type) {
        String normalized = AddressTextUtils.normalizeText(type);
        if (normalized == null || normalized.isEmpty()) {
            return "neutral";
        }
        String asciiLower = AddressTextUtils.stripAccents(normalized).toLowerCase(Locale.ROOT);
        return switch (asciiLower) {
            case "red", "vang do", "traditional", "ruou truyen thong", "traditional wine" -> "wine";
            case "white", "vang trang" -> "gold";
            case "rose", "vang hong" -> "warn";
            case "sparkling", "vang sui" -> "info";
            default -> "neutral";
        };
    }

    public String wineTypeBadgeClass(String type) {
        return "badge badge-" + wineTypeTone(type);
    }

    public DisplayBadge wineTypeBadge(String type) {
        return new DisplayBadge(wineType(type), wineTypeTone(type), wineTypeBadgeClass(type));
    }

    public String stockStatusLabel(int availableStock) {
        return stockStatusLabel(availableStock, VIETNAMESE);
    }

    public String stockStatusLabel(int availableStock, Locale locale) {
        boolean en = isEnglish(locale);
        if (availableStock <= 0) {
            return en ? "Out of Stock" : "Hết hàng";
        }
        if (availableStock <= LOW_STOCK_THRESHOLD) {
            return en ? "Low Stock" : "Sắp hết hàng";
        }
        return en ? "In Stock" : "Còn hàng";
    }

    public String stockStatusTone(int availableStock) {
        if (availableStock <= 0) {
            return "bad";
        }
        if (availableStock <= LOW_STOCK_THRESHOLD) {
            return "warn";
        }
        return "ok";
    }

    public String stockStatusBadgeClass(int availableStock) {
        return "status " + stockStatusTone(availableStock);
    }

    public DisplayBadge stockStatusBadge(int availableStock) {
        return new DisplayBadge(stockStatusLabel(availableStock), stockStatusTone(availableStock), stockStatusBadgeClass(availableStock));
    }

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private static boolean isEnglish(Locale locale) {
        return locale != null && Locale.ENGLISH.getLanguage().equalsIgnoreCase(locale.getLanguage());
    }

    private static String unknownLabel(boolean en) {
        return en ? "Unknown" : "Không xác định";
    }

    public String userRole(String role) {
        return userRole(role, VIETNAMESE);
    }

    public String userRole(String role, Locale locale) {
        boolean en = isEnglish(locale);
        if (role == null || role.isBlank()) {
            return unknownLabel(en);
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> en ? "Administrator" : "Quản trị viên";
            case "USER" -> en ? "Customer" : "Khách hàng";
            case "SHIPPER" -> en ? "Delivery Shipper" : "Nhân viên giao hàng";
            default -> role;
        };
    }

    public String userRoleTone(String role) {
        if (role == null || role.isBlank()) {
            return "neutral";
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> "wine";
            case "SHIPPER" -> "info";
            default -> "neutral";
        };
    }

    public String inventoryOperation(InventoryOperationType operationType) {
        return inventoryOperation(operationType, VIETNAMESE);
    }

    public String inventoryOperation(InventoryOperationType operationType, Locale locale) {
        boolean en = isEnglish(locale);
        if (operationType == null) {
            return unknownLabel(en);
        }
        return switch (operationType) {
            case IMPORT -> en ? "Stock Import" : "Nhập kho";
            case EXPORT -> en ? "Stock Export" : "Xuất kho";
            case ORDER -> en ? "Order Reservation" : "Giữ hàng theo đơn";
            case CANCEL -> en ? "Cancelled Order Release" : "Hoàn kho do hủy đơn";
            case ADJUSTMENT -> en ? "Inventory Adjustment" : "Điều chỉnh kho";
        };
    }

    public String inventoryOperationTone(InventoryOperationType operationType) {
        if (operationType == null) {
            return "neutral";
        }
        return switch (operationType) {
            case IMPORT -> "ok";
            case EXPORT -> "warn";
            case ORDER -> "info";
            case CANCEL -> "neutral";
            case ADJUSTMENT -> "gold";
        };
    }

    public String inventoryOperationBadgeClass(InventoryOperationType operationType) {
        return "status " + inventoryOperationTone(operationType);
    }

    public DisplayBadge inventoryOperationBadge(InventoryOperationType operationType) {
        return new DisplayBadge(inventoryOperation(operationType), inventoryOperationTone(operationType), inventoryOperationBadgeClass(operationType));
    }

    public String inventoryReferenceType(String referenceType) {
        if (referenceType == null || referenceType.isBlank()) {
            return "Không xác định";
        }
        return switch (referenceType.trim().toUpperCase(Locale.ROOT)) {
            case "MANUAL_IMPORT" -> "Nhập kho thủ công";
            case "MANUAL_EXPORT" -> "Xuất kho thủ công";
            case "EXCEL_IMPORT" -> "Nhập dữ liệu từ Excel";
            case "ORDER" -> "Đơn hàng";
            case "PAYMENT" -> "Thanh toán";
            case "ORDER_CANCEL" -> "Hủy đơn";
            case "RETURN_RESTOCK" -> "Nhập kho do hoàn trả";
            case "WAREHOUSE_TRANSFER" -> "Điều chuyển kho";
            default -> referenceType;
        };
    }

    public String paymentTransactionType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            return "Không xác định";
        }
        return switch (transactionType.trim().toUpperCase(Locale.ROOT)) {
            case "SESSION_CREATED" -> "Tạo phiên";
            case "STRIPE_SESSION" -> "Phiên Stripe";
            case "PAYMENT_SESSION_CREATED" -> "Tạo phiên thanh toán";
            case "STRIPE_REDIRECT" -> "Chuyển hướng Stripe";
            case "PAYMENT_RETURN" -> "Người dùng quay lại";
            case "STRIPE_WEBHOOK_EVENT" -> "Sự kiện webhook Stripe";
            case "STRIPE_WEBHOOK_PROCESS" -> "Xử lý webhook Stripe";
            case "VNPAY_CREATE" -> "Khởi tạo giao dịch VNPay";
            case "VNPAY_IPN" -> "Thông báo IPN VNPay";
            case "VNPAY_RETURN" -> "Quay lại từ VNPay";
            case "MOMO_CREATE" -> "Khởi tạo ví MoMo";
            case "MOMO_IPN" -> "Thông báo IPN MoMo";
            case "MOMO_RETURN" -> "Quay lại từ MoMo";
            case "COD_COLLECTED" -> "Thu hộ COD thành công";
            case "COD_SETTLEMENT" -> "Đối soát nộp tiền COD";
            case "REFUND_REQUESTED" -> "Yêu cầu hoàn tiền";
            case "REFUND_COMPLETED" -> "Hoàn tiền thành công";
            default -> transactionType;
        };
    }

    public String paymentTransactionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Không xác định";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Đang chờ";
            case "REDIRECT" -> "Đã chuyển hướng";
            case "RECEIVED" -> "Đã nhận";
            case "SUCCESS" -> "Thành công";
            case "FAILED" -> "Thất bại";
            case "CANCELLED" -> "Đã hủy";
            case "SKIPPED" -> "Bỏ qua";
            case "REFUND_REQUIRED" -> "Chờ hoàn tiền";
            case "REFUNDED" -> "Đã hoàn tiền";
            default -> status;
        };
    }

    public String paymentTransactionStatusTone(String status) {
        if (status == null || status.isBlank()) {
            return "neutral";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> "ok";
            case "PENDING", "REDIRECT", "RECEIVED", "REFUND_REQUIRED" -> "warn";
            case "FAILED", "CANCELLED" -> "bad";
            case "REFUNDED" -> "info";
            default -> "neutral";
        };
    }

    public String paymentTransactionStatusBadgeClass(String status) {
        return "status " + paymentTransactionStatusTone(status);
    }
}
