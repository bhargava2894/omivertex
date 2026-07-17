package com.softility.omivertex.web;

import com.softility.omivertex.service.AiExecutor;
import com.softility.omivertex.service.AssistantService;
import com.softility.omivertex.service.AuditService;
import com.softility.omivertex.web.dto.AssistantChatRequest;
import com.softility.omivertex.web.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    /** SSE stream lifetime cap: the emitter completes when the reply (or error) event is sent. */
    static final long STREAM_TIMEOUT_MS = 60_000;

    private final AssistantService assistantService;
    private final AiExecutor aiExecutor;

    public AssistantController(AssistantService assistantService, AiExecutor aiExecutor) {
        this.assistantService = assistantService;
        this.aiExecutor = aiExecutor;
    }

    /** Role + username, resolved on the servlet thread — the ai-* pool never sees the SecurityContext. */
    private static AssistantService.Caller currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new AssistantService.Caller(AuditService.currentUsername(), admin);
    }

    /** Async on the AI bulkhead: the servlet thread is freed while Gemini responds. */
    @PostMapping("/chat")
    public CompletableFuture<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        // Resolved here, on the servlet thread: the ai-* pool never sees the SecurityContext.
        AssistantService.Caller caller = currentCaller();
        return aiExecutor.submit(() -> assistantService.chat(request, caller));
    }

    /**
     * Streaming variant of {@link #chat}: {@code tool} events as lookups run, then one
     * {@code reply} event carrying the exact JSON the plain endpoint returns. Failures
     * surface as an {@code error} event with the user-facing message. Same bulkhead,
     * same service body, same interaction log — only the transport differs.
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Valid @RequestBody AssistantChatRequest request) {
        // Resolved here, on the servlet thread: the ai-* pool never sees the SecurityContext.
        AssistantService.Caller caller = currentCaller();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        aiExecutor.submit(() -> {
            try {
                AssistantChatResponse response = assistantService.chat(request, caller,
                        name -> send(emitter, "tool", name));
                send(emitter, "reply", response);
            } catch (RuntimeException e) {
                trySend(emitter, "error", e.getMessage() == null
                        ? "The AI assistant hit an error — try again." : e.getMessage());
            }
            emitter.complete();
            return null;
        });
        return emitter;
    }

    /** A failed send means the client went away — abandon the turn quietly. */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("SSE client disconnected", e);
        }
    }

    /** Best-effort send for the error path — the client may already be gone. */
    private void trySend(SseEmitter emitter, String event, Object data) {
        try {
            send(emitter, event, data);
        } catch (IllegalStateException ignored) {
            // nothing left to tell a departed client
        }
    }
}
