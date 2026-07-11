package com.softility.omivertex.service;

import com.softility.omivertex.web.dto.AssistantChatRequest;
import com.softility.omivertex.web.dto.AssistantChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Orchestrates a dashboard-assistant turn: live context + capped history -> Gemini. */
@Service
@Transactional(readOnly = true)
public class AssistantService {

    /** Prior turns sent to the model; older ones are dropped. */
    static final int MAX_HISTORY_TURNS = 20;

    private final AssistantContextBuilder contextBuilder;
    private final GeminiClient geminiClient;

    public AssistantService(AssistantContextBuilder contextBuilder, GeminiClient geminiClient) {
        this.contextBuilder = contextBuilder;
        this.geminiClient = geminiClient;
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        List<AssistantChatRequest.HistoryTurn> history =
                request.history() == null ? List.of() : request.history();
        List<GeminiClient.Turn> turns = history.stream()
                .skip(Math.max(0, history.size() - MAX_HISTORY_TURNS))
                .map(t -> new GeminiClient.Turn(t.role(), t.content()))
                .toList();
        String reply = geminiClient.reply(contextBuilder.build(), turns, request.message());
        return new AssistantChatResponse(reply);
    }
}
