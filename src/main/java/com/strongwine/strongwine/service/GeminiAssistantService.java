package com.strongwine.strongwine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class GeminiAssistantService {

    private static final int MAX_PROMPT_LENGTH = 1000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    public GeminiAssistantService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    public String ask(String prompt, String context) {
        String normalizedPrompt = normalizePrompt(prompt);
        String normalizedContext = normalizeContext(context);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
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
            String endpoint = buildEndpoint();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Gemini tạm thời không phản hồi. Vui lòng thử lại sau.";
            }

            return parseAssistantResponse(response.body());
        } catch (Exception ex) {
            return "Không thể kết nối Gemini lúc này. Vui lòng thử lại sau.";
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

    private String buildEndpoint() {
        String normalizedBaseUrl = geminiBaseUrl.endsWith("/")
                ? geminiBaseUrl.substring(0, geminiBaseUrl.length() - 1)
                : geminiBaseUrl;
        String model = geminiModel == null || geminiModel.isBlank() ? "gemini-1.5-flash" : geminiModel.trim();
        String encodedKey = URLEncoder.encode(geminiApiKey.trim(), StandardCharsets.UTF_8);
        return normalizedBaseUrl + "/models/" + model + ":generateContent?key=" + encodedKey;
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
}
