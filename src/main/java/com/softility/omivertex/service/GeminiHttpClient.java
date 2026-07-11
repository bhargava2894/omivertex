package com.softility.omivertex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calls the Gemini generateContent REST API. Config-gated like
 * GoogleApiTokenVerifier: with no API key set it fails closed with a clear 400
 * instead of crashing or returning blank replies.
 */
@Component
public class GeminiHttpClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiHttpClient.class);
    // v1beta, deliberately: the stable v1 surface rejects systemInstruction
    // ("Unknown name") — verified against the live API 2026-07-11.
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final RestClient rest = RestClient.create();

    public GeminiHttpClient(@Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
                            @Value("${omivertex.assistant.gemini.model:gemini-3.1-flash-lite}") String model) {
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
        boolean first = true;
        for (Turn turn : history) {
            String text = turn.content();
            if (first && !"model".equals(turn.role())) {
                text = workforceContext + "\n\n" + text;
                first = false;
            }
            contents.add(Map.of("role", "model".equals(turn.role()) ? "model" : "user",
                    "parts", List.of(Map.of("text", text))));
        }
        String finalMessage = userMessage;
        if (first) {
            finalMessage = workforceContext + "\n\n" + finalMessage;
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", finalMessage))));

        Map<String, Object> body = Map.of("contents", contents);
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
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // surface the upstream error body in the log — "unavailable" alone is undebuggable
            log.warn("Gemini API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("The AI assistant is unavailable right now (upstream "
                    + e.getStatusCode().value() + ") — try again shortly");
        } catch (Exception e) {
            log.warn("Gemini API call failed", e);
            throw new BadRequestException("The AI assistant is unavailable right now — try again shortly");
        }
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy) {
        if (apiKey.isEmpty()) {
            throw new BadRequestException("AI resume parsing is not configured — "
                    + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
        }
        String skillList = taxonomy.stream()
                .map(o -> o.skillId() + ": " + o.name())
                .collect(Collectors.joining("\n"));
        String prompt = """
                You extract structured skill data from a resume.
                Match ONLY skills from this taxonomy (lines are "id: name"):
                %s

                Return STRICT JSON and nothing else:
                {"skills":[{"skillId":<id>,"proficiency":"NOVICE|FOUNDATIONAL|INTERMEDIATE|FUNCTIONAL_USER|ADVANCE|MASTERY","evidence":"<short quote or phrase from the resume>"}],
                 "experienceSummary":"<1-2 sentences: total years of experience and recent roles>"}

                Resume text:
                %s
                """.formatted(skillList, resumeText);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = rest.post()
                    .uri(ENDPOINT.formatted(model))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseExtraction(extractText(response), taxonomy);
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Gemini extraction returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("AI resume parsing is unavailable right now (upstream "
                    + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.warn("Gemini extraction call failed", e);
            throw new BadRequestException("AI resume parsing is unavailable right now");
        }
    }

    /** Maps the model's JSON to the contract; unknown skill ids are dropped, unknown proficiencies degrade to INTERMEDIATE. */
    static ResumeExtraction parseExtraction(String json, List<SkillOption> taxonomy) {
        Set<Long> validIds = taxonomy.stream().map(SkillOption::skillId).collect(Collectors.toSet());
        try {
            JsonNode root = MAPPER.readTree(json);
            List<ExtractedSkill> skills = new ArrayList<>();
            for (JsonNode s : root.path("skills")) {
                long id = s.path("skillId").asLong(-1);
                if (!validIds.contains(id)) {
                    continue;
                }
                Proficiency proficiency;
                try {
                    proficiency = Proficiency.valueOf(s.path("proficiency").asText(""));
                } catch (IllegalArgumentException e) {
                    proficiency = Proficiency.INTERMEDIATE;
                }
                skills.add(new ExtractedSkill(id, proficiency, s.path("evidence").asText("")));
            }
            return new ResumeExtraction(List.copyOf(skills), root.path("experienceSummary").asText(""));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("AI resume parsing returned an unexpected response");
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
