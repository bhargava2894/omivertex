package com.softility.omivertex.web;

import com.softility.omivertex.service.AiExecutor;
import com.softility.omivertex.service.AssistantService;
import com.softility.omivertex.web.dto.AssistantChatRequest;
import com.softility.omivertex.web.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final AiExecutor aiExecutor;

    public AssistantController(AssistantService assistantService, AiExecutor aiExecutor) {
        this.assistantService = assistantService;
        this.aiExecutor = aiExecutor;
    }

    /** Async on the AI bulkhead: the servlet thread is freed while Gemini responds. */
    @PostMapping("/chat")
    public CompletableFuture<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        return aiExecutor.submit(() -> assistantService.chat(request));
    }
}
