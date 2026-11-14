package com.strongwine.strongwine.service;

import com.strongwine.strongwine.util.AddressTextUtils;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final ConcurrentHashMap<String, Pattern> TERM_PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Validates that an address is in mainland Vietnam and not located in remote islands / offshore archipelagos.
     * Throws DeliveryAddressNotSupportedException or IllegalArgumentException if invalid.
     */
    public void validateMainlandDeliveryAddress(String address, Double lat, Double lng) {
        String cleaned = AddressTextUtils.normalizeText(address);
        if (cleaned == null || cleaned.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ giao hàng chi tiết");
        }

        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("Toạ độ vĩ độ và kinh độ phải được cung cấp đầy đủ.");
        }
        if (lat != null && !AddressTextUtils.isValidCoordinatePair(lat, lng)) {
            throw new IllegalArgumentException(
                    "Toạ độ GPS không hợp lệ (vĩ độ phải nằm trong [-90, 90] và kinh độ trong [-180, 180]).");
        }

        String textWithoutLegacyGps = AddressTextUtils.stripLegacyGpsSuffix(cleaned);
        String normalizedAddress = textWithoutLegacyGps.toLowerCase(Locale.ROOT);
        String unaccentedAddress = stripAccents(normalizedAddress);

        // 1. Check for remote islands / offshore archipelagos with whole-term boundary matching
        for (String island : REMOTE_ISLAND_KEYWORDS) {
            String lowerIsland = AddressTextUtils.normalizeText(island).toLowerCase(Locale.ROOT);
            String unaccentedIsland = stripAccents(lowerIsland);
            if (containsWholeTerm(normalizedAddress, lowerIsland)
                    || containsWholeTerm(unaccentedAddress, unaccentedIsland)) {
                throw new DeliveryAddressNotSupportedException(
                        "StrongWine chỉ phục vụ giao rượu vang bảo ôn trên đất liền tại Việt Nam. " +
                        "Rất tiếc hiện chưa hỗ trợ giao hàng tới khu vực huyện đảo/đảo xa (" + island + ") " +
                        "do yêu cầu nghiêm ngặt về nhiệt độ bảo quản hầm rượu.");
            }
        }

        // 2. Check for foreign country indicators with whole-term boundary matching (avoids substring false positives)
        for (String foreign : FOREIGN_COUNTRY_KEYWORDS) {
            String lowerForeign = AddressTextUtils.normalizeText(foreign).toLowerCase(Locale.ROOT);
            String unaccentedForeign = stripAccents(lowerForeign);
            if (containsWholeTerm(normalizedAddress, lowerForeign)
                    || containsWholeTerm(unaccentedAddress, unaccentedForeign)) {
                throw new DeliveryAddressNotSupportedException(
                        "Địa chỉ giao hàng không hợp lệ: StrongWine chỉ phục vụ giao hàng nội địa tại Việt Nam (không hỗ trợ quốc tế).");
            }
        }

        // 3. Coordinate validation if coordinates are provided
        if (lat != null && lng != null && !isWithinMainlandVietnam(lat, lng)) {
            throw new DeliveryAddressNotSupportedException(
                    "Toạ độ vị trí giao hàng nằm ngoài phạm vi đất liền Việt Nam (không hỗ trợ giao hàng tới khu vực hải đảo xa).");
        }
    }

    /**
     * Checks whether an address points to an offshore island.
     */
    public boolean isRemoteIslandAddress(String address) {
        String cleaned = AddressTextUtils.normalizeText(address);
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }
        String normalizedAddress = AddressTextUtils.stripLegacyGpsSuffix(cleaned).toLowerCase(Locale.ROOT);
        String unaccentedAddress = stripAccents(normalizedAddress);
        for (String island : REMOTE_ISLAND_KEYWORDS) {
            String lowerIsland = AddressTextUtils.normalizeText(island).toLowerCase(Locale.ROOT);
            String unaccentedIsland = stripAccents(lowerIsland);
            if (containsWholeTerm(normalizedAddress, lowerIsland)
                    || containsWholeTerm(unaccentedAddress, unaccentedIsland)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves fallback coordinates for common Vietnam cities if customer didn't pick on map.
     */
    public double[] resolveDefaultCoordinates(String address) {
        String cleaned = AddressTextUtils.normalizeText(address);
        if (cleaned == null || cleaned.isEmpty()) {
            return new double[]{10.7769, 106.7009}; // Default TP.HCM center
        }

        String withoutGps = AddressTextUtils.stripLegacyGpsSuffix(cleaned);
        String norm = stripAccents(withoutGps.toLowerCase(Locale.ROOT));
        String[] rawSegments = norm.split(",");

        // Pass 1: Match city/province tokens from right-most segment (province/city) to left-most (street)
        for (int i = rawSegments.length - 1; i >= 0; i--) {
            String seg = normalizeSegmentTokens(rawSegments[i]);
            if (seg.isEmpty()) {
                continue;
            }
            double[] cityCoords = matchCityOrProvinceSegment(seg);
            if (cityCoords != null) {
                return cityCoords;
            }
        }

        // Pass 2: Fall back to distinctive district tokens from right to left
        for (int i = rawSegments.length - 1; i >= 0; i--) {
            String seg = normalizeSegmentTokens(rawSegments[i]);
            if (seg.isEmpty()) {
                continue;
            }
            double[] districtCoords = matchDistrictSegment(seg);
            if (districtCoords != null) {
                return districtCoords;
            }
        }

        // Default to Ho Chi Minh City center
        return new double[]{10.7769, 106.7009};
    }

    public boolean isWithinMainlandVietnam(Double lat, Double lng) {
        if (!AddressTextUtils.isValidCoordinatePair(lat, lng)) {
            return false;
        }
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
            return false;
        }
        return isPointInsidePolygon(lat, lng, MAINLAND_VIETNAM_POLYGON);
    }

    private static boolean containsWholeTerm(String text, String term) {
        if (text == null || text.isEmpty() || term == null || term.isEmpty()) {
            return false;
        }
        Pattern pattern = TERM_PATTERN_CACHE.computeIfAbsent(term, key -> Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(key) + "(?![\\p{L}\\p{N}])",
                Pattern.UNICODE_CASE));
        return pattern.matcher(text).find();
    }

    private static String normalizeSegmentTokens(String segment) {
        if (segment == null) {
            return "";
        }
        return segment.replace('.', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double[] matchCityOrProvinceSegment(String seg) {
        if (containsAnyWholeTerm(seg, "ha noi", "hanoi", "tp ha noi", "thanh pho ha noi")) {
            return new double[]{21.0285, 105.8542};
        }
        if (containsAnyWholeTerm(seg, "ho chi minh", "tp ho chi minh", "tphcm", "tp hcm", "hcm", "sai gon", "saigon")) {
            return new double[]{10.7769, 106.7009};
        }
        if (containsAnyWholeTerm(seg, "da nang", "danang", "tp da nang", "thanh pho da nang")) {
            return new double[]{16.0544, 108.2022};
        }
        if (containsAnyWholeTerm(seg, "hai phong", "haiphong", "tp hai phong")) {
            return new double[]{20.8449, 106.6881};
        }
        if (containsAnyWholeTerm(seg, "can tho", "cantho", "tp can tho")) {
            return new double[]{10.0452, 105.7469};
        }
        if (containsAnyWholeTerm(seg, "da lat", "dalat", "tp da lat", "lam dong")) {
            return new double[]{11.9404, 108.4583};
        }
        if (containsAnyWholeTerm(seg, "thua thien hue", "tp hue", "thanh pho hue")
                || (containsWholeTerm(seg, "hue") && !seg.startsWith("ngo ") && !seg.startsWith("duong ") && !seg.startsWith("pho "))) {
            return new double[]{16.4637, 107.5909};
        }
        if (containsAnyWholeTerm(seg, "nha trang", "khanh hoa", "tp nha trang")) {
            return new double[]{12.2388, 109.1967};
        }
        if (containsAnyWholeTerm(seg, "binh duong")) {
            return new double[]{10.9805, 106.6519};
        }
        if (containsAnyWholeTerm(seg, "dong nai", "bien hoa")) {
            return new double[]{10.9425, 106.8228};
        }
        if (containsAnyWholeTerm(seg, "vung tau", "ba ria", "ba ria vung tau")) {
            return new double[]{10.3460, 107.0843};
        }
        if (containsAnyWholeTerm(seg, "quy nhon", "qui nhon", "binh dinh")) {
            return new double[]{13.7820, 109.2197};
        }
        if (containsAnyWholeTerm(seg, "tp vinh", "thanh pho vinh", "nghe an")) {
            return new double[]{18.6796, 105.6813};
        }
        return null;
    }

    private static double[] matchDistrictSegment(String seg) {
        if (containsAnyWholeTerm(seg, "quan 1", "quan 2", "quan 3", "quan 4", "quan 5", "quan 6",
                "quan 7", "quan 8", "quan 9", "quan 10", "quan 11", "quan 12",
                "q 1", "q1", "q 3", "q3", "q 7", "q7",
                "binh thanh", "phu nhuan", "tan binh", "go vap", "thu duc", "binh tan", "tan phu")) {
            return new double[]{10.7769, 106.7009};
        }
        if (containsAnyWholeTerm(seg, "hoan kiem", "ba dinh", "cau giay", "dong da",
                "hai ba trung", "tay ho", "thanh xuan", "long bien", "ha dong", "nam tu liem", "bac tu liem", "hoang mai")) {
            return new double[]{21.0285, 105.8542};
        }
        if (containsAnyWholeTerm(seg, "hai chau", "son tra", "thanh khe", "ngu hanh son", "lien chieu", "cam le")) {
            return new double[]{16.0544, 108.2022};
        }
        if (containsAnyWholeTerm(seg, "ninh kieu", "binh thuy", "cai rang")) {
            return new double[]{10.0452, 105.7469};
        }
        if (containsAnyWholeTerm(seg, "thu dau mot", "thuan an", "di an")) {
            return new double[]{10.9805, 106.6519};
        }
        return null;
    }

    private static boolean containsAnyWholeTerm(String segment, String... terms) {
        for (String term : terms) {
            if (containsWholeTerm(segment, term)) {
                return true;
            }
        }
        return false;
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
