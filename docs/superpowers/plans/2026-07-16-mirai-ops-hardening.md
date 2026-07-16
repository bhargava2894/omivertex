# Mirai Ops Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Mirai an interaction log (log-file only, no DB, no reply text), a test pinning the Gemini tool-loop cap, and an opt-in golden-question eval suite against the live model.

**Architecture:** A new `AssistantInteractionLog` component writes one SLF4J line per chat turn; `AssistantService.chat()` gains a `username` parameter (resolved on the servlet thread in the controller — the `ai-*` pool can't see the `SecurityContext`) and a tool-collecting executor decorator. `GeminiHttpClient`'s hardcoded endpoint becomes a `base-url` property so a stub HTTP server can prove `MAX_TOOL_ROUNDS` terminates the loop. The golden suite is a gated `@SpringBootTest` that runs the real `GeminiHttpClient` against seeded H2 data and reads tool choices from the new interaction log.

**Tech Stack:** Spring Boot 3.5 / Java 21, SLF4J + Logback (`ListAppender` in tests), JUnit 5 + AssertJ + MockMvc, JDK `com.sun.net.httpserver.HttpServer` for the loop-cap stub.

**Spec:** `docs/superpowers/specs/2026-07-16-mirai-ops-hardening-design.md`

**Rules for every task** (AGENTS.md): TDD — failing test first, watch it fail for the right reason. Full `./mvnw test` green before every commit (Spotless + ArchUnit; if Spotless complains run `./mvnw spotless:apply`). Commit messages are plain sentences ending with `Co-Authored-By: <your model> <noreply@anthropic.com>`.

**Explicitly out of scope** (user decision): rate limiting, any DB storage of interactions, logging reply text.

---

### Task 1: `AssistantInteractionLog` component

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/AssistantInteractionLog.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantInteractionLogTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/api/AssistantInteractionLogTest.java` (plain JUnit — no Spring context needed):

```java
package com.softility.omivertex.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.softility.omivertex.service.AssistantInteractionLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantInteractionLogTest {

    private final AssistantInteractionLog interactionLog = new AssistantInteractionLog();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void record_writesOneParseableLine() {
        interactionLog.record("viewer@softility.com", AssistantInteractionLog.Outcome.ANSWERED,
                List.of("search_associates", "get_associate_detail"), 2413, "who is free for Acme?");

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("MIRAI user=viewer@softility.com");
        assertThat(line).contains("outcome=ANSWERED");
        assertThat(line).contains("tools=[search_associates, get_associate_detail]");
        assertThat(line).contains("latencyMs=2413");
        assertThat(line).contains("question=\"who is free for Acme?\"");
    }

    @Test
    void record_escapesQuotesInTheQuestion_andSurvivesNull() {
        interactionLog.record("system", AssistantInteractionLog.Outcome.ERROR, List.of(), 5,
                "find \"Priya\"");
        interactionLog.record("system", AssistantInteractionLog.Outcome.ERROR, List.of(), 5, null);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("question=\"find \\\"Priya\\\"\"");
        assertThat(appender.list.get(1).getFormattedMessage()).contains("question=\"\"");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=AssistantInteractionLogTest`
Expected: COMPILATION ERROR — `AssistantInteractionLog` does not exist. That is the right failure.

- [ ] **Step 3: Write the component**

Create `src/main/java/com/softility/omivertex/service/AssistantInteractionLog.java`:

```java
package com.softility.omivertex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One log line per assistant turn — the only record that Mirai was used.
 * Log-file only by design (privacy decision 2026-07-16): no DB row, and the
 * reply text is never written anywhere — the line says a question was
 * answered, not what the answer said. Failures here are swallowed; a logging
 * bug must never take the assistant down.
 */
@Component
public class AssistantInteractionLog {

    private static final Logger log = LoggerFactory.getLogger(AssistantInteractionLog.class);

    /** How the turn ended: prose reply, a draft to confirm, or an exception. */
    public enum Outcome { ANSWERED, DRAFTED, ERROR }

    public void record(String user, Outcome outcome, List<String> tools, long latencyMs,
                       String question) {
        try {
            String safeQuestion = question == null ? "" : question.replace("\"", "\\\"");
            log.info("MIRAI user={} outcome={} tools={} latencyMs={} question=\"{}\"",
                    user, outcome, tools, latencyMs, safeQuestion);
        } catch (RuntimeException e) {
            // deliberately swallowed — see class javadoc
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=AssistantInteractionLogTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite and commit**

Run: `./mvnw test` — expected PASS.

```bash
git add src/main/java/com/softility/omivertex/service/AssistantInteractionLog.java src/test/java/com/softility/omivertex/api/AssistantInteractionLogTest.java
git commit -m "Add the Mirai interaction log component (log-file only, never the reply)

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 2: Wire interaction logging through controller and service

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AuditService.java` (widen `currentUsername` to public)
- Modify: `src/main/java/com/softility/omivertex/web/AssistantController.java` (resolve username on servlet thread)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (signature + collection + logging)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `AssistantApiTest.java` — first these imports (some already present; add the missing ones):

```java
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.softility.omivertex.service.AssistantInteractionLog;
import org.slf4j.LoggerFactory;
```

then this helper and these three tests inside the class:

```java
private ListAppender<ILoggingEvent> attachInteractionAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
}

private void detachInteractionAppender(ListAppender<ILoggingEvent> appender) {
    ((Logger) LoggerFactory.getLogger(AssistantInteractionLog.class)).detachAppender(appender);
}

@Test
void chat_logsAnsweredTurnWithUserToolsAndQuestion_neverTheReply() throws Exception {
    associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // benched
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                ex.execute("search_associates", Map.of("benchOnly", true));
                return new GeminiClient.AssistantReply("Priya Sharma is on the bench.", null);
            });

    ListAppender<ILoggingEvent> appender = attachInteractionAppender();
    try {
        asyncPerform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer@softility.com").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on the bench?","history":[]}"""))
                .andExpect(status().isOk());

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("MIRAI user=viewer@softility.com");
        assertThat(line).contains("outcome=ANSWERED");
        assertThat(line).contains("tools=[search_associates]");
        assertThat(line).contains("question=\"who is on the bench?\"");
        assertThat(line).contains("latencyMs=");
        // privacy pin: the reply text is never logged
        assertThat(line).doesNotContain("Priya Sharma is on the bench.");
    } finally {
        detachInteractionAppender(appender);
    }
}

@Test
void chat_logsDraftedWhenAProposedActionIsReturned() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenReturn(new GeminiClient.AssistantReply("Here you go.",
                    new GeminiClient.ActionCall("propose_allocation",
                            Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp"))));

    ListAppender<ILoggingEvent> appender = attachInteractionAppender();
    try {
        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"allocate priya to storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("CREATE_ALLOCATION"));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("outcome=DRAFTED");
    } finally {
        detachInteractionAppender(appender);
    }
}

@Test
void chat_logsErrorWhenTheTurnThrows_andTheErrorStillReachesTheClient() throws Exception {
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenThrow(new com.softility.omivertex.web.error.BadRequestException(
                    "The AI assistant is unavailable right now — try again shortly"));

    ListAppender<ILoggingEvent> appender = attachInteractionAppender();
    try {
        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":[]}"""))
                .andExpect(status().isBadRequest());

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("outcome=ERROR");
        assertThat(line).contains("question=\"hello\"");
    } finally {
        detachInteractionAppender(appender);
    }
}
```

Note: the existing tests post without a user; for those the logged user falls back to `"system"` — they don't assert on the log, so they stay untouched.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantApiTest`
Expected: the three new tests FAIL with `appender.list` empty (size 0 instead of 1) — nothing logs yet. Pre-existing tests still pass.

- [ ] **Step 3: Widen `AuditService.currentUsername` to public**

In `src/main/java/com/softility/omivertex/service/AuditService.java`, change:

```java
private static String currentUsername() {
```

to:

```java
/** The acting principal, "system" when unauthenticated — shared with the assistant interaction log. */
public static String currentUsername() {
```

(One implementation of the actor rule; the controller reuses it rather than re-deriving it.)

- [ ] **Step 4: Pass the username from the controller (servlet thread)**

In `src/main/java/com/softility/omivertex/web/AssistantController.java`, add the import `com.softility.omivertex.service.AuditService` and change the endpoint method to:

```java
/** Async on the AI bulkhead: the servlet thread is freed while Gemini responds. */
@PostMapping("/chat")
public CompletableFuture<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
    // Resolved here, on the servlet thread: the ai-* pool never sees the SecurityContext.
    String username = AuditService.currentUsername();
    return aiExecutor.submit(() -> assistantService.chat(request, username));
}
```

- [ ] **Step 5: Collect tools, time the turn, and log in `AssistantService`**

In `src/main/java/com/softility/omivertex/service/AssistantService.java`:

(a) Add the field and constructor parameter (keep all existing ones; shown in full):

```java
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
```

(b) Replace the `chat` method with (the tool-collecting decorator wraps `executeReadTool`; the log call runs on every exit path):

```java
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
```

(Semantics pinned by the tests: a draft whose name-resolution failed comes back with `proposedAction == null` and is logged `ANSWERED` — it is a clarifying reply, not a draft.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AssistantApiTest`
Expected: PASS — all tests including the three new ones.

- [ ] **Step 7: Run the full suite and commit**

Run: `./mvnw test` — expected PASS (the only caller of `chat()` was the controller, updated above).

```bash
git add src/main/java/com/softility/omivertex src/test/java/com/softility/omivertex
git commit -m "Log every Mirai turn: user, outcome, tools, latency, question — never the reply

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 3: Tool-loop cap test (+ base-url property)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (endpoint from property)
- Create: `src/test/java/com/softility/omivertex/api/GeminiToolLoopCapTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/api/GeminiToolLoopCapTest.java` (plain JUnit — constructs the client directly, no Spring):

```java
package com.softility.omivertex.api;

import com.softility.omivertex.service.GeminiClient;
import com.softility.omivertex.service.GeminiHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins MAX_TOOL_ROUNDS: even against a model that requests a tool on every
 * round forever, one assistant turn performs a bounded number of upstream
 * calls and returns. Without this cap a confused model means an infinite
 * paid-API loop.
 */
class GeminiToolLoopCapTest {

    private HttpServer server;

    @Test
    @Timeout(10) // a regression here would otherwise hang the suite
    void replyWithTools_terminatesAfterMaxToolRounds_evenIfTheModelNeverStops() throws Exception {
        AtomicInteger apiCalls = new AtomicInteger();
        String alwaysAskForATool = """
                {"candidates":[{"content":{"parts":[{"functionCall":{"name":"search_associates","args":{}}}]}}]}""";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            apiCalls.incrementAndGet();
            byte[] body = alwaysAskForATool.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        GeminiHttpClient client = new GeminiHttpClient("test-key", "test-model",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        AtomicInteger toolRuns = new AtomicInteger();

        GeminiClient.AssistantReply reply = client.replyWithTools("context", List.of(),
                "who is on the bench?", (name, args) -> {
                    toolRuns.incrementAndGet();
                    return "rows";
                });

        // MAX_TOOL_ROUNDS = 3: the initial call plus three tool rounds, then a forced return
        assertThat(apiCalls.get()).isEqualTo(4);
        assertThat(toolRuns.get()).isEqualTo(3);
        // the uncompleted read-tool call surfaces as an ActionCall (documented fall-through:
        // AssistantService answers it with the polite "can't do that" fallback)
        assertThat(reply.action()).isNotNull();
        assertThat(reply.action().name()).isEqualTo("search_associates");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=GeminiToolLoopCapTest`
Expected: COMPILATION ERROR — `GeminiHttpClient` has no 5-arg constructor with a base URL. That is the right failure.

- [ ] **Step 3: Make the endpoint base configurable**

In `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`:

(a) Replace the constant

```java
// v1beta, deliberately: the stable v1 surface rejects systemInstruction
// ("Unknown name") — verified against the live API 2026-07-11.
private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
```

with a path-only constant plus an instance field:

```java
// v1beta, deliberately: the stable v1 surface rejects systemInstruction
// ("Unknown name") — verified against the live API 2026-07-11.
private static final String GENERATE_PATH = "/v1beta/models/%s:generateContent";

private final String endpoint;
```

(b) Change the constructor signature to accept the base URL (new third parameter) and build the endpoint:

```java
public GeminiHttpClient(
        @Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
        @Value("${omivertex.assistant.gemini.model:gemini-3.1-flash-lite}") String model,
        @Value("${omivertex.assistant.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
        @Value("${omivertex.assistant.gemini.connect-timeout:5s}") Duration connectTimeout,
        @Value("${omivertex.assistant.gemini.read-timeout:30s}") Duration readTimeout) {
```

and inside the constructor body, after `this.model = model;`, add:

```java
this.endpoint = baseUrl + GENERATE_PATH; // overridable for tests; default is byte-identical to before
```

(c) Replace **both** uses of `ENDPOINT.formatted(model)` (one in `callApi`, one in `extractResume`) with `endpoint.formatted(model)`.

(d) In `src/main/resources/application.properties`, next to the other `omivertex.assistant.gemini.*` entries, add:

```properties
omivertex.assistant.gemini.base-url=${OMIVERTEX_ASSISTANT_GEMINI_BASE_URL:https://generativelanguage.googleapis.com}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=GeminiToolLoopCapTest`
Expected: PASS.

- [ ] **Step 5: Run the full suite and commit**

Run: `./mvnw test` — expected PASS (default base-url keeps production behavior identical).

```bash
git add src/main/java/com/softility/omivertex/service/GeminiHttpClient.java src/main/resources/application.properties src/test/java/com/softility/omivertex/api/GeminiToolLoopCapTest.java
git commit -m "Pin the Gemini tool-loop cap with an HTTP-level test; make the base URL a property

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 4: Golden-question eval suite (opt-in, live model)

**Files:**
- Create: `src/test/java/com/softility/omivertex/api/MiraiGoldenQuestionsTest.java`

- [ ] **Step 1: Write the suite**

(No red-green cycle here — this task creates an *evaluation harness*, not production behavior; its "failure mode" is a live-model regression. TDD's failing-test-first applies to Tasks 1–3, which change production code. This task must leave `./mvnw test` untouched: the class is skipped unless explicitly enabled.)

Create `src/test/java/com/softility/omivertex/api/MiraiGoldenQuestionsTest.java`:

```java
package com.softility.omivertex.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Certification;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssistantInteractionLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-question eval against the LIVE Gemini model — Mirai's regression
 * exam for prompt/tool changes. Never part of a normal build: requires
 * MIRAI_GOLDEN_EVAL=true and a real API key, and costs a few paid calls.
 *
 * Run: MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest
 *
 * Assertions check PROPERTIES of the reply (a seeded name present, a warning
 * present, a tool called — tool choice read from the interaction log), never
 * exact prose: the suite must not flake on wording.
 */
@EnabledIfEnvironmentVariable(named = "MIRAI_GOLDEN_EVAL", matches = "true")
@EnabledIfEnvironmentVariable(named = "OMIVERTEX_ASSISTANT_GEMINI_API_KEY", matches = ".+")
@TestPropertySource(properties =
        "omivertex.assistant.gemini.api-key=${OMIVERTEX_ASSISTANT_GEMINI_API_KEY:}")
class MiraiGoldenQuestionsTest extends ApiTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Logger interactionLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void seedGoldenWorkforceAndAttachAppender() {
        var acme = client("Golden Client");
        var proj = project("GLD-100", "Golden Project", acme);
        var busy = associate("Golden Busy", "golden.busy@softility.com", WorkMode.ONSHORE);
        allocation(busy, proj, true); // 100% billable — any extra allocation must warn
        associate("Golden Bench", "golden.bench@softility.com", WorkMode.OFFSHORE); // benched
        var certHolder = associate("Golden Certified", "golden.cert@softility.com", WorkMode.ONSHORE);
        var cert = new Certification();
        cert.setAssociate(certHolder);
        cert.setName("Golden Cloud Practitioner");
        cert.setExpiryDate(LocalDate.now().plusDays(30));
        certificationRepository.save(cert);

        interactionLogger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
        appender = new ListAppender<>();
        appender.start();
        interactionLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        interactionLogger.detachAppender(appender);
    }

    private JsonNode ask(String question) throws Exception {
        MvcResult result = asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                java.util.Map.of("message", question, "history", List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private String loggedToolsOfLastTurn() {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void bench_question_namesTheBenchedAssociate_viaARosterTool() throws Exception {
        JsonNode response = ask("Who is on the bench right now?");

        assertThat(response.get("reply").asText()).contains("Golden Bench");
        // either roster tool is a legitimate choice for this question
        assertThat(loggedToolsOfLastTurn()).containsAnyOf("search_associates", "list_bench_aging");
    }

    @Test
    void certification_question_usesTheCertTool_andNamesTheHolder() throws Exception {
        JsonNode response = ask("Whose certifications expire in the next 60 days?");

        assertThat(response.get("reply").asText()).contains("Golden Certified");
        assertThat(loggedToolsOfLastTurn()).contains("list_expiring_certifications");
    }

    @Test
    void overCapacityAllocation_isDraftedWithAWarning_andMutatesNothing() throws Exception {
        long allocationsBefore = allocationRepository.count();

        JsonNode response = ask("Allocate Golden Busy to Golden Project at 50%");

        JsonNode action = response.get("proposedAction");
        assertThat(action).isNotNull();
        assertThat(action.get("warnings").toString()).contains("over 100%");
        assertThat(allocationRepository.count()).isEqualTo(allocationsBefore); // draft-only, nothing mutated
    }

    @Test
    void unanswerable_question_doesNotInventAnAnswerFromTheRoster() throws Exception {
        JsonNode response = ask("Who is our CEO?");

        // the exam is the absence of invention, not exact refusal wording
        String reply = response.get("reply").asText();
        assertThat(reply).doesNotContain("Golden Bench");
        assertThat(reply).doesNotContain("Golden Busy");
        assertThat(reply).doesNotContain("Golden Certified");
    }
}
```

- [ ] **Step 2: Verify the gate — normal build skips it**

Run: `./mvnw test -Dtest=MiraiGoldenQuestionsTest`
Expected: BUILD SUCCESS with the class reported as **skipped** (disabled by `@EnabledIfEnvironmentVariable`), zero API calls.

- [ ] **Step 3: Live run (only if you have the key in your environment)**

Run: `MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest`
Expected: 4 tests PASS against the live model. If a live assertion fails, report it — do not weaken the assertion to pass; a failure here is the suite doing its job (it means the current prompt/tool setup genuinely mishandles that golden question).
If you have no `OMIVERTEX_ASSISTANT_GEMINI_API_KEY` available, note the live run as not executed in your report — the gate test in Step 2 is the mergeable verification.

- [ ] **Step 4: Run the full suite and commit**

Run: `./mvnw test` — expected PASS, `MiraiGoldenQuestionsTest` skipped.

```bash
git add src/test/java/com/softility/omivertex/api/MiraiGoldenQuestionsTest.java
git commit -m "Add the opt-in golden-question eval suite for Mirai (MIRAI_GOLDEN_EVAL=true)

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 5: Docs, graph, final verification, plan deletion

**Files:**
- Modify: `docs/TECHNICAL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Update `docs/TECHNICAL.md`**

(a) In the numbered "AI assistant" item (~line 148), append:

```
Every turn writes one interaction-log line (`AssistantInteractionLog`,
log-file only — no DB, and never the reply text):
`MIRAI user=… outcome=ANSWERED|DRAFTED|ERROR tools=[…] latencyMs=… question="…"`.
```

(b) In the "AI execution model" section (~line 219), after the timeout sentence, add:

```
The Gemini endpoint base is configurable
(`omivertex.assistant.gemini.base-url`, default
`https://generativelanguage.googleapis.com`) so tests can stub the upstream;
`GeminiToolLoopCapTest` pins that one turn performs at most
`MAX_TOOL_ROUNDS + 1` upstream calls. A live golden-question eval
(`MiraiGoldenQuestionsTest`) runs only with `MIRAI_GOLDEN_EVAL=true` plus a
real API key: `MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest`.
```

(Match the file's prose style and wrapping; place naturally within the existing text.)

- [ ] **Step 2: Record the decisions in `docs/TODO.md`**

Under `## Resolved decisions`, add at the top of the list:

```
- **Mirai interactions are logged, not stored** (2026-07-16): one log line per
  assistant turn (user, outcome, tools, latency, question) through Logback into
  the rotating app log. Deliberately no DB table (user decision: storage is
  waste at this scale) and the reply text is never written anywhere — roster
  data must not get a second home outside the entity tables. Revisit only if
  usage analytics need querying.
- **Assistant rate limiting consciously deferred** (2026-07-16): the AiExecutor
  bulkhead bounds concurrency but nothing bounds per-user request volume/spend.
  Descoped from the ops-hardening phase by user decision; add before widening
  assistant access beyond the current user base.
```

- [ ] **Step 3: Refresh the knowledge graph**

```bash
cd /Users/bhargavasista/omivertex
$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```

(`graphify-out/` is gitignored — the refresh updates local files only; nothing to stage.)

- [ ] **Step 4: Final verification**

Run: `./mvnw test` — expected BUILD SUCCESS, 0 failures (Task 1 added 2 tests, Task 2 added 3, Task 3 added 1; `MiraiGoldenQuestionsTest` skipped).
No frontend changes in this phase — no npm build needed.

- [ ] **Step 5: Commit docs**

```bash
git add docs/TECHNICAL.md docs/TODO.md
git commit -m "Document the Mirai interaction log, loop-cap test, and golden eval; record the log-not-DB decision

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

- [ ] **Step 6: Delete this plan (plans are disposable scaffolding)**

```bash
git rm docs/superpowers/plans/2026-07-16-mirai-ops-hardening.md
git commit -m "Remove the merged Mirai ops-hardening plan

Co-Authored-By: <your model> <noreply@anthropic.com>"
```
