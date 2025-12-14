package com.kensai.sandbox.lexio.web.controller;

import com.kensai.sandbox.lexio.ai.assistant.LexioAssistant;
import com.kensai.sandbox.lexio.web.dto.ChatRequest;
import com.kensai.sandbox.lexio.web.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final LexioAssistant lexioAssistant;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat message: {}", request.message());

        try {
            String response = lexioAssistant.chat(request.message());
            log.info("Assistant response: {}", response.length() > 200 ? response.substring(0, 200) + "..." : response);
            return ResponseEntity.ok(new ChatResponse(response, true, null));
        } catch (Exception e) {
            log.error("Error processing chat message", e);
            return ResponseEntity.ok(new ChatResponse(
                "I'm sorry, I encountered an error processing your message. Please try again.",
                false, e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Agent Lexio is running!");
    }
}
