package com.strongwine.strongwine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiGatewayResilienceTest {

    @Mock
    private WineRepository wineRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private GeminiAssistantService assistantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        Wine wine1 = new Wine();
        wine1.setId(1L);
        wine1.setName("Château Margaux Premier Grand Cru");
        wine1.setType("Red");
        wine1.setYear(2015);
        wine1.setCountry("France");
        wine1.setPrice(BigDecimal.valueOf(18_500_000));
        wine1.setDescription("Tannin mịn, hương lý chua đen và gỗ sồi tuyết tùng.");
        wine1.setDeleted(false);

        Inventory inv = new Inventory();
        inv.setWine(wine1);
        inv.setCurrentQuantity(8);
        inv.setReservedQuantity(0);

        when(wineRepository.findByDeletedFalse()).thenReturn(List.of(wine1));
        lenient().when(inventoryRepository.findAllActiveByWineIdIn(anyList())).thenReturn(List.of(inv));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        assistantService = new GeminiAssistantService();
        ReflectionTestUtils.setField(assistantService, "wineRepository", wineRepository);
        ReflectionTestUtils.setField(assistantService, "inventoryRepository", inventoryRepository);
        ReflectionTestUtils.setField(assistantService, "geminiApiKey", "test-gemini-key");
        ReflectionTestUtils.setField(assistantService, "geminiBaseUrl", baseUrl + "/v1beta");
        ReflectionTestUtils.setField(assistantService, "omniBaseUrl", baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void skipsOmniGatewayWhenApiKeyIsBlankAndCallsGeminiDirectly() {
        AtomicInteger omniHits = new AtomicInteger();
        AtomicInteger geminiHits = new AtomicInteger();
        List<String> capturedPayloads = new CopyOnWriteArrayList<>();

        server.createContext("/v1/chat/completions", exchange -> {
            omniHits.incrementAndGet();
            byte[] err = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            geminiHits.incrementAndGet();
            capturedPayloads.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = geminiJsonEnvelope(
                    "Gợi ý từ Gemini Live: Château Margaux kết hợp hoàn hảo với bò bít tết.",
                    List.of(1L)
            );
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.start();

        ReflectionTestUtils.setField(assistantService, "omniEnabled", true);
        ReflectionTestUtils.setField(assistantService, "omniApiKey", "   ");
        ReflectionTestUtils.setField(assistantService, "geminiModel", "gemini-2.5-flash");

        GeminiAssistantService.StructuredAssistantResponse response =
                assistantService.askStructured("Gợi ý vang đỏ ăn bò bít tết", "storefront");

        assertThat(omniHits.get()).isZero();
        assertThat(geminiHits.get()).isEqualTo(1);
        assertThat(response.answer()).contains("Gợi ý từ Gemini Live");
        assertThat(response.recommendations())
                .extracting(GeminiAssistantService.WineRecommendationCard::id)
                .containsExactly(1L);
    }

    @Test
    void fallsBackToNextCandidateModelWhenPrimaryTimesOutAndUsesSufficientTokenBudget() throws Exception {
        AtomicInteger slowPrimaryHits = new AtomicInteger();
        AtomicInteger fallbackHits = new AtomicInteger();
        List<String> capturedPayloads = new CopyOnWriteArrayList<>();

        server.createContext("/v1beta/models/gemini-3.8-flash:generateContent", exchange -> {
            slowPrimaryHits.incrementAndGet();
            try {
                Thread.sleep(1100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] err = "{\"error\":\"timeout\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(504, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            fallbackHits.incrementAndGet();
            capturedPayloads.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = geminiJsonEnvelope(
                    "Fallback Live OK: chọn Château Margaux 2015 cho bữa tối.",
                    List.of(1L)
            );
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.start();

        ReflectionTestUtils.setField(assistantService, "omniEnabled", false);
        ReflectionTestUtils.setField(assistantService, "geminiModel", "gemini-3.8-flash");
        ReflectionTestUtils.setField(assistantService, "geminiTimeoutMs", 450L);

        GeminiAssistantService.StructuredAssistantResponse response =
                assistantService.askStructured("Tư vấn vang Pháp", "storefront");

        assertThat(slowPrimaryHits.get()).isEqualTo(1);
        assertThat(fallbackHits.get()).isEqualTo(1);
        assertThat(response.answer()).contains("Fallback Live OK");
        assertThat(capturedPayloads).hasSize(1);

        JsonNode reqNode = objectMapper.readTree(capturedPayloads.get(0));
        int maxOutputTokens = reqNode.path("generationConfig").path("maxOutputTokens").asInt(0);
        assertThat(maxOutputTokens).isGreaterThanOrEqualTo(4096);
    }

    @Test
    void recoversNaturalProseAndRecommendedIdsWhenUpstreamJsonIsTruncated() throws Exception {
        String truncatedJson = "{\"answer\":\"StrongWine gợi ý Château Margaux Premier Grand Cru [ID #1] kết hợp hoàn hảo với bò bít tết.\\nHương gỗ sồi tinh tế giúp cân bằng vị béo";
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "candidates", List.of(java.util.Map.of(
                            "finishReason", "MAX_TOKENS",
                            "content", java.util.Map.of(
                                    "parts", List.of(java.util.Map.of("text", truncatedJson))
                            )
                    ))
            ));
            byte[] ok = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.start();

        ReflectionTestUtils.setField(assistantService, "omniEnabled", false);
        ReflectionTestUtils.setField(assistantService, "geminiModel", "gemini-2.5-flash");

        GeminiAssistantService.StructuredAssistantResponse response =
                assistantService.askStructured("Gợi ý vang đỏ ăn bò bít tết", "storefront");

        assertThat(response.answer())
                .doesNotContain("{\"answer\"")
                .contains("StrongWine gợi ý Château Margaux Premier Grand Cru");
        assertThat(response.recommendations())
                .extracting(GeminiAssistantService.WineRecommendationCard::id)
                .containsExactly(1L);
    }

    @Test
    void ignoresGeminiThoughtPartsAndRequestsAtLeast4096MaxOutputTokens() throws Exception {
        List<String> capturedPayloads = new CopyOnWriteArrayList<>();
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            capturedPayloads.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String structuredPayload = objectMapper.writeValueAsString(
                    java.util.Map.of("answer", "Chọn Château Margaux [ID #1] cho bữa tối.", "recommendedIds", List.of(1L))
            );
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "candidates", List.of(java.util.Map.of(
                            "finishReason", "STOP",
                            "content", java.util.Map.of(
                                    "parts", List.of(
                                            java.util.Map.of("thought", true, "text", "Internal English reasoning trace."),
                                            java.util.Map.of("text", structuredPayload)
                                    )
                            )
                    ))
            ));
            byte[] ok = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.start();

        ReflectionTestUtils.setField(assistantService, "omniEnabled", false);
        ReflectionTestUtils.setField(assistantService, "geminiModel", "gemini-2.5-flash");

        GeminiAssistantService.StructuredAssistantResponse response =
                assistantService.askStructured("Tư vấn vang Pháp", "storefront");

        assertThat(response.answer())
                .doesNotContain("Internal English reasoning trace")
                .isEqualTo("Chọn Château Margaux [ID #1] cho bữa tối.");
        assertThat(capturedPayloads).hasSize(1);
        JsonNode reqNode = objectMapper.readTree(capturedPayloads.get(0));
        assertThat(reqNode.path("generationConfig").path("maxOutputTokens").asInt(0))
                .isGreaterThanOrEqualTo(4096);
    }

    private byte[] geminiJsonEnvelope(String answerText, List<Long> wineIds) throws IOException {
        String structuredPayload = objectMapper.writeValueAsString(
                java.util.Map.of("answer", answerText, "recommendedIds", wineIds)
        );
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "candidates", List.of(java.util.Map.of(
                        "finishReason", "STOP",
                        "content", java.util.Map.of(
                                "parts", List.of(java.util.Map.of("text", structuredPayload))
                        )
                ))
        ));
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
