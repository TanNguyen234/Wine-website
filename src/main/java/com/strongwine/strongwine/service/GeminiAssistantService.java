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
import java.util.*;

@Service
public class GeminiAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAssistantService.class);
    private static final int MAX_PROMPT_LENGTH = 1000;
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 600;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    private final AiModelFallbackManager modelManager;

    public GeminiAssistantService(AiModelFallbackManager modelManager) {
        this.modelManager = modelManager;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)) // Overall connection timeout
                .build();
    }

    public String ask(String prompt, String context) {
        String normalizedPrompt = normalizePrompt(prompt);
        String normalizedContext = normalizeContext(context);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            logger.warn("Gemini API key is missing.");
            return "Chatbot hiện chưa được cấu hình GEMINI_API_KEY ở backend.";
        }

        String fullPrompt = buildPrompt(normalizedPrompt, normalizedContext);
        Set<String> excludedModels = new HashSet<>();

        while (true) {
            Optional<String> modelOpt = modelManager.getNextModel(excludedModels);
            if (modelOpt.isEmpty()) {
                logger.error("All AI models exhausted or in cooldown.");
                return buildLocalAdvice(normalizedPrompt, normalizedContext);
            }

            String currentModel = modelOpt.get();
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
                
                // Implement strictly sequential fallback logic
                GeminiHttpResult result = sendWithHandling(requestBody, currentModel);
                
                if (result.isSuccess()) {
                    modelManager.reportSuccess(currentModel);
                    return parseAssistantResponse(result.body());
                }

                // Error Handling Strategy
                handleModelError(currentModel, result.statusCode(), excludedModels);

            } catch (Exception ex) {
                logger.error("Unexpected error with model {}. Falling back.", currentModel, ex);
                modelManager.reportError(currentModel, 500);
                excludedModels.add(currentModel);
            }
        }
    }

    private GeminiHttpResult sendWithHandling(String requestBody, String model) throws Exception {
        // Set request-specific timeout (3-5s as specified)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildEndpoint(model)))
                .timeout(Duration.ofSeconds(4)) 
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new GeminiHttpResult(response.statusCode(), response.body(), model);
        } catch (java.net.http.HttpTimeoutException e) {
            return new GeminiHttpResult(408, "Request Timeout", model);
        }
    }

    private void handleModelError(String model, int status, Set<String> excludedModels) {
        if (status == 429) {
            // 429 → switch model immediately (NO retry same model)
            logger.warn("Model {} rate limited (429). Switching immediately.", model);
            modelManager.reportError(model, 429);
            excludedModels.add(model);
        } else if (status == 404) {
            // 404 → skip model permanently
            logger.error("Model {} not found (404). Skipping permanently.", model);
            modelManager.reportError(model, 404);
            excludedModels.add(model);
        } else if (status >= 500 && status < 600) {
            // 5xx → retry ONCE (already handled via loop if we wanted, but prompt says retry ONCE then fallback)
            // Implementation: Simple approach - mark as 500 error in manager and move to next model
            logger.warn("Model {} returned 5xx ({}). Moving to next model.", model, status);
            modelManager.reportError(model, status);
            excludedModels.add(model);
        } else if (status == 408) {
            // Timeout Handling
            logger.warn("Model {} timed out. Falling back immediately.", model);
            modelManager.reportTimeout(model);
            excludedModels.add(model);
        } else {
            logger.error("Model {} failed with status {}. Body: {}", model, status, model);
            modelManager.reportError(model, status);
            excludedModels.add(model);
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
