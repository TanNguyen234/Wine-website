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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EnumDisplayServiceTest {

    private EnumDisplayService enumDisplayService;
    private VietnamGeoValidationService geoValidationService;

    @BeforeEach
    void setUp() {
        enumDisplayService = new EnumDisplayService();
        geoValidationService = new VietnamGeoValidationService();
        ReflectionTestUtils.setField(enumDisplayService, "geoValidationService", geoValidationService);
    }

    @Test
    @DisplayName("googleMapsUrl should prioritize valid direct GPS coordinates")
    void testGoogleMapsUrlWithCoordinates() {
        String url = enumDisplayService.googleMapsUrl(10.7769, 106.7009, "72 Lê Thánh Tôn, Quận 1");
        assertThat(url).contains("destination=10.776900,106.700900");
        assertThat(url).contains("api=1");
        assertThat(url).contains("travelmode=driving");
    }

    @Test
    @DisplayName("googleMapsUrl should extract legacy [GPS: lat, lng] suffix cleanly")
    void testGoogleMapsUrlWithLegacyGpsSuffix() {
        String url = enumDisplayService.googleMapsUrl(null, null, "123 Hai Bà Trưng, Phường Bến Nghé, Quận 1 [GPS: 10.781200, 106.698500]");
        assertThat(url).contains("destination=10.781200,106.698500");
        assertThat(url).doesNotContain("%5BGPS");
        assertThat(url).doesNotContain("[GPS");
    }

    @Test
    @DisplayName("googleMapsUrl should resolve city fallback when coordinates are null")
    void testGoogleMapsUrlCityFallback() {
        String url = enumDisplayService.googleMapsUrl(null, null, "Hoàn Kiếm, Hà Nội");
        assertThat(url).contains("destination=21.028500,105.854200");
    }

    @Test
    @DisplayName("googleMapsUrl should ignore out-of-bounds or NaN coordinates and fall back safely")
    void testGoogleMapsUrlRejectsInvalidBounds() {
        String urlOutOfRange = enumDisplayService.googleMapsUrl(95.0, 200.0, "Hoàn Kiếm, Hà Nội");
        assertThat(urlOutOfRange).contains("destination=21.028500,105.854200");
        assertThat(urlOutOfRange).doesNotContain("95.000000");

        String urlNan = enumDisplayService.googleMapsUrl(Double.NaN, 106.7, "Quận 1, TP. Hồ Chí Minh");
        assertThat(urlNan).contains("destination=10.776900,106.700900");
    }

    @Test
    @DisplayName("Every OrderStatus constant yields non-blank VI/EN labels, semantic CSS tone, and badge metadata")
    void testAllOrderStatusesCovered() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(enumDisplayService.orderStatus(status)).as(status.name()).isNotBlank();
            assertThat(enumDisplayService.orderStatus(status, Locale.ENGLISH)).as(status.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.orderStatusTone(status)).as(status.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.orderStatusBadgeClass(status)).as(status.name() + " badgeClass").startsWith("status ");
            EnumDisplayService.DisplayBadge badge = enumDisplayService.orderStatusBadge(status);
            assertThat(badge.label()).isEqualTo(enumDisplayService.orderStatus(status));
            assertThat(badge.tone()).isEqualTo(enumDisplayService.orderStatusTone(status));
            assertThat(badge.cssClass()).isNotBlank();
        }
        assertThat(enumDisplayService.orderStatus(OrderStatus.RETURN_REQUESTED)).isEqualTo("Yêu cầu hoàn trả");
        assertThat(enumDisplayService.orderStatus(OrderStatus.RETURNED)).isEqualTo("Đã hoàn trả");
        assertThat(enumDisplayService.orderStatus(null)).isEqualTo("Không xác định");
        assertThat(enumDisplayService.orderStatusTone(null)).isEqualTo("neutral");
    }

    @Test
    @DisplayName("Every PaymentStatus constant yields non-blank VI/EN labels, semantic CSS tone, and badge metadata")
    void testAllPaymentStatusesCovered() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(enumDisplayService.paymentStatus(status)).as(status.name()).isNotBlank();
            assertThat(enumDisplayService.paymentStatus(status, Locale.ENGLISH)).as(status.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.paymentStatusTone(status)).as(status.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.paymentStatusBadgeClass(status)).as(status.name() + " badgeClass").startsWith("status ");
            EnumDisplayService.DisplayBadge badge = enumDisplayService.paymentStatusBadge(status);
            assertThat(badge.label()).isNotBlank();
            assertThat(badge.tone()).isNotBlank();
        }
        assertThat(enumDisplayService.paymentStatus(PaymentStatus.REFUND_REQUIRED)).isEqualTo("Chờ hoàn tiền");
        assertThat(enumDisplayService.paymentStatus(PaymentStatus.REFUNDED)).isEqualTo("Đã hoàn tiền");
        assertThat(enumDisplayService.paymentStatusTone(PaymentStatus.REFUND_REQUIRED)).isEqualTo("warn");
        assertThat(enumDisplayService.paymentStatus(null)).isEqualTo("Không xác định");
    }

    @Test
    @DisplayName("Every ShipmentStatus constant including CANCELLED yields non-blank labels and CSS tone")
    void testAllShipmentStatusesCovered() {
        for (ShipmentStatus status : ShipmentStatus.values()) {
            assertThat(enumDisplayService.shipmentStatus(status)).as(status.name()).isNotBlank();
            assertThat(enumDisplayService.shipmentStatus(status, Locale.ENGLISH)).as(status.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.shipmentStatusTone(status)).as(status.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.shipmentStatusBadgeClass(status)).as(status.name() + " badgeClass").startsWith("status ");
            assertThat(enumDisplayService.shipmentStatusBadge(status).label()).isNotBlank();
        }
        assertThat(enumDisplayService.shipmentStatus(ShipmentStatus.CANCELLED)).isEqualTo("Đã hủy");
        assertThat(enumDisplayService.shipmentStatus(null)).isEqualTo("Không xác định");
    }

    @Test
    @DisplayName("All V3 enums (VoucherType, ReturnStatus, CodBatchStatus) and existing enums yield non-blank labels and CSS tones")
    void testAllV3AndOperationalEnumsCovered() {
        for (VoucherType type : VoucherType.values()) {
            assertThat(enumDisplayService.voucherType(type)).as(type.name()).isNotBlank();
            assertThat(enumDisplayService.voucherType(type, Locale.ENGLISH)).as(type.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.voucherTypeTone(type)).as(type.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.voucherTypeBadgeClass(type)).as(type.name() + " badgeClass").isNotBlank();
            assertThat(enumDisplayService.voucherTypeBadge(type).label()).isNotBlank();
        }

        for (ReturnStatus status : ReturnStatus.values()) {
            assertThat(enumDisplayService.returnStatus(status)).as(status.name()).isNotBlank();
            assertThat(enumDisplayService.returnStatus(status, Locale.ENGLISH)).as(status.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.returnStatusTone(status)).as(status.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.returnStatusBadgeClass(status)).as(status.name() + " badgeClass").startsWith("status ");
            assertThat(enumDisplayService.returnStatusBadge(status).label()).isNotBlank();
        }

        for (CodBatchStatus status : CodBatchStatus.values()) {
            assertThat(enumDisplayService.codBatchStatus(status)).as(status.name()).isNotBlank();
            assertThat(enumDisplayService.codBatchStatus(status, Locale.ENGLISH)).as(status.name() + " EN").isNotBlank();
            assertThat(enumDisplayService.codBatchStatusTone(status)).as(status.name() + " tone").isNotBlank();
            assertThat(enumDisplayService.codBatchStatusBadgeClass(status)).as(status.name() + " badgeClass").startsWith("status ");
            assertThat(enumDisplayService.codBatchStatusBadge(status).label()).isNotBlank();
        }

        for (ShipperStatus status : ShipperStatus.values()) {
            assertThat(enumDisplayService.shipperStatus(status)).isNotBlank();
            assertThat(enumDisplayService.shipperStatusTone(status)).isNotBlank();
            assertThat(enumDisplayService.shipperStatusBadgeClass(status)).startsWith("status ");
        }

        for (PaymentMethod method : PaymentMethod.values()) {
            assertThat(enumDisplayService.paymentMethod(method)).isNotBlank();
            assertThat(enumDisplayService.paymentMethodTone(method)).isNotBlank();
        }

        for (InventoryOperationType op : InventoryOperationType.values()) {
            assertThat(enumDisplayService.inventoryOperation(op)).isNotBlank();
            assertThat(enumDisplayService.inventoryOperationTone(op)).isNotBlank();
        }

        for (OtpDeliveryStatus otpStatus : OtpDeliveryStatus.values()) {
            assertThat(enumDisplayService.otpDeliveryStatus(otpStatus)).isNotBlank();
            assertThat(enumDisplayService.otpDeliveryStatusTone(otpStatus)).isNotBlank();
        }
    }

    @Test
    @DisplayName("All 5 wine types including Traditional yield proper Vietnamese/EN labels and wine tone")
    void testAllFiveWineTypesCovered() {
        for (String wineType : List.of("Red", "White", "Rose", "Sparkling", "Traditional")) {
            assertThat(enumDisplayService.wineType(wineType)).as(wineType).isNotBlank();
            assertThat(enumDisplayService.wineType(wineType, Locale.ENGLISH)).as(wineType + " EN").isNotBlank();
            assertThat(enumDisplayService.wineTypeTone(wineType)).as(wineType + " tone").isNotBlank();
            assertThat(enumDisplayService.wineTypeBadgeClass(wineType)).as(wineType + " badgeClass").isNotBlank();
        }

        assertThat(enumDisplayService.wineType("Traditional")).isEqualTo("Rượu Truyền Thống");
        assertThat(enumDisplayService.wineType("  traditional ")).isEqualTo("Rượu Truyền Thống");
        assertThat(enumDisplayService.wineType("Traditional", Locale.ENGLISH)).isEqualTo("Traditional Wine");
        assertThat(enumDisplayService.wineTypeTone("Traditional")).isEqualTo("wine");
        assertThat(enumDisplayService.wineTypeBadgeClass("Traditional")).isEqualTo("badge badge-wine");
        assertThat(enumDisplayService.wineType("Rosé")).isEqualTo("Vang hồng");
        assertThat(enumDisplayService.wineTypeTone(null)).isEqualTo("neutral");
    }

    @Test
    @DisplayName("Stock status and V3 transaction labels/tones are consistent")
    void testStockAndTransactionDisplays() {
        assertThat(enumDisplayService.stockStatusLabel(0)).isEqualTo("Hết hàng");
        assertThat(enumDisplayService.stockStatusTone(0)).isEqualTo("bad");
        assertThat(enumDisplayService.stockStatusLabel(5)).isEqualTo("Sắp hết hàng");
        assertThat(enumDisplayService.stockStatusTone(5)).isEqualTo("warn");
        assertThat(enumDisplayService.stockStatusLabel(25)).isEqualTo("Còn hàng");
        assertThat(enumDisplayService.stockStatusTone(25)).isEqualTo("ok");

        assertThat(enumDisplayService.paymentTransactionType("COD_COLLECTED")).isNotEqualTo("COD_COLLECTED");
        assertThat(enumDisplayService.paymentTransactionStatus("REFUND_REQUIRED")).isEqualTo("Chờ hoàn tiền");
        assertThat(enumDisplayService.paymentTransactionStatusTone("REFUND_REQUIRED")).isEqualTo("warn");
        assertThat(enumDisplayService.inventoryReferenceType("RETURN_RESTOCK")).isNotEqualTo("RETURN_RESTOCK");
    }
}

