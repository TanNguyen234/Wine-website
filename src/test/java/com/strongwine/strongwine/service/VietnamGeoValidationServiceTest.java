package com.strongwine.strongwine.service;

import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
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
            "Bangkok, Thailand"
    })
    @DisplayName("Foreign country addresses must be rejected")
    void testForeignCountriesRejected(String foreignAddress) {
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(foreignAddress, null, null))
                .isInstanceOf(DeliveryAddressNotSupportedException.class)
                .hasMessageContaining("chỉ phục vụ giao hàng nội địa tại Việt Nam");
    }

    @Test
    @DisplayName("Null or blank address should throw IllegalArgumentException")
    void testNullOrBlankAddress() {
        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> geoService.validateMainlandDeliveryAddress("   ", null, null))
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
    @DisplayName("isRemoteIslandAddress helper correctly identifies island strings")
    void testIsRemoteIslandAddress() {
        assertThat(geoService.isRemoteIslandAddress("Côn Đảo")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("huyện đảo Hoàng Sa")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("Phú Quốc")).isTrue();
        assertThat(geoService.isRemoteIslandAddress("72 Lê Thánh Tôn, Quận 1")).isFalse();
    }

    @Test
    @DisplayName("resolveDefaultCoordinates provides sensible mainland city fallbacks")
    void testResolveDefaultCoordinates() {
        double[] hcm = geoService.resolveDefaultCoordinates("Quận 1, TP. Hồ Chí Minh");
        assertThat(hcm).isNotNull();
        assertThat(hcm[0]).isEqualTo(10.7769);
        assertThat(hcm[1]).isEqualTo(106.7009);

        double[] hanoi = geoService.resolveDefaultCoordinates("Hoàn Kiếm, Hà Nội");
        assertThat(hanoi).isNotNull();
        assertThat(hanoi[0]).isEqualTo(21.0285);
        assertThat(hanoi[1]).isEqualTo(105.8542);

        double[] danang = geoService.resolveDefaultCoordinates("Hải Châu, Đà Nẵng");
        assertThat(danang).isNotNull();
        assertThat(danang[0]).isEqualTo(16.0544);
        assertThat(danang[1]).isEqualTo(108.2022);
    }
}
