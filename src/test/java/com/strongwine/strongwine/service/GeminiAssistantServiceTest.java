package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.WineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAssistantServiceTest {

    @Mock
    private WineRepository wineRepository;

    @InjectMocks
    private GeminiAssistantService assistantService;

    @BeforeEach
    void setUp() {
        Wine wine1 = new Wine();
        wine1.setId(1L);
        wine1.setName("Château Margaux Premier Grand Cru");
        wine1.setType("Red");
        wine1.setCountry("France");
        wine1.setPrice(BigDecimal.valueOf(18500000));
        wine1.setDescription("Hương quả lý chua đen, gỗ sồi tuyết tùng và cấu trúc tannin thượng hạng.");

        Wine wine2 = new Wine();
        wine2.setId(2L);
        wine2.setName("Cloudy Bay Sauvignon Blanc");
        wine2.setType("White");
        wine2.setCountry("New Zealand");
        wine2.setPrice(BigDecimal.valueOf(1250000));
        wine2.setDescription("Hương thơm cam chanh tươi mát, chanh dây và khoáng chất thanh khiết.");

        Wine wine3 = new Wine();
        wine3.setId(3L);
        wine3.setName("Moët & Chandon Impérial Brut");
        wine3.setType("Sparkling");
        wine3.setCountry("France");
        wine3.setPrice(BigDecimal.valueOf(1950000));
        wine3.setDescription("Bọt sủi mịn màng, hương táo xanh và hoa trắng thanh lịch.");

        org.mockito.Mockito.lenient().when(wineRepository.findByDeletedFalse()).thenReturn(List.of(wine1, wine2, wine3));

        // Disable external network calls in unit tests so it deterministic tests the engine
        ReflectionTestUtils.setField(assistantService, "omniEnabled", false);
        ReflectionTestUtils.setField(assistantService, "geminiApiKey", "");
    }

    @Test
    void ask_greeting_returnsWarmSommelierWelcome() {
        String response = assistantService.ask("xin chào", "Trang chủ");

        assertThat(response).contains("StrongWine");
        assertThat(response).contains("Sommelier");
        assertThat(response).doesNotContain("bảo trì nhẹ");
    }

    @Test
    void ask_beefSteakPairing_recommendsRedWine() {
        String response = assistantService.ask("Gợi ý vang đỏ dùng với bít tết bò", "Bộ sưu tập");

        assertThat(response).contains("bít tết");
        assertThat(response).contains("vang đỏ");
        assertThat(response).contains("Château Margaux");
    }

    @Test
    void ask_seafoodPairing_recommendsWhiteWine() {
        String response = assistantService.ask("Tư vấn rượu uống với hải sản tôm hàu", "Vang trắng");

        assertThat(response).contains("hải sản");
        assertThat(response).contains("vang trắng");
        assertThat(response).contains("Cloudy Bay");
    }

    @Test
    void ask_partyCelebration_recommendsSparklingWine() {
        String response = assistantService.ask("Chọn vang sủi cho tiệc kỷ niệm", "Vang sủi");

        assertThat(response).contains("tiệc");
        assertThat(response).contains("vang sủi");
        assertThat(response).contains("Moët & Chandon");
    }

    @Test
    void ask_budgetUnder2Million_filtersProperWines() {
        String response = assistantService.ask("Tư vấn rượu vang dưới 2 triệu", "Tất cả");

        assertThat(response).contains("2,000,000");
        assertThat(response).contains("Cloudy Bay");
        assertThat(response).doesNotContain("18,500,000");
    }

    @Test
    void ask_blankPrompt_throwsException() {
        assertThatThrownBy(() -> assistantService.ask("   ", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
