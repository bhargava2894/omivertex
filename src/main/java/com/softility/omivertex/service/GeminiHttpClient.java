package com.softility.omivertex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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
    private final RestClient rest;

    public GeminiHttpClient(
            @Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
            @Value("${omivertex.assistant.gemini.model:gemini-3.1-flash-lite}") String model,
            @Value("${omivertex.assistant.gemini.connect-timeout:5s}") Duration connectTimeout,
            @Value("${omivertex.assistant.gemini.read-timeout:30s}") Duration readTimeout) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        // Bounded I/O: a hung upstream call must never hold an ai-* thread forever.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());
        this.rest = RestClient.builder().requestFactory(requestFactory).build();
        if (this.apiKey.isEmpty()) {
            log.warn("omivertex.assistant.gemini.api-key is not set — the AI assistant is disabled "
                    + "(the endpoint will return 400). Set it to enable the dashboard assistant.");
        }
    }

    /** Max read-tool round-trips per turn before we force a final answer. */
    static final int MAX_TOOL_ROUNDS = 3;
    static final String READ_TOOL_MATCHES = "get_position_matches";

    /** Tools executed server-side and fed back to the model (write tools return as drafts). */
    static final Set<String> READ_TOOLS = Set.of(READ_TOOL_MATCHES,
            "search_associates", "get_associate_detail", "list_rolloffs", "list_open_positions");

    /** The three assistant tools. Write tools are drafts only — the server never executes them. */
    private static final List<Map<String, Object>> FUNCTION_DECLARATIONS = List.of(
            Map.of("name", "propose_allocation",
                    "description", "Draft assigning an associate to a project for the user to confirm."
                            + " Use when asked to allocate, assign, or staff someone on a project.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "associateName", Map.of("type", "string"),
                                    "projectName", Map.of("type", "string"),
                                    "percent", Map.of("type", "integer",
                                            "description", "allocation percent 1-100; default 100"),
                                    "billable", Map.of("type", "boolean", "description", "default true"),
                                    "startDate", Map.of("type", "string",
                                            "description", "ISO date; default today"),
                                    "endDate", Map.of("type", "string", "description", "ISO date; optional")),
                            "required", List.of("associateName", "projectName"))),
            Map.of("name", "propose_position_fill",
                    "description", "Draft filling an open position with an associate for the user to confirm."
                            + " Use when asked to fill a seat or staff an open position.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "positionTitle", Map.of("type", "string"),
                                    "associateName", Map.of("type", "string")),
                            "required", List.of("positionTitle", "associateName"))),
            Map.of("name", READ_TOOL_MATCHES,
                    "description", "Rank candidates for an open position by skill and bench status."
                            + " Use when asked who matches or could fill a position.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("positionTitle", Map.of("type", "string")),
                            "required", List.of("positionTitle"))),
            Map.of("name", "search_associates",
                    "description", "Look up associates by name, skill, proficiency, or bench status."
                            + " Always use this before answering questions about specific people or"
                            + " who has a skill / is available.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "name", Map.of("type", "string",
                                            "description", "partial or full name"),
                                    "skill", Map.of("type", "string",
                                            "description", "skill name to filter by"),
                                    "minProficiency", Map.of("type", "string",
                                            "description", "NOVICE, FOUNDATIONAL, INTERMEDIATE,"
                                                    + " FUNCTIONAL_USER, ADVANCE or MASTERY"),
                                    "benchOnly", Map.of("type", "boolean",
                                            "description", "true = only unallocated associates")))),
            Map.of("name", "get_associate_detail",
                    "description", "Full profile of one associate: skills with proficiency, current"
                            + " allocations, bench days, upcoming exit.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("name", Map.of("type", "string")),
                            "required", List.of("name"))),
            Map.of("name", "list_rolloffs",
                    "description", "Current allocations ending soon — who rolls off which project"
                            + " and when.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("withinDays", Map.of("type", "integer",
                                    "description", "look-ahead window in days; default 30")))),
            Map.of("name", "list_open_positions",
                    "description", "All open positions with project, client, and required skills.",
                    "parameters", Map.of("type", "object", "properties", Map.of())));

    @Override
    public AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                         String userMessage, ToolExecutor tools) {
        if (apiKey.isEmpty()) {
            throw new BadRequestException("The AI assistant is not configured — "
                    + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
        }
        List<Map<String, Object>> contents = buildContents(workforceContext, history, userMessage);
        for (int round = 0; ; round++) {
            Map<String, Object> response = callApi(Map.of(
                    "contents", contents,
                    "tools", List.of(Map.of("functionDeclarations", FUNCTION_DECLARATIONS))));
            Map<String, Object> functionCall = firstFunctionCall(response);
            if (functionCall == null) {
                return new AssistantReply(textOrEmpty(response), null);
            }
            String name = String.valueOf(functionCall.get("name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> args = functionCall.get("args") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            if (READ_TOOLS.contains(name) && tools != null && round < MAX_TOOL_ROUNDS) {
                String result = tools.execute(name, args);
                contents.add(Map.of("role", "model",
                        "parts", List.of(Map.of("functionCall", functionCall))));
                contents.add(Map.of("role", "user",
                        "parts", List.of(Map.of("functionResponse",
                                Map.of("name", name, "response", Map.of("result", result))))));
                continue;
            }
            return new AssistantReply(textOrEmpty(response), new ActionCall(name, args));
        }
    }

    private List<Map<String, Object>> buildContents(String workforceContext, List<Turn> history,
                                                    String userMessage) {
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
        return contents;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callApi(Map<String, Object> body) {
        try {
            return rest.post()
                    .uri(ENDPOINT.formatted(model))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstCandidateParts(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            return (List<Map<String, Object>>) content.get("parts");
        } catch (RuntimeException e) {
            throw new BadRequestException("The AI assistant returned an unexpected response — try again");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstFunctionCall(Map<String, Object> response) {
        for (Map<String, Object> part : firstCandidateParts(response)) {
            if (part.get("functionCall") instanceof Map<?, ?> call) {
                return (Map<String, Object>) call;
            }
        }
        return null;
    }

    private String textOrEmpty(Map<String, Object> response) {
        return firstCandidateParts(response).stream()
                .map(p -> p.get("text"))
                .filter(t -> t instanceof String)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
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
