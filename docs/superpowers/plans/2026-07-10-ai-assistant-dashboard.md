# AI Assistant on the Dashboard (Gemini) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A natural-language "Ask OmiVertex AI" card on the Dashboard that answers questions about the live workforce (bench, projects, positions, roll-offs) via the Google Gemini API, per `docs/superpowers/specs/2026-07-10-gemini-ai-chatbot-design.md`.

**Architecture:** Vendor-neutral `/api/v1/assistant/chat` (ADMIN+VIEWER). `AssistantService` builds a full-detail workforce context (Markdown) from the repositories per request and calls an injectable `GeminiClient`; the HTTP implementation (`GeminiHttpClient`) is config-gated and fails closed, and every test mocks the interface — the suite never calls Google.

**Tech Stack:** Spring Boot 3.5 `RestClient`, Gemini `generateContent` REST API (`gemini-2.5-flash` default), React 18 dashboard card. No new frontend dependencies.

**Conventions:** AGENTS.md applies — TDD, DTOs at the boundary, constants not magic numbers, tokens not hex, Definition of Done before stopping.

---

### Task 1: `GeminiClient` boundary + config-gated HTTP implementation

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/GeminiClient.java`
- Create: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java`

- [ ] **Step 1: Write the failing test** (plain JUnit, no Spring context — mirrors `ResumeSkillMatcherTest` style)

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiHttpClientTest {

    @Test
    void withoutApiKey_failsClosedWithClearMessage() {
        GeminiHttpClient client = new GeminiHttpClient("", "gemini-2.5-flash");
        assertThatThrownBy(() -> client.reply("context", List.of(), "who is on the bench?"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not configured");
    }
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=GeminiHttpClientTest` — expect COMPILATION ERROR (classes missing). That is the correct RED.

- [ ] **Step 3: Implement.**

`GeminiClient.java`:
```java
package com.softility.omivertex.service;

import java.util.List;

/**
 * Generates an assistant reply from a workforce context, prior turns, and the
 * user's question. Abstracted so the endpoint depends on the contract, not the
 * Gemini SDK/REST shape — and so tests supply a stub instead of calling Google.
 */
public interface GeminiClient {

    String reply(String workforceContext, List<Turn> history, String userMessage);

    /** One prior chat turn; role is "user" or "model". */
    record Turn(String role, String content) {}
}
```

`GeminiHttpClient.java`:
```java
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
```

`application.properties` — append:
```properties
# Dashboard AI assistant (Gemini). Empty key = assistant disabled (fail closed).
omivertex.assistant.gemini.api-key=${OMIVERTEX_ASSISTANT_GEMINI_API_KEY:}
omivertex.assistant.gemini.model=${OMIVERTEX_ASSISTANT_GEMINI_MODEL:gemini-2.5-flash}
```

- [ ] **Step 4: Run** `./mvnw test -Dtest=GeminiHttpClientTest` — expect PASS.
- [ ] **Step 5: Commit** `feat: config-gated Gemini client boundary for the dashboard assistant`

### Task 2: Workforce context builder

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`

- [ ] **Step 1: Write the failing test** (extends `ApiTestBase` for the seeded-repo helpers)

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.service.AssistantContextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContextBuilderTest extends ApiTestBase {

    @Autowired AssistantContextBuilder builder;

    @Test
    void context_containsRosterAllocationsDemandAndKpis() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");

        var staffed = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        rateSkill(staffed, java, Proficiency.ADVANCE);
        allocation(staffed, proj, true);

        var bench = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);

        var position = new OpenPosition();
        position.setTitle("Java Dev");
        position.setProject(proj);
        openPositionRepository.save(position);
        var req = new PositionSkill();
        req.setPosition(position);
        req.setSkill(java);
        req.setMinProficiency(Proficiency.INTERMEDIATE);
        req.setRequired(true);
        positionSkillRepository.save(req);

        String context = builder.build();

        assertThat(context).contains("Priya Sharma");
        assertThat(context).contains("priya@softility.com");          // full-detail decision
        assertThat(context).contains("Java (ADVANCE)");
        assertThat(context).contains("Storefront Revamp");
        assertThat(context).contains("Acme Corp");
        assertThat(context).contains("Rahul Verma");
        assertThat(context).contains("BENCH");                        // bench marker
        assertThat(context).contains("Java Dev");
        assertThat(context).contains("must-have: Java (min INTERMEDIATE)");
        assertThat(context).contains("Bench count: 1");
    }

    @Test
    void context_marksExitedAssociates() {
        var leaver = associate("Gone Guy", "gone@softility.com", WorkMode.ONSHORE);
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().plusDays(10));
        associateRepository.save(leaver);

        assertThat(builder.build()).contains("leaving on " + LocalDate.now().plusDays(10));
    }
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=AssistantContextBuilderTest` — COMPILATION ERROR (class missing) = RED.

- [ ] **Step 3: Implement.**

```java
package com.softility.omivertex.service;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.*;
import com.softility.omivertex.web.dto.AssociateResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles the live workforce picture into a dense Markdown context for the AI
 * assistant. FULL detail by user decision (2026-07-10, see docs/TODO.md):
 * names, emails, skills, allocations, exits, demand. Resume file contents are
 * never included.
 */
@Component
public class AssistantContextBuilder {

    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AssociateSkillRepository associateSkills;
    private final OpenPositionRepository positions;
    private final PositionSkillRepository positionSkills;

    public AssistantContextBuilder(AssociateRepository associates, AllocationRepository allocations,
                                   AssociateSkillRepository associateSkills, OpenPositionRepository positions,
                                   PositionSkillRepository positionSkills) {
        this.associates = associates;
        this.allocations = allocations;
        this.associateSkills = associateSkills;
        this.positions = positions;
        this.positionSkills = positionSkills;
    }

    @Transactional(readOnly = true)
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the OmiVertex AI Assistant for Softility's internal resource-management ")
          .append("platform. Answer questions about the workforce data below concisely and accurately. ")
          .append("Use short bullet lists where helpful. If the data cannot answer the question, say so ")
          .append("— never invent people, projects, or numbers.\n\n");

        List<Associate> all = associates.findAll();
        List<Associate> active = all.stream().filter(a -> a.getStatus() == EntityStatus.ACTIVE).toList();
        List<Allocation> allAllocations = allocations.findAllWithDetails();
        Map<Long, List<Allocation>> byAssociate = allAllocations.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));

        long benchCount = active.stream()
                .filter(a -> byAssociate.getOrDefault(a.getId(), List.of()).stream().noneMatch(Allocation::isCurrent))
                .count();
        sb.append("## Key numbers (today: ").append(LocalDate.now()).append(")\n");
        sb.append("Active associates: ").append(active.size())
          .append(" · Bench count: ").append(benchCount)
          .append(" · Open positions: ").append(positions.findAllWithDetails().stream()
                  .filter(p -> p.getStatus() == PositionStatus.OPEN).count()).append("\n\n");

        sb.append("## Associates (ACTIVE)\n");
        for (Associate a : active) {
            List<Allocation> current = byAssociate.getOrDefault(a.getId(), List.of()).stream()
                    .filter(Allocation::isCurrent).toList();
            sb.append("- ").append(a.getName()).append(" <").append(a.getEmail()).append("> · ")
              .append(a.getDesignation() == null ? "no designation" : a.getDesignation()).append(" · ")
              .append(a.getWorkMode());
            String skills = skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                    .map(s -> s.getSkill().getName() + " (" + s.getProficiency() + ")")
                    .collect(Collectors.joining(", "));
            if (!skills.isEmpty()) sb.append(" · skills: ").append(skills);
            if (current.isEmpty()) {
                Long benchDays = AssociateResponse.benchDays(a, byAssociate.getOrDefault(a.getId(), List.of()));
                sb.append(" · BENCH").append(benchDays == null ? "" : " for " + benchDays + " days");
            } else {
                sb.append(" · allocated: ").append(current.stream()
                        .map(al -> al.getProject().getName() + " @" + al.getProject().getClient().getName()
                                + " (" + al.getAllocationPercent() + "%"
                                + (al.isBillable() ? ", billable" : ", non-billable")
                                + (al.getEndDate() == null ? "" : ", ends " + al.getEndDate()) + ")")
                        .collect(Collectors.joining("; ")));
            }
            if (a.getLastWorkingDay() != null && !a.getLastWorkingDay().isBefore(LocalDate.now())) {
                sb.append(" · leaving on ").append(a.getLastWorkingDay())
                  .append(" (").append(a.getExitReason()).append(")");
            }
            sb.append("\n");
        }

        List<Associate> exited = all.stream().filter(a -> a.getStatus() == EntityStatus.INACTIVE
                && a.getLastWorkingDay() != null).toList();
        if (!exited.isEmpty()) {
            sb.append("\n## Recent exits\n");
            for (Associate a : exited) {
                sb.append("- ").append(a.getName()).append(" left on ").append(a.getLastWorkingDay())
                  .append(" (").append(a.getExitReason()).append(")\n");
            }
        }

        Map<Long, List<PositionSkill>> reqsByPosition = positionSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(ps -> ps.getPosition().getId()));
        sb.append("\n## Open positions\n");
        positions.findAllWithDetails().stream()
                .filter(p -> p.getStatus() == PositionStatus.OPEN)
                .forEach(p -> {
                    sb.append("- ").append(p.getTitle()).append(" on ").append(p.getProject().getName())
                      .append(" @").append(p.getProject().getClient().getName())
                      .append(" (").append(p.getAllocationPercent()).append("%")
                      .append(p.isBillable() ? ", billable" : ", non-billable")
                      .append(p.getWorkMode() == null ? "" : ", " + p.getWorkMode())
                      .append(p.getStartDate() == null ? "" : ", starts " + p.getStartDate())
                      .append(")");
                    List<PositionSkill> reqs = reqsByPosition.getOrDefault(p.getId(), List.of());
                    String must = reqs.stream().filter(PositionSkill::isRequired)
                            .map(r -> r.getSkill().getName() + " (min "
                                    + (r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency()) + ")")
                            .collect(Collectors.joining(", "));
                    String nice = reqs.stream().filter(r -> !r.isRequired())
                            .map(r -> r.getSkill().getName()).collect(Collectors.joining(", "));
                    if (!must.isEmpty()) sb.append(" · must-have: ").append(must);
                    if (!nice.isEmpty()) sb.append(" · nice-to-have: ").append(nice);
                    sb.append("\n");
                });
        return sb.toString();
    }
}
```

(`OpenPositionRepository` must have `findAllWithDetails()` — it exists, used by `PositionService.list`.)

- [ ] **Step 4: Run** `./mvnw test -Dtest=AssistantContextBuilderTest` — PASS. Adjust assertions only if a label differs from implementation; never weaken them.
- [ ] **Step 5: Commit** `feat: assistant workforce context builder (full-detail, resume contents excluded)`

### Task 3: POST /api/v1/assistant/chat (endpoint, limits, security)

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/AssistantController.java`
- Create: `src/main/java/com/softility/omivertex/web/dto/AssistantChatRequest.java`, `AssistantChatResponse.java`
- Create: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/config/SecurityConfig.java` (before the blanket GET rule)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssistantApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    @Test
    void chat_answersWithWorkforceContext() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // on bench
        when(geminiClient.reply(anyString(), anyList(), anyString()))
                .thenReturn("Priya Sharma is on the bench.");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on the bench?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Priya Sharma is on the bench."));

        // the live roster went along as context
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).reply(context.capture(), anyList(), anyString());
        assertThat(context.getValue()).contains("Priya Sharma");
    }

    @Test
    void chat_viewerAllowed_associateForbidden() throws Exception {
        when(geminiClient.reply(anyString(), anyList(), anyString())).thenReturn("ok");
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":[]}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":[]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_blankOrOversizedMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"","history":[]}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"%s","history":[]}""".formatted("x".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_capsHistoryToLast20Turns() throws Exception {
        when(geminiClient.reply(anyString(), anyList(), anyString())).thenReturn("ok");
        StringBuilder history = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) history.append(",");
            history.append("""
                    {"role":"user","content":"turn %d"}""".formatted(i));
        }
        history.append("]");
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":%s}""".formatted(history)))
                .andExpect(status().isOk());

        ArgumentCaptor<List<GeminiClient.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(geminiClient).reply(any(), turns.capture(), any());
        assertThat(turns.getValue()).hasSize(20);
        assertThat(turns.getValue().get(0).content()).isEqualTo("turn 10"); // oldest dropped
    }
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=AssistantApiTest` — 404s/compile error = RED.

- [ ] **Step 3: Implement.**

`AssistantChatRequest.java`:
```java
package com.softility.omivertex.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message cannot exceed 2000 characters")
        String message,
        @Valid List<HistoryTurn> history) {

    public record HistoryTurn(String role, String content) {}
}
```

`AssistantChatResponse.java`:
```java
package com.softility.omivertex.web.dto;

public record AssistantChatResponse(String reply) {}
```

`AssistantService.java`:
```java
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
```

`AssistantController.java`:
```java
package com.softility.omivertex.web;

import com.softility.omivertex.service.AssistantService;
import com.softility.omivertex.web.dto.AssistantChatRequest;
import com.softility.omivertex.web.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }
}
```

`SecurityConfig.java` — insert directly after the `/api/v1/me/**` matcher:
```java
                        // the dashboard AI assistant is a manager tool: viewers may ask too
                        .requestMatchers("/api/v1/assistant/**").hasAnyRole("ADMIN", "VIEWER")
```

- [ ] **Step 4: Run** `./mvnw test` — full suite green.
- [ ] **Step 5: Commit** `feat: POST /api/v1/assistant/chat — workforce Q&A over Gemini (ADMIN+VIEWER)`

### Task 4: Dashboard "Ask OmiVertex AI" card

**Files:**
- Create: `frontend/src/components/AssistantChat.jsx`
- Modify: `frontend/src/api.js` (append helper)
- Modify: `frontend/src/pages/Dashboard.jsx` (render card after the stat grid)

- [ ] **Step 1: api.js helper** (append inside the `api` object)

```javascript
  askAssistant: (message, history) =>
    request('/assistant/chat', { method: 'POST', body: JSON.stringify({ message, history }) }),
```

- [ ] **Step 2: `AssistantChat.jsx`**

```jsx
import { useRef, useState } from 'react';
import { api } from '../api.js';
import Icon from './Icon.jsx';

const SUGGESTIONS = [
  'Who is on the bench right now?',
  'Which open positions have no bench match?',
  'Who rolls off a project in the next 30 days?',
  'Summarize our biggest skill gaps.',
];

/** Renders reply text: blank-line paragraphs, "- " bullets, **bold** — no innerHTML. */
function ReplyText({ text }) {
  const lines = (text || '').split('\n');
  const renderInline = (line, key) => {
    const parts = line.split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
    return (
      <span key={key}>
        {parts.map((p, i) =>
          p.startsWith('**') && p.endsWith('**') ? <strong key={i}>{p.slice(2, -2)}</strong> : p
        )}
      </span>
    );
  };
  const blocks = [];
  let bullets = [];
  lines.forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      bullets.push(renderInline(trimmed.slice(2), i));
    } else {
      if (bullets.length) {
        blocks.push(
          <ul key={`ul-${i}`} style={{ margin: '4px 0', paddingLeft: '18px' }}>
            {bullets.map((b, j) => (
              <li key={j}>{b}</li>
            ))}
          </ul>
        );
        bullets = [];
      }
      if (trimmed) blocks.push(<p key={i} style={{ margin: '4px 0' }}>{renderInline(trimmed, i)}</p>);
    }
  });
  if (bullets.length) {
    blocks.push(
      <ul key="ul-end" style={{ margin: '4px 0', paddingLeft: '18px' }}>
        {bullets.map((b, j) => (
          <li key={j}>{b}</li>
        ))}
      </ul>
    );
  }
  return <>{blocks}</>;
}

export default function AssistantChat({ showToast }) {
  const [messages, setMessages] = useState([]); // { role: 'user'|'model', content }
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const logRef = useRef(null);

  const ask = async (text) => {
    const question = (text || '').trim();
    if (!question || busy) return;
    setBusy(true);
    setInput('');
    const history = messages;
    setMessages((m) => [...m, { role: 'user', content: question }]);
    try {
      const { reply } = await api.askAssistant(question, history);
      setMessages((m) => [...m, { role: 'model', content: reply }]);
    } catch (err) {
      setMessages((m) => m.slice(0, -1));
      showToast(err.message, true);
    } finally {
      setBusy(false);
      setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50);
    }
  };

  return (
    <div className="card panel" style={{ gridColumn: '1 / -1' }}>
      <h2>
        <Icon name="sparkles" size={15} /> Ask OmiVertex AI
      </h2>
      <p className="stat-hint" style={{ marginTop: 0 }}>
        Natural-language questions over the live workforce data — bench, projects, demand,
        roll-offs.
      </p>
      {messages.length === 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '10px' }}>
          {SUGGESTIONS.map((s) => (
            <button key={s} className="btn btn-ghost btn-sm" onClick={() => ask(s)}>
              {s}
            </button>
          ))}
        </div>
      )}
      {messages.length > 0 && (
        <div
          ref={logRef}
          style={{
            maxHeight: '320px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
            marginBottom: '10px',
          }}
        >
          {messages.map((m, i) => (
            <div
              key={i}
              style={{
                alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                padding: '8px 12px',
                borderRadius: '10px',
                background: m.role === 'user' ? 'var(--color-primary-soft)' : 'var(--color-surface-2)',
                border: '1px solid var(--color-border)',
                fontSize: '14px',
              }}
            >
              {m.role === 'model' && <Icon name="sparkles" size={12} />}{' '}
              <ReplyText text={m.content} />
            </div>
          ))}
          {busy && <div className="stat-hint">Thinking…</div>}
        </div>
      )}
      <form
        style={{ display: 'flex', gap: '8px' }}
        onSubmit={(e) => {
          e.preventDefault();
          ask(input);
        }}
      >
        <input
          style={{ flex: 1 }}
          value={input}
          maxLength={2000}
          placeholder="e.g. How many people are on the bench, and what projects are running?"
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
        />
        <button className="btn btn-primary" type="submit" disabled={busy || !input.trim()}>
          {busy ? '…' : 'Ask'}
        </button>
      </form>
    </div>
  );
}
```

Check token names against `styles.css` before using: if `--color-primary-soft` /
`--color-surface-2` don't exist, use the nearest existing tokens (`grep -n "\-\-color"
frontend/src/styles.css`) — never a raw hex. Check `sparkles` exists in `Icon.jsx`
(`grep -n "sparkles" frontend/src/components/Icon.jsx`); it is already used by the
Positions match button. If missing, use `target`.

- [ ] **Step 3: Dashboard.jsx** — import and render as the first panel after the stat grid:

```jsx
import AssistantChat from '../components/AssistantChat.jsx';
// … inside the render, immediately after </div> closing .stat-grid:
      <AssistantChat showToast={showToast} />
```
(`Dashboard.jsx` receives `showToast` as a prop already.)

- [ ] **Step 4: Verify** `cd frontend && npm run format && npm run build` — green. `./mvnw test` still green.
- [ ] **Step 5: Commit** `feat: Ask OmiVertex AI dashboard card (chat over live workforce data)`

### Task 5: Docs + Definition of Done

**Files:**
- Modify: `docs/TECHNICAL.md`, `docs/TODO.md`, `docs/DEPLOYMENT.md`

- [ ] **Step 1:** `TECHNICAL.md` — REST table row: `| /assistant/chat | POST (natural-language Q&A over live workforce context via Gemini; ADMIN+VIEWER) | — |`; add a short §"AI assistant" note: vendor-neutral `GeminiClient` boundary (mocked in tests), full-detail context per user decision, fail-closed config `omivertex.assistant.gemini.api-key` / `.model`, history capped at 20 turns, message ≤ 2000 chars.
- [ ] **Step 2:** `TODO.md` Resolved decisions: *"AI assistant sends FULL workforce detail (incl. emails, exits) to the Gemini API per user decision 2026-07-10; resume file contents excluded. Vendor kept behind GeminiClient so the suite never calls Google."* `DEPLOYMENT.md`: document `OMIVERTEX_ASSISTANT_GEMINI_API_KEY` (+ optional `OMIVERTEX_ASSISTANT_GEMINI_MODEL`) and the outbound HTTPS dependency on `generativelanguage.googleapis.com`.
- [ ] **Step 3:** Full Definition of Done (AGENTS.md): `./mvnw test` · frontend format+build · docs done above · graph refresh · commit `docs: AI assistant contract, config, and data-sharing decision`.
