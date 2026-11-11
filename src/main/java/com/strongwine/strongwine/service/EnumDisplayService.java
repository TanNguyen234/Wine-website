package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.InventoryOperationType;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.ShipperStatus;
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

    /**
     * Generates a valid, robust Google Maps navigation URL.
     * Strips legacy [GPS: ...] tags and ensures driving navigation opens cleanly.
     */
    public String googleMapsUrl(Double lat, Double lng, String rawAddress) {
        // 1. If valid coordinates are provided, prioritize direct GPS coordinates
        if (lat != null && lng != null && Math.abs(lat) > 0.0001 && Math.abs(lng) > 0.0001) {
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
            case STRIPE -> "Thẻ quốc tế Stripe (Visa, Mastercard)";
            case PAYPAL -> "PayPal";
            case VNPAY -> "Cổng thanh toán VNPay (VNPAY-QR, Thẻ ATM, Visa)";
            case MOMO -> "Ví điện tử MoMo";
            case COD -> "Thanh toán khi nhận hàng (COD)";
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
