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
    private static final String GENERATE_PATH = "/v1beta/models/%s:generateContent";

    private final String endpoint;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final RestClient rest;

    public GeminiHttpClient(
            @Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
            @Value("${omivertex.assistant.gemini.model:gemini-3.1-flash-lite}") String model,
            @Value("${omivertex.assistant.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${omivertex.assistant.gemini.connect-timeout:5s}") Duration connectTimeout,
            @Value("${omivertex.assistant.gemini.read-timeout:30s}") Duration readTimeout) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        // Base URL is overridable for tests; the default is byte-identical to before.
        // Format the path constant only, then concatenate — a base URL containing '%'
        // must never pass through String.formatted.
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = base + GENERATE_PATH.formatted(model);
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
    /** Fed to the model as the last tool result once the round cap is hit, so it wraps up. */
    static final String TOOL_BUDGET_EXHAUSTED_RESULT =
            "Tool budget for this turn is exhausted. Do not request more tools — answer the user"
                    + " now from the information already gathered, and say what you could not check.";
    /** Returned when even the wrap-up call produces no prose. */
    static final String TOOL_BUDGET_FALLBACK_TEXT =
            "That question needs more lookups than I can run in one turn — try narrowing it,"
                    + " for example to one position, person, or project.";
    static final String READ_TOOL_MATCHES = "get_position_matches";

    /** Tools executed server-side and fed back to the model (write tools return as drafts). */
    static final Set<String> READ_TOOLS = Set.of(READ_TOOL_MATCHES,
            "search_associates", "get_associate_detail", "get_project_detail",
            "list_rolloffs", "list_open_positions", "list_clients", "list_projects",
            "get_skill_gaps", "list_expiring_certifications", "get_workforce_summary",
            "list_bench_aging", "get_position_match_summary");

    /** All assistant tools. Write tools are drafts only — the server never executes them. */
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
            Map.of("name", "propose_end_allocation",
                    "description", "Draft ending an associate's CURRENT allocation on a project, for"
                            + " the user to confirm. Use when asked to roll someone off, release,"
                            + " or end an engagement.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "associateName", Map.of("type", "string"),
                                    "projectName", Map.of("type", "string"),
                                    "endDate", Map.of("type", "string",
                                            "description", "ISO date; default today")),
                            "required", List.of("associateName", "projectName"))),
            Map.of("name", "propose_edit_allocation",
                    "description", "Draft modifying an associate's CURRENT allocation (change percent, "
                            + "billability, or end date) on a project, for the user to confirm. Use when "
                            + "asked to change allocation rate, make billable/non-billable, or adjust end date.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "associateName", Map.of("type", "string"),
                                    "projectName", Map.of("type", "string"),
                                    "percent", Map.of("type", "integer",
                                            "description", "new allocation percent 1-100"),
                                    "billable", Map.of("type", "boolean"),
                                    "endDate", Map.of("type", "string",
                                            "description", "ISO date; omit/null to keep open-ended")),
                            "required", List.of("associateName", "projectName"))),
            Map.of("name", READ_TOOL_MATCHES,
                    "description", "Rank candidates for ONE open position by skill and bench status."
                            + " Use when asked who matches or could fill a specific position; for"
                            + " questions across all positions use get_position_match_summary instead.",
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
                            + " allocation or bench status, past projects, certifications, and exit."
                            + " Works for former employees too — use it for questions about anyone by"
                            + " name, including past projects, certifications, or people who have left.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("name", Map.of("type", "string")),
                            "required", List.of("name"))),
            Map.of("name", "get_project_detail",
                    "description", "One project's roster: who is currently staffed on it (with"
                            + " percent and billable), its client and status, and any open seats."
                            + " Use when asked who is on / working on / staffed on a project.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("projectName", Map.of("type", "string")),
                            "required", List.of("projectName"))),
            Map.of("name", "list_rolloffs",
                    "description", "Current allocations ending soon — who rolls off which project"
                            + " and when.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("withinDays", Map.of("type", "integer",
                                    "description", "look-ahead window in days; default 30")))),
            Map.of("name", "list_open_positions",
                    "description", "All open positions with project, client, and required skills.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "list_clients",
                    "description", "Every client we have, with industry, location, and how many"
                            + " projects each runs. Use whenever asked who our clients are, to name"
                            + " or list them, or about a client that has no open position.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "list_projects",
                    "description", "Every project with its code, client, status, and dates."
                            + " Optionally narrowed to one client. Use when asked what projects we"
                            + " run, to list or name them, or what a given client is running.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("clientName", Map.of("type", "string",
                                    "description", "optional: only this client's projects")))),
            Map.of("name", "get_skill_gaps",
                    "description", "Skill supply vs demand: for each skill required by open positions,"
                            + " how many seats demand it, bench supply, total holders, and the gap"
                            + " (positive = hire or train). Use when asked about skill gaps, shortages,"
                            + " or hiring/training needs.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "list_expiring_certifications",
                    "description", "Certifications expiring soon across the whole workforce, soonest"
                            + " first. Use when asked whose certifications expire or need renewal.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("withinDays", Map.of("type", "integer",
                                    "description", "look-ahead window in days; default "
                                            + DashboardService.CERT_EXPIRY_HORIZON_DAYS)))),
            Map.of("name", "get_workforce_summary",
                    "description", "Org-health snapshot: headcounts, billable/non-billable/bench split,"
                            + " onshore/offshore, utilization %, bench-aging buckets, exits in the last"
                            + " 12 months, the six-month staffing trend, and the 30/60/90-day utilization"
                            + " forecast with the events driving it. Use for overall health, utilization,"
                            + " or trend questions.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "list_bench_aging",
                    "description", "Everyone on the bench sorted longest-benched first with days on"
                            + " bench, plus aging bucket counts. Use when asked who has been on the"
                            + " bench longest or how the bench is aging.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "get_position_match_summary",
                    "description", "Bench-match overview for ALL open positions in one call: which"
                            + " have full bench matches (and who), and which have none. Use for"
                            + " questions comparing or spanning positions, like which open positions"
                            + " have no bench match.",
                    "parameters", Map.of("type", "object", "properties", Map.of())));

    /** Declared and executable only when the caller is an admin. */
    static final Set<String> ADMIN_READ_TOOLS = Set.of("list_pending_approvals", "get_audit_history");

    private static final List<Map<String, Object>> ADMIN_DECLARATIONS = List.of(
            Map.of("name", "list_pending_approvals",
                    "description", "Everything waiting on an admin: pending profile-change"
                            + " requests and pending access requests. Use when an admin asks"
                            + " what needs their attention or approval.",
                    "parameters", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "get_audit_history",
                    "description", "Recent audit-log entries, newest first: who changed what"
                            + " and when. Optionally filtered by entity type (e.g. Allocation,"
                            + " Associate, Project, Client).",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "entityType", Map.of("type", "string",
                                            "description", "optional entity type filter"),
                                    "limit", Map.of("type", "integer",
                                            "description", "max rows; default 25")))));

    /** The base declarations, plus the admin set when the caller is an admin. */
    static List<Map<String, Object>> declarationsFor(boolean adminTools) {
        if (!adminTools) {
            return FUNCTION_DECLARATIONS;
        }
        List<Map<String, Object>> all = new java.util.ArrayList<>(FUNCTION_DECLARATIONS);
        all.addAll(ADMIN_DECLARATIONS);
        return all;
    }

    @Override
    public AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                         String userMessage, ToolExecutor tools, boolean adminTools) {
        if (apiKey.isEmpty()) {
            throw new BadRequestException("The AI assistant is not configured — "
                    + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
        }
        List<Map<String, Object>> contents = buildContents(workforceContext, history, userMessage);
        for (int round = 0; ; round++) {
            Map<String, Object> response = callApi(Map.of(
                    "contents", contents,
                    "tools", List.of(Map.of("functionDeclarations", declarationsFor(adminTools)))));
            Map<String, Object> functionCall = firstFunctionCall(response);
            if (functionCall == null) {
                return new AssistantReply(textOrEmpty(response), null);
            }
            String name = String.valueOf(functionCall.get("name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> args = functionCall.get("args") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            if ((READ_TOOLS.contains(name) || (adminTools && ADMIN_READ_TOOLS.contains(name)))
                    && tools != null) {
                contents.add(Map.of("role", "model",
                        "parts", firstCandidateParts(response)));
                if (round < MAX_TOOL_ROUNDS) {
                    String result = tools.execute(name, args);
                    contents.add(Map.of("role", "user",
                            "parts", List.of(Map.of("functionResponse",
                                    Map.of("name", name, "response", Map.of("result", result))))));
                    continue;
                }
                // Cap hit on a READ tool: leaking it as an ActionCall would earn the user
                // the misleading write-tool fallback. Instead tell the model the budget is
                // spent and take exactly one wrap-up call for a prose answer.
                contents.add(Map.of("role", "user",
                        "parts", List.of(Map.of("functionResponse",
                                Map.of("name", name, "response",
                                        Map.of("result", TOOL_BUDGET_EXHAUSTED_RESULT))))));
                Map<String, Object> wrapUp = callApi(Map.of(
                        "contents", contents,
                        "tools", List.of(Map.of("functionDeclarations", declarationsFor(adminTools)))));
                String text = textOrEmpty(wrapUp);
                return new AssistantReply(text.isBlank() ? TOOL_BUDGET_FALLBACK_TEXT : text, null);
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
                    .uri(endpoint)
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
                    .uri(endpoint)
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
