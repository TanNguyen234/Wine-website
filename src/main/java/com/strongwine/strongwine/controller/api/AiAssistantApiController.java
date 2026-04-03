package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.AiAssistantRequest;
import com.strongwine.strongwine.service.GeminiAssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AiAssistantApiController {

    private final GeminiAssistantService geminiAssistantService;

    public AiAssistantApiController(GeminiAssistantService geminiAssistantService) {
        this.geminiAssistantService = geminiAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody AiAssistantRequest request) {
        String prompt = request == null ? null : request.getPrompt();
        String context = request == null ? null : request.getContext();
        String answer = geminiAssistantService.ask(prompt, context);
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
