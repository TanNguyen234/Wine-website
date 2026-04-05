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
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;

@Service
public class GeminiAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAssistantService.class);
    private static final int MAX_PROMPT_LENGTH = 1000;
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 600;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    private WineRepository wineRepository;

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
                logger.error("!!! GEMINI API ERROR !!! Status: {}, Model: {}, Body: {}", 
                             result.statusCode(), result.model(), abbreviateForLog(result.body()));
                return buildLocalAdvice(normalizedPrompt, normalizedContext);
            }

            return parseAssistantResponse(result.body());
        } catch (Exception ex) {
            logger.error("!!! GEMINI API CONNECTION FAILED !!! model={}, baseUrl={}, error={}", 
                         resolveModel(), resolveBaseUrl(), ex.getMessage());
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
        builder.append("Bạn là trợ lý chuyên gia gợi ý rượu vang (sommelier) chuyên trách của cửa hàng StrongWine. ")
                .append("Nhiệm vụ của bạn là tư vấn khách hàng chọn rượu, kết hợp món ăn (wine pairing) và giải đáp thắc mắc về dịch vụ của website.\n\n")
                .append("QUY TẮC QUAN TRỌNG:\n")
                .append("1. Chỉ trả lời các câu hỏi liên quan đến rượu vang, đồ uống, kết hợp ẩm thực và quy trình mua hàng tại StrongWine.\n")
                .append("2. Nếu câu hỏi KHÔNG LIÊN QUAN (như tin tức, chính trị, toán học, lập trình, hoặc các chủ đề chung chung không liên quan đến rượu), ")
                .append("hãy trả lời chính xác như sau: 'Rất tiếc, tôi là trợ lý ảo chuyên trách về rượu vang của StrongWine nên không thể hỗ trợ các chủ đề ngoài phạm vi này. Quý khách có muốn tôi gợi ý một chai vang phù hợp không?'\n")
                .append("3. Trả lời bằng tiếng Việt, phong cách lịch sự, chuyên nghiệp, sử dụng 'StrongWine' để xưng hô thay vì 'tôi'.\n")
                .append("4. Sử dụng dữ liệu thực tế dưới đây để tư vấn cụ thể:\n")
                .append(getStoreKnowledge())
                .append("\n5. Không được tự bịa ra thông tin sản phẩm không có trong danh sách trên.");

        if (!context.isEmpty()) {
            builder.append("\n\nNgữ cảnh trang hiện tại của khách hàng: ").append(context);
        }

        builder.append("\n\nCâu hỏi từ khách hàng: ").append(prompt);
        return builder.toString();
    }

    private String getStoreKnowledge() {
        try {
            List<Wine> wines = wineRepository.findByDeletedFalse();
            if (wines == null || wines.isEmpty()) {
                return "Hiện tại cửa hàng đang cập nhật danh mục sản phẩm mới.";
            }

            return "DANH MỤC SẢN PHẨM HIỆN CÓ TẠI STRONGWINE:\n" +
                    wines.stream().limit(15).map(w ->
                        String.format("- %s: Loại %s, Xuất xứ %s, Giá %,.0f đ. Mô tả: %s",
                                w.getName(), w.getType(), w.getCountry(), w.getPrice(),
                                w.getDescription() != null ? w.getDescription() : "N/A")
                    ).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            logger.error("Error creating store knowledge summary", e);
            return "Thông tin sản phẩm đang được cập nhật.";
        }
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
        if (prompt == null || prompt.isBlank()) return "Tôi có thể giúp ích gì cho bạn trong việc chọn rượu hôm nay?";
        
        String normalized = prompt.toLowerCase(Locale.ROOT);
        
        try {
            List<Wine> allWines = wineRepository.findByDeletedFalse();
            
            // 1. Expensive query
            if (normalized.contains("mắc nhất") || normalized.contains("đắt nhất") || normalized.contains("cao nhất")) {
                 return allWines.stream()
                        .max(java.util.Comparator.comparing(Wine::getPrice))
                        .map(w -> String.format("Hiện tại StrongWine có chai %s là đắt nhất với giá %,.0f đ. Đây là dòng %s thượng hạng từ %s.", 
                                w.getName(), w.getPrice(), w.getType(), w.getCountry()))
                        .orElse("Danh mục sản phẩm đang được cập nhật.");
            }
            
            // 2. Cheapest query
            if (normalized.contains("rẻ nhất") || normalized.contains("thấp nhất") || normalized.contains("bình dân")) {
                return allWines.stream()
                        .min(java.util.Comparator.comparing(Wine::getPrice))
                        .map(w -> String.format("Chai rượu có giá bình dân nhất hiện nay là %s với giá chỉ %,.0f đ. Rất phù hợp để thưởng thức hàng ngày.", 
                                w.getName(), w.getPrice()))
                        .orElse("Danh mục sản phẩm đang được cập nhật.");
            }

            // 3. Keyword matching (Country or Type)
            List<Wine> matches = allWines.stream()
                .filter(w -> normalized.contains(w.getName().toLowerCase()) || 
                             normalized.contains(w.getType().toLowerCase()) ||
                             (w.getCountry() != null && normalized.contains(w.getCountry().toLowerCase())))
                .limit(3)
                .collect(Collectors.toList());
            
            if (!matches.isEmpty()) {
                StringBuilder sb = new StringBuilder("StrongWine gợi ý một số chai phù hợp với yêu cầu của bạn:\n");
                for (Wine w : matches) {
                    sb.append("- ").append(w.getName()).append(" (")
                      .append(w.getCountry()).append(", ").append(String.format("%,.0f", w.getPrice())).append(" đ)\n");
                }
                sb.append("Bạn có muốn tìm hiểu thêm về chai nào trong số này không?");
                return sb.toString();
            }
        } catch (Exception e) {
            logger.error("Error in local advice matching", e);
        }

        // Default fallback if no match
        StringBuilder advice = new StringBuilder("Dịch vụ tư vấn AI của StrongWine hiện đang bảo trì nhẹ. ");
        if (normalized.contains("hải sản") || normalized.contains("ca") || normalized.contains("tôm")) {
            advice.append("Gợi ý nhanh: Bạn nên chọn vang trắng Sauvignon Blanc hoặc Chardonnay nhẹ, ướp lạnh khoảng 8-10°C.");
        } else if (normalized.contains("bò") || normalized.contains("thịt đỏ") || normalized.contains("nướng")) {
            advice.append("Gợi ý nhanh: Bạn nên chọn vang đỏ Cabernet Sauvignon hoặc Pinot Noir, phục vụ ở 16-18°C.");
        } else {
            advice.append("Mời bạn cho biết thêm thông tin về món ăn đi kèm hoặc ngân sách để tôi tư vấn chính xác hơn bằng dữ liệu hiện có.");
        }
        return advice.toString();
    }

    private record GeminiHttpResult(int statusCode, String body, String model) {
        private boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
