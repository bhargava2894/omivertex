package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini generateContent REST API. Config-gated like
 * GoogleApiTokenVerifier: with no API key set it fails closed with a clear 400
 * instead of crashing or returning blank replies.
 */
@Component
public class GeminiHttpClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiHttpClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final String apiKey;
    private final String model;
    private final RestClient rest = RestClient.create();

    public GeminiHttpClient(@Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
                            @Value("${omivertex.assistant.gemini.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        if (this.apiKey.isEmpty()) {
            log.warn("omivertex.assistant.gemini.api-key is not set — the AI assistant is disabled "
                    + "(the endpoint will return 400). Set it to enable the dashboard assistant.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String reply(String workforceContext, List<Turn> history, String userMessage) {
        if (apiKey.isEmpty()) {
            throw new BadRequestException("The AI assistant is not configured — "
                    + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
        }
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Turn turn : history) {
            contents.add(Map.of("role", "model".equals(turn.role()) ? "model" : "user",
                    "parts", List.of(Map.of("text", turn.content()))));
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))));
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", workforceContext))),
                "contents", contents);
        try {
            Map<String, Object> response = rest.post()
                    .uri(ENDPOINT.formatted(model))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return extractText(response);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini API call failed: {}", e.getMessage());
            throw new BadRequestException("The AI assistant is unavailable right now — try again shortly");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (RuntimeException e) {
            throw new BadRequestException("The AI assistant returned an unexpected response — try again");
        }
    }
}
