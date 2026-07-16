package com.softility.omivertex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.AssistantChatRequest;
import com.softility.omivertex.web.dto.AssistantChatResponse;
import com.softility.omivertex.web.dto.AssistantChatResponse.ActionType;
import com.softility.omivertex.web.dto.AssistantChatResponse.ProposedAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates a dashboard-assistant turn: live context + capped history ->
 * Gemini with tools. Write tools only ever produce a {@link ProposedAction}
 * draft — this service never mutates data; the user confirms in the browser
 * through the existing endpoints (role checks, capacity guard, audit intact).
 */
@Service
@Transactional(readOnly = true)
public class AssistantService {

    /** Prior turns sent to the model; older ones are dropped. */
    static final int MAX_HISTORY_TURNS = 20;
    /** Allocation drafts default to full-time billable starting today. */
    static final int DEFAULT_PERCENT = 100;

    private final AssistantContextBuilder contextBuilder;
    private final GeminiClient geminiClient;
    private final AssociateRepository associateRepository;
    private final ProjectRepository projectRepository;
    private final OpenPositionRepository positionRepository;
    private final AllocationRepository allocationRepository;
    private final PositionService positionService;
    private final ObjectMapper objectMapper;
    private final AssistantInteractionLog interactionLog;

    public AssistantService(AssistantContextBuilder contextBuilder, GeminiClient geminiClient,
                            AssociateRepository associateRepository, ProjectRepository projectRepository,
                            OpenPositionRepository positionRepository, AllocationRepository allocationRepository,
                            PositionService positionService, ObjectMapper objectMapper,
                            AssistantInteractionLog interactionLog) {
        this.contextBuilder = contextBuilder;
        this.geminiClient = geminiClient;
        this.associateRepository = associateRepository;
        this.projectRepository = projectRepository;
        this.positionRepository = positionRepository;
        this.allocationRepository = allocationRepository;
        this.positionService = positionService;
        this.objectMapper = objectMapper;
        this.interactionLog = interactionLog;
    }

    public AssistantChatResponse chat(AssistantChatRequest request, String username) {
        long start = System.currentTimeMillis();
        List<String> toolsCalled = new ArrayList<>();
        try {
            List<AssistantChatRequest.HistoryTurn> history =
                    request.history() == null ? List.of() : request.history();
            List<GeminiClient.Turn> turns = history.stream()
                    .skip(Math.max(0, history.size() - MAX_HISTORY_TURNS))
                    .map(t -> new GeminiClient.Turn(t.role(), t.content()))
                    .toList();
            GeminiClient.AssistantReply reply = geminiClient.replyWithTools(
                    contextBuilder.build(), turns, request.message(), (name, args) -> {
                        toolsCalled.add(name);
                        return executeReadTool(name, args);
                    });
            AssistantChatResponse response = reply.action() == null
                    ? new AssistantChatResponse(reply.text(), null)
                    : draft(reply);
            interactionLog.record(username,
                    response.proposedAction() == null ? AssistantInteractionLog.Outcome.ANSWERED
                            : AssistantInteractionLog.Outcome.DRAFTED,
                    toolsCalled, System.currentTimeMillis() - start, request.message());
            return response;
        } catch (RuntimeException e) {
            interactionLog.record(username, AssistantInteractionLog.Outcome.ERROR,
                    toolsCalled, System.currentTimeMillis() - start, request.message());
            throw e;
        }
    }

    // ---- write-tool drafts (resolution + pre-validation, never execution) ----

    private AssistantChatResponse draft(GeminiClient.AssistantReply reply) {
        GeminiClient.ActionCall action = reply.action();
        Map<String, Object> args = action.args();
        return switch (action.name()) {
            case "propose_allocation" -> draftAllocation(reply.text(), args);
            case "propose_position_fill" -> draftFill(reply.text(), args);
            default -> new AssistantChatResponse(nonBlank(reply.text(),
                    "I can't do that yet — I can draft allocations and position fills."), null);
        };
    }

    private AssistantChatResponse draftAllocation(String text, Map<String, Object> args) {
        Resolved<Associate> associate = resolveAssociate(str(args, "associateName"));
        if (associate.reply() != null) {
            return new AssistantChatResponse(associate.reply(), null);
        }
        Resolved<Project> project = resolveProject(str(args, "projectName"));
        if (project.reply() != null) {
            return new AssistantChatResponse(project.reply(), null);
        }
        int percent = intOrDefault(args.get("percent"), DEFAULT_PERCENT);
        boolean billable = boolOrDefault(args.get("billable"), true);
        LocalDate start = dateOrDefault(args.get("startDate"), LocalDate.now());
        LocalDate end = dateOrDefault(args.get("endDate"), null);

        List<String> warnings = capacityWarnings(associate.value(), percent);
        String summary = "Allocate %s to %s at %d%% (%s) from %s".formatted(
                associate.value().getName(), project.value().getName(), percent,
                billable ? "billable" : "non-billable", start);
        ProposedAction proposed = new ProposedAction(ActionType.CREATE_ALLOCATION,
                associate.value().getId(), associate.value().getName(),
                project.value().getId(), project.value().getName(),
                null, null, percent, billable, start, end, summary, warnings);
        return new AssistantChatResponse(nonBlank(text,
                "Here's the draft — review and confirm below."), proposed);
    }

    private AssistantChatResponse draftFill(String text, Map<String, Object> args) {
        Resolved<OpenPosition> position = resolvePosition(str(args, "positionTitle"));
        if (position.reply() != null) {
            return new AssistantChatResponse(position.reply(), null);
        }
        Resolved<Associate> associate = resolveAssociate(str(args, "associateName"));
        if (associate.reply() != null) {
            return new AssistantChatResponse(associate.reply(), null);
        }
        OpenPosition pos = position.value();
        int percent = pos.getAllocationPercent();
        List<String> warnings = capacityWarnings(associate.value(), percent);
        String summary = "Fill '%s' with %s — allocates %d%% %s per the position's terms".formatted(
                pos.getTitle(), associate.value().getName(), percent,
                pos.isBillable() ? "billable" : "non-billable");
        ProposedAction proposed = new ProposedAction(ActionType.FILL_POSITION,
                associate.value().getId(), associate.value().getName(),
                pos.getProject().getId(), pos.getProject().getName(),
                pos.getId(), pos.getTitle(), percent, pos.isBillable(),
                pos.getStartDate(), pos.getEndDate(), summary, warnings);
        return new AssistantChatResponse(nonBlank(text,
                "Here's the draft — review and confirm below."), proposed);
    }

    private List<String> capacityWarnings(Associate associate, int percent) {
        int current = allocationRepository.findByAssociateId(associate.getId()).stream()
                .filter(Allocation::isCurrent)
                .mapToInt(Allocation::getAllocationPercent)
                .sum();
        List<String> warnings = new ArrayList<>();
        if (current + percent > 100) {
            warnings.add("This would take %s over 100%% — current allocations already total %d%%."
                    .formatted(associate.getName(), current));
        }
        return warnings;
    }

    // ---- name -> entity resolution; ambiguity or no match returns a clarifying reply ----

    private record Resolved<T>(T value, String reply) {}

    /** Active-only — used by the write drafts; you can't allocate someone who has left. */
    private Resolved<Associate> resolveAssociate(String name) {
        return resolveAssociate(name, true);
    }

    /**
     * Resolve a name to an associate. Read tools pass {@code activeOnly=false} so
     * questions about former employees still return their record; write drafts keep
     * {@code activeOnly=true} so an exited person can never be allocated.
     */
    private Resolved<Associate> resolveAssociate(String name, boolean activeOnly) {
        if (name == null || name.isBlank()) {
            return new Resolved<>(null, "Which associate did you mean? Please give me a name.");
        }
        List<Associate> pool = associateRepository.findAll().stream()
                .filter(a -> !activeOnly || a.getStatus() == EntityStatus.ACTIVE).toList();
        List<Associate> matches = matchByName(pool, Associate::getName, name);
        if (matches.size() == 1) {
            return new Resolved<>(matches.get(0), null);
        }
        if (matches.isEmpty()) {
            return new Resolved<>(null, activeOnly
                    ? "I couldn't find an active associate matching \"%s\".".formatted(name)
                    : "I couldn't find an associate matching \"%s\".".formatted(name));
        }
        return new Resolved<>(null, "I found more than one match for \"%s\": %s — which one did you mean?"
                .formatted(name, matches.stream().map(Associate::getName).collect(Collectors.joining(", "))));
    }

    private Resolved<Project> resolveProject(String name) {
        if (name == null || name.isBlank()) {
            return new Resolved<>(null, "Which project did you mean? Please give me a name.");
        }
        List<Project> matches = matchByName(projectRepository.findAll(), Project::getName, name);
        if (matches.size() == 1) {
            return new Resolved<>(matches.get(0), null);
        }
        if (matches.isEmpty()) {
            return new Resolved<>(null, "I couldn't find a project matching \"%s\".".formatted(name));
        }
        return new Resolved<>(null, "I found more than one project for \"%s\": %s — which one did you mean?"
                .formatted(name, matches.stream().map(Project::getName).collect(Collectors.joining(", "))));
    }

    private Resolved<OpenPosition> resolvePosition(String title) {
        if (title == null || title.isBlank()) {
            return new Resolved<>(null, "Which open position did you mean? Please give me its title.");
        }
        List<OpenPosition> matches = matchByName(
                positionRepository.findAll().stream()
                        .filter(p -> p.getStatus() == PositionStatus.OPEN).toList(),
                OpenPosition::getTitle, title);
        if (matches.size() == 1) {
            return new Resolved<>(matches.get(0), null);
        }
        if (matches.isEmpty()) {
            return new Resolved<>(null, "I couldn't find an open position matching \"%s\".".formatted(title));
        }
        return new Resolved<>(null, "I found more than one open position for \"%s\": %s — which one did you mean?"
                .formatted(title, matches.stream().map(OpenPosition::getTitle).collect(Collectors.joining(", "))));
    }

    /** Exact (case-insensitive) match wins; otherwise fall back to contains. */
    private <T> List<T> matchByName(List<T> candidates, java.util.function.Function<T, String> nameOf, String query) {
        String needle = query.trim().toLowerCase();
        List<T> exact = candidates.stream()
                .filter(c -> nameOf.apply(c).toLowerCase().equals(needle)).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return candidates.stream()
                .filter(c -> nameOf.apply(c).toLowerCase().contains(needle)).toList();
    }

    // ---- read tools (executed server-side, result goes back to the model) ----

    private String executeReadTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "get_position_matches" -> positionMatches(args);
            case "search_associates" -> contextBuilder.searchAssociates(
                    str(args, "name"), str(args, "skill"),
                    proficiencyOrNull(str(args, "minProficiency")),
                    boolOrDefault(args.get("benchOnly"), false));
            case "get_associate_detail" -> {
                Resolved<Associate> associate = resolveAssociate(str(args, "name"), false);
                yield associate.reply() != null ? associate.reply()
                        : contextBuilder.associateDetail(associate.value());
            }
            case "get_project_detail" -> {
                Resolved<Project> project = resolveProject(str(args, "projectName"));
                yield project.reply() != null ? project.reply()
                        : contextBuilder.projectDetail(project.value());
            }
            case "list_rolloffs" -> contextBuilder.rolloffs(intOrDefault(args.get("withinDays"), 30));
            case "list_open_positions" -> contextBuilder.openPositions();
            case "list_clients" -> contextBuilder.listClients();
            case "list_projects" -> contextBuilder.listProjects(str(args, "clientName"));
            case "get_skill_gaps" -> contextBuilder.skillGaps();
            case "list_expiring_certifications" -> contextBuilder.expiringCertifications(
                    intOrDefault(args.get("withinDays"), DashboardService.CERT_EXPIRY_HORIZON_DAYS));
            case "get_workforce_summary" -> contextBuilder.workforceSummary();
            case "list_bench_aging" -> contextBuilder.benchAging();
            case "get_position_match_summary" -> contextBuilder.positionMatchSummary();
            default -> "Unknown tool: " + name;
        };
    }

    private String positionMatches(Map<String, Object> args) {
        Resolved<OpenPosition> position = resolvePosition(str(args, "positionTitle"));
        if (position.reply() != null) {
            return position.reply();
        }
        try {
            return objectMapper.writeValueAsString(positionService.matches(position.value().getId()));
        } catch (JsonProcessingException e) {
            return "Could not compute matches right now.";
        }
    }

    private static com.softility.omivertex.domain.Proficiency proficiencyOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return com.softility.omivertex.domain.Proficiency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- lenient arg coercion (model output is untyped) ----

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    private static int intOrDefault(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? fallback : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOrDefault(Object v, boolean fallback) {
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? fallback : Boolean.parseBoolean(v.toString());
    }

    private static LocalDate dateOrDefault(Object v, LocalDate fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(v.toString());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static String nonBlank(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
