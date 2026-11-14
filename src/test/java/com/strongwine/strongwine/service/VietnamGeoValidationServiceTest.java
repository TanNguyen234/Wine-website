package com.strongwine.strongwine.service;

import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.util.AddressTextUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietnamGeoValidationServiceTest {

    private VietnamGeoValidationService geoService;

    @BeforeEach
    void setUp() {
        geoService = new VietnamGeoValidationService();
    }

    @Test
    @DisplayName("Mainland Vietnam addresses should pass validation")
    void testMainlandAddressesPass() {
        assertThatCode(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh", 10.7769, 106.7009))
                .doesNotThrowAnyException();

        assertThatCode(() -> geoService.validateMainlandDeliveryAddress("15 Tràng Tiền, Quận Hoàn Kiếm, Hà Nội", 21.0285, 105.8542))
                .doesNotThrowAnyException();

        assertThatCode(() -> geoService.validateMainlandDeliveryAddress("Bạch Đằng, Quận Hải Châu, Đà Nẵng", 16.0544, 108.2022))
                .doesNotThrowAnyException();

        assertThatCode(() -> geoService.validateMainlandDeliveryAddress("Thủ Dầu Một, Bình Dương", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NFD Unicode, non-breaking spaces, and zero-width characters normalize cleanly without false-positive foreign substring matches")
    void testUnicodeNormalizationAndWordBoundaries() {
        assertThatCode(() -> geoService.validateMainlandDeliveryAddress(
                "72\u00A0\u00A0Le\u0302   Tha\u0301nh\u200B  To\u0302n, Qu\u1eadn 1, TP. Ho\u0302\u0300 Chi\u0301 Minh",
                10.7769,
                106.7009))
                .doesNotThrowAnyException();

        // Must NOT trigger false positive on "uk" substring inside Vietnamese words or street names like Lukas/Phukien
        assertThatCode(() -> geoService.validateMainlandDeliveryAddress(
                "Số 12 Đường Thủ Đức, Khu phố Lukas, TP. Thủ Đức, TP. Hồ Chí Minh",
                10.8490,
                106.7717))
                .doesNotThrowAnyException();

        // NFD decomposed island with non-breaking spaces must still be caught
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(
                "Thị trấn Côn Sơn, Huyện C\u006f\u0302n\u00A0  \u0110a\u0309o, Bà Rịa - Vũng Tàu",
                null,
                null))
                .isInstanceOf(DeliveryAddressNotSupportedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Thị trấn Côn Sơn, Huyện Côn Đảo, Bà Rịa - Vũng Tàu",
            "Quần đảo Hoàng Sa, TP Đà Nẵng",
            "Đặc khu Trường Sa, Khánh Hòa",
            "Huyện đảo Phú Quý, Bình Thuận",
            "Huyện đảo Lý Sơn, Quảng Ngãi",
            "Xã đảo Cù Lao Chàm, Hội An, Quảng Nam",
            "Thổ Chu, Phú Quốc, Kiên Giang",
            "Đảo Phú Quốc, Kiên Giang",
            "Huyện đảo Cô Tô, Quảng Ninh",
            "Đảo Cát Bà, Cát Hải, Hải Phòng",
            "Xã đảo Nam Du, Kiên Giang",
            "Đảo Bình Ba, Cam Ranh, Khánh Hòa"
    })
    @DisplayName("Remote island addresses must be rejected with DeliveryAddressNotSupportedException")
    void testRemoteIslandsRejected(String islandAddress) {
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(islandAddress, null, null))
                .isInstanceOf(DeliveryAddressNotSupportedException.class)
                .hasMessageContaining("đảo");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10 Marina Boulevard, Singapore",
            "123 Broadway, New York, USA",
            "Champs-Élysées, Paris, France",
            "Shibuya, Tokyo, Japan",
            "Bangkok, Thailand",
            "221B Baker Street, London, UK"
    })
    @DisplayName("Foreign country addresses must be rejected")
    void testForeignCountriesRejected(String foreignAddress) {
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(foreignAddress, null, null))
                .isInstanceOf(DeliveryAddressNotSupportedException.class)
                .hasMessageContaining("chỉ phục vụ giao hàng nội địa tại Việt Nam");
    }

    @Test
    @DisplayName("Null, blank, or invisible-whitespace-only address should throw IllegalArgumentException")
    void testNullOrBlankAddress() {
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("\u00A0\u200B  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coordinates outside mainland Vietnam polygon should be rejected")
    void testOutOfCountryCoordinatesRejected() {
        // Coordinate in the Pacific Ocean / outside mainland
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("Địa chỉ mẫu", 0.0, 0.0))
                .isInstanceOf(DeliveryAddressNotSupportedException.class);
    }

    @Test
    @DisplayName("Invalid lat/lng bounds (NaN, Infinity, >90, >180, partial pair) must be rejected")
    void testInvalidCoordinateBoundsRejected() {
        assertThat(geoService.isWithinMainlandVietnam(91.0, 106.7)).isFalse();
        assertThat(geoService.isWithinMainlandVietnam(-91.0, 106.7)).isFalse();
        assertThat(geoService.isWithinMainlandVietnam(10.7, 181.0)).isFalse();
        assertThat(geoService.isWithinMainlandVietnam(Double.NaN, 106.7)).isFalse();
        assertThat(geoService.isWithinMainlandVietnam(10.7, Double.POSITIVE_INFINITY)).isFalse();

        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Quận 1", 95.0, 106.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Quận 1", 10.7, 185.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Quận 1", Double.NaN, 106.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Quận 1", 10.7769, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("72 Lê Thánh Tôn, Quận 1", null, 106.7009))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isRemoteIslandAddress helper correctly identifies island strings")
    void testIsRemoteIslandAddress() {
        assertThat(geoService.isRemoteIslandAddress("Côn Đảo")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("huyện đảo Hoàng Sa")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("Phú Quốc")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("72 Lê Thánh Tôn, Quận 1")).isFalse();
    }

    @Test
    @DisplayName("resolveDefaultCoordinates provides sensible mainland city fallbacks across cities, abbreviations, and street-vs-city priority")
    void testResolveDefaultCoordinates() {
        double[] hcm = geoService.resolveDefaultCoordinates("Quận 1, TP. Hồ Chí Minh");
        assertThat(hcm).containsExactly(10.7769, 106.7009);

        double[] hcmAbbrev = geoService.resolveDefaultCoordinates("P. Bến Nghé, Q.1, TPHCM");
        assertThat(hcmAbbrev).containsExactly(10.7769, 106.7009);

        double[] hanoi = geoService.resolveDefaultCoordinates("Hoàn Kiếm, Hà Nội");
        assertThat(hanoi).containsExactly(21.0285, 105.8542);

        // Street named "Ngõ Huế" in Hà Nội must resolve to Hà Nội, not Huế
        double[] hanoiWithHueStreet = geoService.resolveDefaultCoordinates("Ngõ Huế, Hai Bà Trưng, Hà Nội");
        assertThat(hanoiWithHueStreet).containsExactly(21.0285, 105.8542);

        double[] danang = geoService.resolveDefaultCoordinates("Hải Châu, Đà Nẵng");
        assertThat(danang).containsExactly(16.0544, 108.2022);

        double[] dalat = geoService.resolveDefaultCoordinates("Phường Xuân Hương, TP. Đà Lạt, Lâm Đồng");
        assertThat(dalat).containsExactly(11.9404, 108.4583);
        assertThat(geoService.isWithinMainlandVietnam(dalat[0], dalat[1])).isTrue();

        double[] hue = geoService.resolveDefaultCoordinates("Phường Vĩnh Ninh, TP. Huế, Thừa Thiên Huế");
        assertThat(hue).containsExactly(16.4637, 107.5909);
        assertThat(geoService.isWithinMainlandVietnam(hue[0], hue[1])).isTrue();

        double[] nhaTrang = geoService.resolveDefaultCoordinates("Phường Lộc Thọ, Nha Trang, Khánh Hòa");
        assertThat(nhaTrang).containsExactly(12.2388, 109.1967);
        assertThat(geoService.isWithinMainlandVietnam(nhaTrang[0], nhaTrang[1])).isTrue();
    }

    @Test
    @DisplayName("AddressTextUtils normalizes whitespace/Unicode and rejects invalid GPS bounds")
    void testAddressTextUtilsEdgeCases() {
        assertThat(AddressTextUtils.normalizeText("  72\u00A0\u00A0Le\u0302   Tha\u0301nh\u200B  To\u0302n  "))
                .isEqualTo("72 Lê Thánh Tôn");

        assertThat(AddressTextUtils.stripLegacyGpsSuffix("  123  Hai\u00A0Bà  Trưng [gps: 10.781200, 106.698500] "))
                .isEqualTo("123 Hai Bà Trưng");

        assertThat(AddressTextUtils.extractLegacyCoordinates("123 Hai Bà Trưng [gps: 10.781200, 106.698500]"))
                .contains(new AddressTextUtils.Coordinates(10.7812, 106.6985));

        assertThat(AddressTextUtils.extractLegacyCoordinates("123 Hai Bà Trưng [GPS: 95.000000, 200.000000]"))
                .isEmpty();

        assertThat(AddressTextUtils.isValidCoordinatePair(10.7769, 106.7009)).isTrue();
        assertThat(AddressTextUtils.isValidCoordinatePair(91.0, 106.7009)).isFalse();
        assertThat(AddressTextUtils.isValidCoordinatePair(10.7769, 181.0)).isFalse();
        assertThat(AddressTextUtils.isValidCoordinatePair(Double.NaN, 106.7009)).isFalse();
    }
}

