package com.strongwine.strongwine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
