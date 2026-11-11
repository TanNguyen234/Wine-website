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

    @Value("${app.gemini.model:gemini-3.8-flash}")
    private String geminiModel;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${app.omni.enabled:true}")
    private boolean omniEnabled;

    @Value("${app.omni.base-url:https://omni-getway.vercel.app}")
    private String omniBaseUrl;

    @Value("${app.omni.api-key:}")
    private String omniApiKey;

    @Value("${app.omni.model:gemini-2.5-flash}")
    private String omniModel;

    public GeminiAssistantService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String ask(String prompt, String context) {
        String normalizedPrompt = normalizePrompt(prompt);
        String normalizedContext = normalizeContext(context);

        // 1. Prioritize Omni Route on Vercel if configured and enabled
        if (omniEnabled && omniBaseUrl != null && !omniBaseUrl.isBlank()) {
            try {
                String omniAnswer = sendOmniGatewayRequest(normalizedPrompt, normalizedContext);
                if (omniAnswer != null && !omniAnswer.isBlank()) {
                    return omniAnswer;
                }
            } catch (Exception ex) {
                logger.warn("Omni Vercel Gateway request attempt skipped or failed: {}. Continuing to direct Gemini.", ex.getMessage());
            }
        }

        // 2. Direct Google Gemini API call
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            try {
                String fullPrompt = buildPrompt(normalizedPrompt, normalizedContext);
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

                if (!result.isSuccess()) {
                    List<String> fallbacks = List.of("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-pro");
                    for (String fallback : fallbacks) {
                        if (!fallback.equalsIgnoreCase(primaryModel)) {
                            logger.info("Retry Gemini with fallback model {}. previousStatus={}", fallback, result.statusCode());
                            result = sendGeminiRequest(requestBody, fallback);
                            if (result.isSuccess()) {
                                break;
                            }
                        }
                    }
                }

                if (result.isSuccess()) {
                    return parseAssistantResponse(result.body());
                } else {
                    logger.error("!!! GEMINI API ERROR !!! Status: {}, Model: {}, Body: {}", 
                                 result.statusCode(), result.model(), abbreviateForLog(result.body()));
                }
            } catch (Exception ex) {
                logger.error("!!! GEMINI API CONNECTION FAILED !!! model={}, baseUrl={}, error={}", 
                             resolveModel(), resolveBaseUrl(), ex.getMessage());
            }
        } else {
            logger.warn("Gemini API key is missing or not configured. model={}, baseUrl={}", resolveModel(), resolveBaseUrl());
        }

        // 3. Fallback to intelligent local sommelier knowledge engine
        return buildLocalAdvice(normalizedPrompt, normalizedContext);
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

    private String sendOmniGatewayRequest(String prompt, String context) throws Exception {
        String endpoint = resolveOmniEndpoint();
        String systemInstruction = "Bạn là trợ lý chuyên gia gợi ý rượu vang (sommelier) cao cấp của StrongWine (Maison de Vins). " +
                "Tư vấn thanh lịch, chuyên nghiệp, súc tích bằng tiếng Việt, giải đáp về hương vị, cách kết hợp món ăn và các dòng vang có tại cửa hàng:\n" +
                getStoreKnowledge();

        String userContent = prompt;
        if (context != null && !context.isBlank()) {
            userContent = "[Ngữ cảnh: " + context + "] " + prompt;
        }

        Map<String, Object> payload = Map.of(
                "model", resolveOmniModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemInstruction),
                        Map.of("role", "user", "content", userContent)
                ),
                "temperature", 0.6,
                "max_tokens", 512
        );

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (omniApiKey != null && !omniApiKey.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + omniApiKey.trim());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String text = choices.get(0).path("message").path("content").asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } else {
            logger.warn("Omni Vercel Gateway returned status {}: {}", response.statusCode(), abbreviateForLog(response.body()));
        }
        return null;
    }

    private String resolveOmniEndpoint() {
        String base = (omniBaseUrl == null || omniBaseUrl.isBlank()) 
                ? "https://omni-getway.vercel.app" 
                : omniBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/chat/completions";
    }

    private String resolveOmniModel() {
        return (omniModel == null || omniModel.isBlank()) ? "gemini-2.5-flash" : omniModel.trim();
    }

    private String buildLocalAdvice(String prompt, String context) {
        if (prompt == null || prompt.isBlank()) {
            return "Tôi có thể giúp ích gì cho bạn trong việc chọn rượu hôm nay?";
        }

        String normalized = prompt.toLowerCase(Locale.ROOT).trim();

        // 1. Warm Greeting & Welcome
        if (normalized.equals("xin chào") || normalized.equals("chào") || normalized.equals("hello") || normalized.equals("hi")
                || normalized.startsWith("xin chào") || normalized.startsWith("chào bạn") || normalized.contains("chào")) {
            StringBuilder sb = new StringBuilder("Xin chào quý khách! Tôi là trợ lý Sommelier ảo của StrongWine.\n\n");
            sb.append("Rất hân hạnh được đồng hành cùng quý khách chọn lựa chai vang tinh tuyển. Bạn đang tìm vang cho mục đích nào:\n");
            sb.append("• Kết hợp món ăn (Bít tết bò, Hải sản tươi sống, Đồ nướng BBQ...)\n");
            sb.append("• Dịp đặc biệt (Tiệc gia đình, Kỷ niệm, Biếu tặng đối tác sang trọng)\n");
            sb.append("• Ngân sách mong muốn (Dưới 1 triệu, 1-3 triệu, hoặc dòng Grand Cru thượng hạng)\n\n");
            sb.append("Gợi ý nhanh một số chai tiêu biểu đang sẵn có tại hầm rượu StrongWine:\n");
            try {
                List<Wine> topWines = wineRepository.findByDeletedFalse().stream().limit(3).toList();
                for (Wine w : topWines) {
                    sb.append(String.format("- %s (Dòng %s, %s, %,.0f ₫)\n", w.getName(), w.getType(), w.getCountry(), w.getPrice()));
                }
            } catch (Exception ignored) {}
            sb.append("\nHãy cho tôi biết thêm thông tin về khẩu vị hoặc món ăn bạn dự định thưởng thức nhé!");
            return sb.toString();
        }

        try {
            List<Wine> allWines = wineRepository.findByDeletedFalse();

            // 2. Food pairing: Beef / Steak / Red Meat / BBQ
            if (normalized.contains("bít tết") || normalized.contains("bit tet") || normalized.contains("steak")
                    || normalized.contains("bò") || normalized.contains("thịt đỏ") || normalized.contains("nướng") || normalized.contains("bbq")) {
                List<Wine> redMatches = allWines.stream()
                        .filter(w -> w.getType() != null && (w.getType().equalsIgnoreCase("Red") || w.getType().toLowerCase().contains("đỏ")))
                        .limit(3)
                        .toList();
                StringBuilder sb = new StringBuilder("Với món bít tết hoặc thịt đỏ đậm đà, StrongWine khuyên bạn nên kết hợp cùng dòng vang đỏ (Red Wine) có cấu trúc tannin tốt để làm dậy vị ngọt mềm của thịt.\n\nGợi ý các chai lý tưởng tại hầm:\n");
                for (Wine w : redMatches) {
                    sb.append(String.format("- %s (%s, %,.0f ₫)\n", w.getName(), w.getCountry(), w.getPrice()));
                }
                sb.append("\nNhiệt độ phục vụ lý tưởng: 16-18°C. Bạn nên cho rượu thở trong bình decanter khoảng 20-30 phút trước khi thưởng thức.");
                return sb.toString();
            }

            // 3. Food pairing: Seafood / Fish / Shrimp / Oyster
            if (normalized.contains("hải sản") || normalized.contains("hai san") || normalized.contains("cá")
                    || normalized.contains("tôm") || normalized.contains("hàu") || normalized.contains("sashimi") || normalized.contains("salad")) {
                List<Wine> whiteMatches = allWines.stream()
                        .filter(w -> w.getType() != null && (w.getType().equalsIgnoreCase("White") || w.getType().toLowerCase().contains("trắng")))
                        .limit(3)
                        .toList();
                StringBuilder sb = new StringBuilder("Với món hải sản, tôm, hàu hoặc cá tươi ngon, các dòng vang trắng (White Wine) thanh khiết với vị chua thanh sảng khoái và hương cam chanh sẽ là sự kết hợp hoàn hảo.\n\nGợi ý vang trắng tinh tuyển:\n");
                for (Wine w : whiteMatches) {
                    sb.append(String.format("- %s (%s, %,.0f ₫)\n", w.getName(), w.getCountry(), w.getPrice()));
                }
                sb.append("\nNhiệt độ phục vụ lý tưởng: Ướp lạnh 8-10°C để cảm nhận trọn vẹn vị tươi mát.");
                return sb.toString();
            }

            // 4. Party / Celebration / Sparkling
            if (normalized.contains("tiệc") || normalized.contains("kỷ niệm") || normalized.contains("khai vị")
                    || normalized.contains("sinh nhật") || normalized.contains("chúc mừng") || normalized.contains("sủi") || normalized.contains("champagne")) {
                List<Wine> sparkMatches = allWines.stream()
                        .filter(w -> w.getType() != null && (w.getType().equalsIgnoreCase("Sparkling") || w.getType().toLowerCase().contains("sủi")))
                        .limit(3)
                        .toList();
                StringBuilder sb = new StringBuilder("Cho các buổi tiệc chúc mừng hoặc dịp kỷ niệm đáng nhớ, các dòng vang sủi (Sparkling Wine / Champagne) với nốt bọt mịn màng sẽ mang lại không khí hân hoan:\n\n");
                for (Wine w : sparkMatches) {
                    sb.append(String.format("- %s (%s, %,.0f ₫)\n", w.getName(), w.getCountry(), w.getPrice()));
                }
                sb.append("\nNhiệt độ phục vụ lý tưởng: Ướp lạnh thật sâu ở 6-8°C.");
                return sb.toString();
            }

            // 5. Budget: Under 2 million / Under 1 million
            if (normalized.contains("dưới 2 triệu") || normalized.contains("duoi 2 trieu") || normalized.contains("2 triệu")
                    || normalized.contains("dưới 1 triệu") || normalized.contains("duoi 1 trieu") || normalized.contains("ngân sách")) {
                double budgetLimit = (normalized.contains("1 triệu") || normalized.contains("1 trieu")) ? 1_000_000.0 : 2_000_000.0;
                List<Wine> budgetMatches = allWines.stream()
                        .filter(w -> w.getPrice() != null && w.getPrice().doubleValue() <= budgetLimit)
                        .sorted(java.util.Comparator.comparing(Wine::getPrice).reversed())
                        .limit(3)
                        .toList();
                StringBuilder sb = new StringBuilder(String.format("Các dòng vang hảo hạng với mức giá hợp lý dưới %,.0f ₫ tại StrongWine:\n\n", budgetLimit));
                for (Wine w : budgetMatches) {
                    sb.append(String.format("- %s (Dòng %s, %s, %,.0f ₫)\n", w.getName(), w.getType(), w.getCountry(), w.getPrice()));
                }
                sb.append("\nBạn có muốn xem thêm chi tiết hoặc đặt giao nhanh tận tay kèm mã OTP cho chai nào không?");
                return sb.toString();
            }

            // 6. Most expensive / Luxury
            if (normalized.contains("mắc nhất") || normalized.contains("đắt nhất") || normalized.contains("cao nhất") || normalized.contains("quà tặng") || normalized.contains("biếu tặng")) {
                return allWines.stream()
                        .max(java.util.Comparator.comparing(Wine::getPrice))
                        .map(w -> String.format("Dòng vang đẳng cấp và danh giá nhất tại StrongWine hiện tại là %s với giá %,.0f ₫. Đây là tuyệt tác dòng %s thượng hạng từ %s, cực kỳ thích hợp làm quà biếu tặng đối tác cấp cao.", 
                                w.getName(), w.getPrice(), w.getType(), w.getCountry()))
                        .orElse("Danh mục sản phẩm đang được cập nhật.");
            }

            // 7. Cheapest / Everyday wine
            if (normalized.contains("rẻ nhất") || normalized.contains("thấp nhất") || normalized.contains("bình dân")) {
                return allWines.stream()
                        .min(java.util.Comparator.comparing(Wine::getPrice))
                        .map(w -> String.format("Chai rượu có giá dễ tiếp cận nhất hiện nay là %s với giá chỉ %,.0f ₫. Hương vị thơm ngon, cân bằng và rất phù hợp để thưởng thức cùng bữa ăn hàng ngày.", 
                                w.getName(), w.getPrice()))
                        .orElse("Danh mục sản phẩm đang được cập nhật.");
            }

            // 8. Keyword matching (Wine Name, Country or Type)
            List<Wine> matches = allWines.stream()
                    .filter(w -> normalized.contains(w.getName().toLowerCase()) || 
                                 normalized.contains(w.getType().toLowerCase()) ||
                                 (w.getCountry() != null && normalized.contains(w.getCountry().toLowerCase())))
                    .limit(3)
                    .collect(Collectors.toList());

            if (!matches.isEmpty()) {
                StringBuilder sb = new StringBuilder("StrongWine xin gợi ý một số chai rượu vang phù hợp với yêu cầu của bạn:\n\n");
                for (Wine w : matches) {
                    sb.append(String.format("- %s (Dòng %s, %s, %,.0f ₫)\n", w.getName(), w.getType(), w.getCountry(), w.getPrice()));
                }
                sb.append("\nBạn có muốn biết thêm về cách kết hợp món ăn hoặc đặt giao tận nơi cho chai nào không?");
                return sb.toString();
            }
        } catch (Exception e) {
            logger.error("Error in local advice matching", e);
        }

        // Polite Sommelier General Fallback
        StringBuilder advice = new StringBuilder("StrongWine luôn sẵn lòng hỗ trợ bạn tìm kiếm chai vang ưng ý nhất!\n\n");
        advice.append("Gợi ý nhanh theo nhu cầu phổ biến:\n");
        advice.append("• Dùng kèm bít tết / thịt đỏ: Chọn Vang Đỏ Cabernet Sauvignon (nhiệt độ 16-18°C)\n");
        advice.append("• Dùng kèm hải sản tươi sống: Chọn Vang Trắng Sauvignon Blanc (ướp lạnh 8-10°C)\n");
        advice.append("• Tiệc kỷ niệm, liên hoan: Chọn Vang Sủi / Champagne (ướp lạnh 6-8°C)\n\n");
        advice.append("Hãy cho tôi biết cụ thể món ăn đi kèm hoặc mức ngân sách bạn mong muốn để tôi tư vấn chuẩn xác nhất nhé!");
        return advice.toString();
    }

    private record GeminiHttpResult(int statusCode, String body, String model) {
        private boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
