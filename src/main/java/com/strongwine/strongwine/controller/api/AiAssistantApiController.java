package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.AiAssistantRequest;
import com.strongwine.strongwine.service.GeminiAssistantService;
import com.strongwine.strongwine.service.WineService;
import com.strongwine.strongwine.entity.Wine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AiAssistantApiController {

    private final GeminiAssistantService geminiAssistantService;
    private final WineService wineService;

    public AiAssistantApiController(GeminiAssistantService geminiAssistantService, WineService wineService) {
        this.geminiAssistantService = geminiAssistantService;
        this.wineService = wineService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody AiAssistantRequest request, jakarta.servlet.http.HttpSession session) {
        String prompt = request == null ? null : request.getPrompt();
        String context = request == null ? null : request.getContext();
        
        // Append wine context
        StringBuilder wineContextBuilder = new StringBuilder(context == null ? "" : context + " | ");
        wineContextBuilder.append("Thông tin danh sách rượu tại cửa hàng (ID, Tên, Giá VND, Loại): ");
        List<Wine> allWines = wineService.getAllWines();
        if (allWines != null) {
            for (Wine w : allWines) {
                wineContextBuilder.append(String.format("[%d] %s - %s VND (%s); ", w.getId(), w.getName(), w.getPrice(), w.getType()));
            }
        }
        context = wineContextBuilder.toString();

        // 1. Get History from Session
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) session.getAttribute("CHAT_HISTORY");
        if (history == null) {
            history = new java.util.ArrayList<>();
        }

        // 2. Call Service with History
        String answer = geminiAssistantService.ask(prompt, context, history);
        
        // Safety check for empty answers
        if (answer == null || answer.trim().isEmpty()) {
            answer = "Mình đang gặp chút gián đoạn khi kết nối với máy chủ AI. Bạn vui lòng thử lại sau vài giây nhé.";
        }

        // 3. Update History (Limit to 10 messages to avoid token bloat)
        if (prompt != null && !prompt.isBlank()) {
            history.add(Map.of("role", "user", "content", prompt));
            history.add(Map.of("role", "model", "content", answer));
            
            if (history.size() > 10) {
                history = new java.util.ArrayList<>(history.subList(history.size() - 10, history.size()));
            }
            
            session.setAttribute("CHAT_HISTORY", new java.util.ArrayList<>(history));
        }

        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
