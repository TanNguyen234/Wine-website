package com.strongwine.strongwine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class GeminiAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAssistantService.class);
    private static final int MAX_PROMPT_LENGTH = 1000;
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 600;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    public GeminiAssistantService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    public String ask(String prompt, String context) {
        String normalizedPrompt = normalizePrompt(prompt);
        String normalizedContext = normalizeContext(context);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            logger.warn("Gemini API key is missing. model={}, baseUrl={}", resolveModel(), resolveBaseUrl());
            return "Chatbot hiện chưa được cấu hình GEMINI_API_KEY ở backend.";
        }

        String fullPrompt = buildPrompt(normalizedPrompt, normalizedContext);

        try {
            Map<String, Object> payload = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", fullPrompt))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.6,
                            "maxOutputTokens", 512
                    )
            );

            String requestBody = objectMapper.writeValueAsString(payload);
            String primaryModel = resolveModel();
            GeminiHttpResult result = sendGeminiRequest(requestBody, primaryModel);

            if (!result.isSuccess() && !"gemini-2.0-flash".equals(primaryModel)) {
                logger.info("Retry Gemini with fallback model. previousStatus={}, previousModel={}", result.statusCode(), primaryModel);
                result = sendGeminiRequest(requestBody, "gemini-2.0-flash");
            }

            if (!result.isSuccess()) {
                logger.warn("Gemini API returned non-2xx status. status={}, model={}, baseUrl={}, responseBody={}",
                        result.statusCode(), result.model(), resolveBaseUrl(), abbreviateForLog(result.body()));
                return buildLocalAdvice(normalizedPrompt, normalizedContext);
            }

            return parseAssistantResponse(result.body());
        } catch (Exception ex) {
            logger.error("Gemini API call failed. model={}, baseUrl={}", resolveModel(), resolveBaseUrl(), ex);
            return buildLocalAdvice(normalizedPrompt, normalizedContext);
        }
    }

    private String parseAssistantResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "Mình chưa tạo được câu trả lời phù hợp. Bạn thử hỏi cụ thể hơn nhé.";
        }

        JsonNode firstCandidate = candidates.get(0);
        JsonNode parts = firstCandidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return "Mình chưa tạo được câu trả lời phù hợp. Bạn thử hỏi cụ thể hơn nhé.";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("").trim();
            if (!text.isEmpty()) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(text);
            }
        }

        if (textBuilder.isEmpty()) {
            return "Mình chưa tạo được câu trả lời phù hợp. Bạn thử hỏi cụ thể hơn nhé.";
        }
        return textBuilder.toString();
    }

    private String buildPrompt(String prompt, String context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Bạn là trợ lý tư vấn rượu vang của StrongWine. ")
                .append("Trả lời ngắn gọn, rõ ràng, bằng tiếng Việt, ưu tiên tư vấn mua hàng thực tế.")
                .append(" Không bịa thông tin tồn kho hoặc giá nếu không chắc chắn; hãy nói rõ là cần kiểm tra trên website.");

        if (!context.isEmpty()) {
            builder.append("\n\nNgữ cảnh trang hiện tại: ").append(context);
        }

        builder.append("\n\nCâu hỏi khách hàng: ").append(prompt);
        return builder.toString();
    }

    private String buildEndpoint(String model) {
        String normalizedBaseUrl = resolveBaseUrl();
        String encodedKey = URLEncoder.encode(geminiApiKey.trim(), StandardCharsets.UTF_8);
        return normalizedBaseUrl + "/models/" + model + ":generateContent?key=" + encodedKey;
    }

    private GeminiHttpResult sendGeminiRequest(String requestBody, String model) throws Exception {
        String endpoint = buildEndpoint(model);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new GeminiHttpResult(response.statusCode(), response.body(), model);
    }

    private String resolveModel() {
        return geminiModel == null || geminiModel.isBlank() ? "gemini-2.0-flash" : geminiModel.trim();
    }

    private String resolveBaseUrl() {
        if (geminiBaseUrl == null || geminiBaseUrl.isBlank()) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        return geminiBaseUrl.endsWith("/")
                ? geminiBaseUrl.substring(0, geminiBaseUrl.length() - 1)
                : geminiBaseUrl;
    }

    private String abbreviateForLog(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_ERROR_BODY_LOG_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_BODY_LOG_LENGTH) + "...";
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập nội dung cần tư vấn");
        }
        String normalized = prompt.trim();
        if (normalized.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("Nội dung câu hỏi quá dài (tối đa 1000 ký tự)");
        }
        return normalized;
    }

    private String normalizeContext(String context) {
        if (context == null) {
            return "";
        }
        String normalized = context.trim();
        if (normalized.length() > 300) {
            return normalized.substring(0, 300);
        }
        return normalized;
    }

    private String buildLocalAdvice(String prompt, String context) {
        String normalized = prompt.toLowerCase(Locale.ROOT);
        StringBuilder advice = new StringBuilder("Mình gợi ý nhanh cho bạn như sau: ");

        if (normalized.contains("hải sản") || normalized.contains("ca") || normalized.contains("tôm")) {
            advice.append("Bạn nên chọn vang trắng Sauvignon Blanc hoặc Chardonnay nhẹ, ướp lạnh khoảng 8-10°C.");
        } else if (normalized.contains("bò") || normalized.contains("thịt đỏ") || normalized.contains("nướng")) {
            advice.append("Bạn nên chọn vang đỏ Cabernet Sauvignon hoặc Pinot Noir, phục vụ ở 16-18°C.");
        } else if (normalized.contains("quà") || normalized.contains("tặng")) {
            advice.append("Bạn có thể chọn một chai vang đỏ tầm trung, nhãn dễ uống và đóng gói hộp quà.");
        } else if (normalized.contains("dưới") || normalized.contains("ngân sách") || normalized.contains("giá")) {
            advice.append("Bạn lọc theo khoảng giá trên trang Sản phẩm, ưu tiên chai có đánh giá tốt và còn hàng.");
        } else {
            advice.append("Bạn cho mình biết thêm món ăn đi kèm, ngân sách và khẩu vị (chát/êm) để mình gợi ý chính xác hơn.");
        }

        if (context != null && !context.isBlank()) {
            advice.append(" Mình đang dựa trên ngữ cảnh trang hiện tại để tư vấn nhanh.");
        }
        advice.append(" Nếu cần, mình sẽ tiếp tục gợi ý theo 2-3 lựa chọn cụ thể.");
        return advice.toString();
    }

    private record GeminiHttpResult(int statusCode, String body, String model) {
        private boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
