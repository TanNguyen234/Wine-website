package com.strongwine.strongwine.service;

import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class VietnamGeoValidationService {

    private static final double MIN_LAT = 8.2;
    private static final double MAX_LAT = 23.6;
    private static final double MIN_LNG = 102.0;
    private static final double MAX_LNG = 109.7;

    private static final List<String> REMOTE_ISLAND_KEYWORDS = List.of(
            "hoang sa", "hoàng sa",
            "truong sa", "trường sa",
            "con dao", "côn đảo", "con son", "côn sơn",
            "phu quy", "phú quý", "cu lao thu", "cù lao thu",
            "bach long vi", "bạch long vĩ",
            "ly son", "lý sơn", "cu lao re", "cù lao ré",
            "cu lao cham", "cù lao chàm",
            "tho chu", "thổ chu", "tho chau", "thổ châu",
            "hon khoai", "hòn khoai",
            "hon chuoi", "hòn chuối",
            "hon me", "hòn mê",
            "hon ngu", "hòn ngư",
            "cu lao xanh", "cù lao xanh",
            "nam du",
            "phu quoc", "phú quốc",
            "co to", "cô tô",
            "cat ba", "cát bà",
            "hon son", "hòn sơn",
            "hon tre", "hòn tre",
            "binh ba", "bình ba",
            "binh hung", "bình hưng",
            "diep son", "điệp sơn",
            "kien hai", "kiên hải",
            "van don dao", "vân đồn đảo",
            "huyen dao", "huyện đảo",
            "xa dao", "xã đảo",
            "dao xa", "đảo xa"
    );

    private static final List<String> FOREIGN_COUNTRY_KEYWORDS = List.of(
            "singapore", "united states", "thailand", "malaysia", "philippines",
            "japan", "korea", "china", "australia", "france", "usa", "uk",
            "london", "new york", "tokyo", "paris", "bangkok", "seoul",
            "germany", "canada", "russia", "taiwan", "hong kong", "laos", "cambodia"
    );

    // Rough polygon enclosing mainland Vietnam to block out-of-country points.
    private static final double[][] MAINLAND_VIETNAM_POLYGON = {
            {23.23, 102.14},
            {22.50, 104.70},
            {22.85, 106.70},
            {21.55, 108.20},
            {20.50, 108.10},
            {19.20, 107.70},
            {18.00, 107.50},
            {16.80, 108.40},
            {16.00, 108.40},
            {15.00, 109.10},
            {14.00, 109.30},
            {12.30, 109.50},
            {10.80, 107.50},
            {10.30, 107.30},
            {9.40, 105.80},
            {8.50, 104.80},
            {9.70, 104.00},
            {10.90, 103.30},
            {12.20, 104.60},
            {14.10, 107.40},
            {16.00, 106.80},
            {18.10, 105.80},
            {20.40, 104.10},
            {22.00, 103.10}
    };

    /**
     * Validates that an address is in mainland Vietnam and not located in remote islands / offshore archipelagos.
     * Throws DeliveryAddressNotSupportedException or IllegalArgumentException if invalid.
     */
    public void validateMainlandDeliveryAddress(String address, Double lat, Double lng) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ giao hàng chi tiết");
        }

        String normalizedAddress = address.trim().toLowerCase(Locale.ROOT);
        String unaccentedAddress = stripAccents(normalizedAddress);

        // 1. Check for remote islands / offshore archipelagos
        for (String island : REMOTE_ISLAND_KEYWORDS) {
            String unaccentedIsland = stripAccents(island);
            if (normalizedAddress.contains(island) || unaccentedAddress.contains(unaccentedIsland)) {
                throw new DeliveryAddressNotSupportedException(
                        "StrongWine chỉ phục vụ giao rượu vang bảo ôn trên đất liền tại Việt Nam. " +
                        "Rất tiếc hiện chưa hỗ trợ giao hàng tới khu vực huyện đảo/đảo xa (" + island + ") " +
                        "do yêu cầu nghiêm ngặt về nhiệt độ bảo quản hầm rượu.");
            }
        }

        // 2. Check for foreign country indicators
        for (String foreign : FOREIGN_COUNTRY_KEYWORDS) {
            if (normalizedAddress.contains(foreign)) {
                throw new DeliveryAddressNotSupportedException(
                        "Địa chỉ giao hàng không hợp lệ: StrongWine chỉ phục vụ giao hàng nội địa tại Việt Nam (không hỗ trợ quốc tế).");
            }
        }

        // 3. Coordinate validation if coordinates are provided
        if (lat != null && lng != null) {
            if (!isWithinMainlandVietnam(lat, lng)) {
                throw new DeliveryAddressNotSupportedException(
                        "Toạ độ vị trí giao hàng nằm ngoài phạm vi đất liền Việt Nam (không hỗ trợ giao hàng tới khu vực hải đảo xa).");
            }
        }
    }

    /**
     * Checks whether an address points to an offshore island.
     */
    public boolean isRemoteIslandAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String normalizedAddress = address.trim().toLowerCase(Locale.ROOT);
        String unaccentedAddress = stripAccents(normalizedAddress);
        for (String island : REMOTE_ISLAND_KEYWORDS) {
            if (normalizedAddress.contains(island) || unaccentedAddress.contains(stripAccents(island))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves fallback coordinates for common Vietnam cities if customer didn't pick on map.
     */
    public double[] resolveDefaultCoordinates(String address) {
        if (address == null || address.isBlank()) {
            return new double[]{10.7769, 106.7009}; // Default TP.HCM center
        }
        String norm = stripAccents(address.toLowerCase(Locale.ROOT));
        if (norm.contains("ha noi") || norm.contains("hoan kiem") || norm.contains("ba dinh") || norm.contains("cau giay") || norm.contains("dong da")) {
            return new double[]{21.0285, 105.8542}; // Ha Noi
        }
        if (norm.contains("da nang") || norm.contains("hai chau") || norm.contains("son tra") || norm.contains("thanh khe")) {
            return new double[]{16.0544, 108.2022}; // Da Nang
        }
        if (norm.contains("hai phong")) {
            return new double[]{20.8449, 106.6881}; // Hai Phong
        }
        if (norm.contains("can tho") || norm.contains("ninh kieu")) {
            return new double[]{10.0452, 105.7469}; // Can Tho
        }
        if (norm.contains("binh duong") || norm.contains("thu dau mot")) {
            return new double[]{10.9805, 106.6519}; // Binh Duong
        }
        if (norm.contains("dong nai") || norm.contains("bien hoa")) {
            return new double[]{10.9425, 106.8228}; // Dong Nai
        }
        if (norm.contains("vung tau") || norm.contains("ba ria")) {
            return new double[]{10.3460, 107.0843}; // Vung Tau mainland
        }
        // Default to Ho Chi Minh City center
        return new double[]{10.7769, 106.7009};
    }

    public boolean isWithinMainlandVietnam(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false;
        }
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
            return false;
        }
        return isPointInsidePolygon(lat, lng, MAINLAND_VIETNAM_POLYGON);
    }

    private boolean isPointInsidePolygon(double lat, double lng, double[][] polygon) {
        boolean inside = false;
        int j = polygon.length - 1;
        for (int i = 0; i < polygon.length; i++) {
            double yi = polygon[i][0];
            double xi = polygon[i][1];
            double yj = polygon[j][0];
            double xj = polygon[j][1];

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / ((yj - yi) + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }

    private String stripAccents(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized)
                .replaceAll("")
                .replace("đ", "d")
                .replace("Đ", "D");
    }
}
