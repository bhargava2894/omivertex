# Mirai Write Expansion + Role-Aware Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three new draft verbs (`propose_end_allocation`, `propose_edit_allocation`, `propose_position`), a `Caller(username, admin)` role seam, and two admin-only read tools (`list_pending_approvals`, `get_audit_history`) — with the draft-only safety contract untouched.

**Architecture:** A `Caller` record replaces the bare username through both chat endpoints; `GeminiClient.replyWithTools` gains a `boolean adminTools` so admin declarations are sent only to admins (with dispatch-level defense in depth). The three write verbs follow the existing resolve→pre-validate→`ProposedAction` pattern in `AssistantService`; confirms reuse the existing `PUT /allocations/{id}` and `POST /positions` endpoints from the browser. Admin formatters live in `AssistantContextBuilder` like every read tool.

**Tech Stack:** Spring Boot 3.5 / Java 21, JUnit 5 + AssertJ + MockMvc + Mockito, React 18.

**Spec:** `docs/superpowers/specs/2026-07-17-mirai-write-expansion-design.md`

**Rules for every task** (AGENTS.md): TDD — failing test first, right reason (Task 1 is a pure signature refactor: the existing suite is its net). Full `./mvnw test` green before every commit (Spotless + ArchUnit; `./mvnw spotless:apply` if needed). Commit messages are plain sentences ending `Co-Authored-By: <your model> <noreply@anthropic.com>`.

**Verified shapes used throughout** (do not re-derive): `AllocationUpdateRequest(billable, allocationPercent, startDate, endDate)` at `PUT /allocations/{id}`; `PositionRequest(title, projectId, requiredSkill, workMode, skills[SkillReq(skillId, minProficiency, required)], billable, allocationPercent, headcount, …)` at `POST /positions`; `ProfileChangeRequestRepository.findAllByStatus(ProfileChangeStatus)`; `AppUser.getStatus()==AccessStatus.PENDING` (no finder — filter `findAll()`); `AuditEntryRepository.findAllByOrderByIdDesc(Pageable)` / `findByEntityTypeOrderByIdDesc(String, Pageable)`; `AuditEntry` getters `getTimestamp()/getUsername()/getAction()/getEntityType()/getEntityId()/getSummary()`; `ProfileChangeRequest.getAssociate()/getType()/getCreatedAt()` (Instant).

---

### Task 1: `Caller` seam + `adminTools` parameter (pure refactor, suite stays green)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/web/AssistantController.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiClient.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`
- Modify: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java` (mock matchers only)
- Modify: `src/test/java/com/softility/omivertex/api/AssistantStreamApiTest.java` (mock matchers only)
- Modify: `src/test/java/com/softility/omivertex/api/GeminiToolLoopCapTest.java` (call sites only)

- [ ] **Step 1: `AssistantService` — Caller record + signatures.** Inside the class add:

```java
/** Who is asking — resolved on the servlet thread, since the ai-* pool cannot. */
public record Caller(String username, boolean admin) {}
```

Change the three chat signatures (bodies otherwise unchanged; `username` uses become `caller.username()`):

```java
public AssistantChatResponse chat(AssistantChatRequest request, Caller caller) {
    return chat(request, caller, ToolProgressListener.NONE);
}

public AssistantChatResponse chat(AssistantChatRequest request, Caller caller,
                                  ToolProgressListener progress) {
```

Inside the 3-arg body: both `interactionLog.record(username, …)` calls become `interactionLog.record(caller.username(), …)`, and the Gemini call passes the flag:

```java
GeminiClient.AssistantReply reply = geminiClient.replyWithTools(
        contextBuilder.build(), turns, request.message(), (name, args) -> {
            toolsCalled.add(name);
            progress.toolCalled(name);
            return executeReadTool(name, args);
        }, caller.admin());
```

- [ ] **Step 2: `GeminiClient` interface** — the method becomes:

```java
/**
 * One assistant turn with tool support. Read tools run via {@code tools};
 * a write tool surfaces as {@link AssistantReply#action()} for the caller
 * to turn into a user-confirmable draft. Never mutates anything itself.
 * {@code adminTools} controls whether admin-only tools are declared to the
 * model at all — a non-admin's model never sees them.
 */
AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                              String userMessage, ToolExecutor tools, boolean adminTools);
```

- [ ] **Step 3: `GeminiHttpClient`** — add the parameter (accepted, unused for now; Task 3 wires it):

```java
public AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                     String userMessage, ToolExecutor tools, boolean adminTools) {
```

- [ ] **Step 4: `AssistantController`** — both endpoints resolve a `Caller`. Add imports `org.springframework.security.core.Authentication` and `org.springframework.security.core.context.SecurityContextHolder`, plus this helper:

```java
/** Role + username, resolved on the servlet thread — the ai-* pool never sees the SecurityContext. */
private static AssistantService.Caller currentCaller() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean admin = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    return new AssistantService.Caller(AuditService.currentUsername(), admin);
}
```

In `chat`: replace the username resolution with `AssistantService.Caller caller = currentCaller();` and call `assistantService.chat(request, caller)`. In `chatStream`: same, calling `assistantService.chat(request, caller, name -> send(emitter, "tool", name))`.

- [ ] **Step 5: Update test mocks.** In `AssistantApiTest` and `AssistantStreamApiTest`, every
`replyWithTools(anyString(), anyList(), anyString(), any())` (in `when(...)` AND `verify(...)`) becomes
`replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean())` — add
`import static org.mockito.ArgumentMatchers.anyBoolean;` to both. In `GeminiToolLoopCapTest`, both
`client.replyWithTools("…", List.of(), "…", (name, args) -> …)` calls gain a final `, false)` argument.

- [ ] **Step 6: Full suite** — `./mvnw test` → PASS (refactor-only; the suite is the net).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/softility/omivertex src/test/java/com/softility/omivertex
git commit -m "Thread the caller's role through the assistant: Caller record + adminTools flag

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 2: Admin formatters — `pendingApprovals()` and `auditHistory()`

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`

- [ ] **Step 1: Failing tests** — add to `AssistantContextBuilderTest` (repositories `profileChangeRequestRepository` and `appUserRepository` are already `@Autowired` in `ApiTestBase`; also add `@Autowired AuditEntryRepository auditEntryRepository` is NOT needed — audit rows are seeded through `auditEntryRepository` which IS in `ApiTestBase`):

```java
@Test
void pendingApprovals_listsBothQueues() {
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    var change = new com.softility.omivertex.domain.ProfileChangeRequest();
    change.setAssociate(priya);
    change.setType(com.softility.omivertex.domain.ProfileChangeType.SKILLS);
    profileChangeRequestRepository.save(change);
    var user = new com.softility.omivertex.domain.AppUser();
    user.setEmail("new.hire@softility.com");
    user.setName("New Hire");
    appUserRepository.save(user); // status defaults to PENDING

    String result = builder.pendingApprovals();

    assertThat(result).contains("Profile changes pending");
    assertThat(result).contains("Priya Sharma").contains("SKILLS");
    assertThat(result).contains("Access requests pending");
    assertThat(result).contains("New Hire (new.hire@softility.com)");
}

@Test
void pendingApprovals_emptyState() {
    assertThat(builder.pendingApprovals()).contains("Nothing is waiting for approval");
}

@Test
void auditHistory_newestFirst_filtersByEntityType_andCaps() {
    for (int i = 1; i <= 3; i++) {
        var e = new com.softility.omivertex.domain.AuditEntry();
        e.setUsername("admin");
        e.setAction("UPDATE");
        e.setEntityType(i == 3 ? "Project" : "Allocation");
        e.setEntityId((long) i);
        e.setSummary("change " + i);
        auditEntryRepository.save(e);
    }

    String all = builder.auditHistory(null, 25);
    assertThat(all.indexOf("change 3")).isLessThan(all.indexOf("change 1")); // newest first
    String filtered = builder.auditHistory("Allocation", 25);
    assertThat(filtered).contains("change 2").doesNotContain("change 3");
    String capped = builder.auditHistory(null, 2);
    assertThat(capped).contains("change 3").contains("change 2").doesNotContain("change 1");
}

@Test
void auditHistory_emptyStateNamesTheFilter() {
    assertThat(builder.auditHistory("Client", 25)).contains("No audit entries for type \"Client\"");
}
```

(If `AuditEntry` requires `timestamp` non-null on save, set `e.setTimestamp(java.time.Instant.now())` — check the entity; `AuditService.record` relies on a `@PrePersist` or explicit set. Adapt the seed accordingly and note it in your report.)

- [ ] **Step 2: Run** `./mvnw test -Dtest=AssistantContextBuilderTest` — COMPILATION ERROR (methods missing). Right failure.

- [ ] **Step 3: Implement.** In `AssistantContextBuilder`: add constructor deps (append, with fields + assignments, same pattern as the existing ten):
`ProfileChangeRequestRepository profileChanges`, `AppUserRepository appUsers`, `AuditEntryRepository auditEntries`. Add imports for those repositories plus `com.softility.omivertex.domain.AccessStatus`, `com.softility.omivertex.domain.AppUser`, `com.softility.omivertex.domain.AuditEntry`, `com.softility.omivertex.domain.ProfileChangeRequest`, `com.softility.omivertex.domain.ProfileChangeStatus`, `java.time.ZoneId`, `org.springframework.data.domain.PageRequest`. Then the formatters (after `positionMatchSummary`):

```java
/** ADMIN-only read tool: everything waiting on an admin — profile changes + access requests. */
public String pendingApprovals() {
    List<ProfileChangeRequest> changes = profileChanges.findAllByStatus(ProfileChangeStatus.PENDING);
    List<AppUser> access = appUsers.findAll().stream()
            .filter(u -> u.getStatus() == AccessStatus.PENDING).toList();
    if (changes.isEmpty() && access.isEmpty()) {
        return "Nothing is waiting for approval.";
    }
    StringBuilder sb = new StringBuilder();
    if (!changes.isEmpty()) {
        sb.append("Profile changes pending:\n");
        for (ProfileChangeRequest c : changes.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(c.getAssociate().getName())
              .append(" · ").append(c.getType())
              .append(c.getCreatedAt() == null ? "" : " · requested "
                      + c.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
              .append("\n");
        }
        appendOverflow(sb, changes.size());
    }
    if (!access.isEmpty()) {
        sb.append("Access requests pending:\n");
        for (AppUser u : access.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(u.getName() == null || u.getName().isBlank()
                    ? u.getEmail() : u.getName() + " (" + u.getEmail() + ")").append("\n");
        }
        appendOverflow(sb, access.size());
    }
    return sb.toString();
}

/** ADMIN-only read tool: recent audit entries, newest first, optionally one entity type. */
public String auditHistory(String entityType, int limit) {
    int cap = Math.min(Math.max(limit, 1), MAX_TOOL_ROWS);
    PageRequest page = PageRequest.of(0, cap);
    List<AuditEntry> entries = entityType == null || entityType.isBlank()
            ? auditEntries.findAllByOrderByIdDesc(page)
            : auditEntries.findByEntityTypeOrderByIdDesc(entityType.trim(), page);
    if (entries.isEmpty()) {
        return entityType == null || entityType.isBlank() ? "No audit entries."
                : "No audit entries for type \"" + entityType + "\".";
    }
    return entries.stream()
            .map(e -> "- " + (e.getTimestamp() == null ? "" : e.getTimestamp() + " · ")
                    + e.getUsername() + " · " + e.getAction() + " " + e.getEntityType()
                    + (e.getEntityId() == null ? "" : "#" + e.getEntityId())
                    + " · " + e.getSummary())
            .collect(Collectors.joining("\n"));
}
```

- [ ] **Step 4: Run** the test class → PASS. **Step 5: Full suite + commit**

```bash
git add -A src/main src/test
git commit -m "Add admin-only formatters: pending approvals and audit history

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 3: Role-aware registration — declarations, loop gating, dispatch gating, prompt

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (build overload)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Create: `src/test/java/com/softility/omivertex/api/GeminiRoleDeclarationsTest.java`

- [ ] **Step 1: Failing dispatch tests** — add to `AssistantApiTest` (the shared-account "admin" login IS an admin; the existing tests post without `.with(user())` and run as admin by default — verify by checking `chat_answersWithWorkforceContext` logs `user=admin`. Viewer via `SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")`):

```java
@Test
void chat_adminToolWorksForAdmin_andIsUnknownForViewer() throws Exception {
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(
                        ex.execute("list_pending_approvals", Map.of()), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"what needs my attention?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Nothing is waiting for approval")));

    asyncPerform(post("/api/v1/assistant/chat")
                    .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"what needs my attention?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Unknown tool: list_pending_approvals")));
}

@Test
void chat_auditHistoryToolIsAdminOnly() throws Exception {
    var e = new com.softility.omivertex.domain.AuditEntry();
    e.setUsername("admin");
    e.setAction("CREATE");
    e.setEntityType("Client");
    e.setEntityId(7L);
    e.setSummary("created Acme");
    auditEntryRepository.save(e);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(
                        ex.execute("get_audit_history", Map.of("entityType", "Client")), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"who created acme?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("created Acme")));
}
```

Add to `AssistantContextBuilderTest`:

```java
@Test
void standingContext_advertisesAdminToolsOnlyForAdmins() {
    seedWorkforce();
    assertThat(builder.build(true)).contains("list_pending_approvals").contains("get_audit_history");
    assertThat(builder.build()).doesNotContain("list_pending_approvals");
}
```

- [ ] **Step 2: Run both classes** — FAIL (`build(boolean)` missing; dispatch returns Unknown tool for admin). Right failures.

- [ ] **Step 3: Implement.**

(a) `AssistantContextBuilder` — `build()` delegates; the tool-list sentence gains a conditional clause:

```java
/** Standing context: instructions + aggregate counts. Never roster rows. */
public String build() {
    return build(false);
}

public String build(boolean adminTools) {
```

and inside, after the existing tool-list fragment `+ "get_workforce_summary, list_bench_aging, get_position_match_summary) "`, insert:

```java
+ (adminTools
        ? "As an admin you can also use list_pending_approvals (what awaits "
                + "approval) and get_audit_history (who changed what, when). "
        : "")
```

(b) `AssistantService` — the 3-arg `chat` builds the context with the role: `contextBuilder.build(caller.admin())`, and the executor lambda passes the flag through a widened private dispatch:

```java
}, caller.admin());
```

stays as is; change `executeReadTool` to take the flag — `private String executeReadTool(String name, Map<String, Object> args, boolean admin)` — update the lambda to `executeReadTool(name, args, caller.admin())`, and add the two gated cases before `default`:

```java
case "list_pending_approvals" -> admin ? contextBuilder.pendingApprovals()
        : "Unknown tool: " + name;
case "get_audit_history" -> admin ? contextBuilder.auditHistory(
                str(args, "entityType"),
                intOrDefault(args.get("limit"), AssistantContextBuilder.MAX_TOOL_ROWS))
        : "Unknown tool: " + name;
```

(`MAX_TOOL_ROWS` is package-private static in the same package.)

(c) `GeminiHttpClient` — next to `READ_TOOLS` add:

```java
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
```

Replace the tool payload at ALL THREE `callApi` sites in `replyWithTools` (initial call, tool-round call is the same map — there is one construction inside the loop plus the wrap-up call; update every `List.of(Map.of("functionDeclarations", FUNCTION_DECLARATIONS))` in that method) with
`List.of(Map.of("functionDeclarations", declarationsFor(adminTools)))`. Change the read-tool gate to:

```java
if ((READ_TOOLS.contains(name) || (adminTools && ADMIN_READ_TOOLS.contains(name)))
        && tools != null) {
```

- [ ] **Step 4: Declarations wire test** — create `src/test/java/com/softility/omivertex/api/GeminiRoleDeclarationsTest.java` (loop-cap style; captures the request body actually sent to Gemini):

```java
package com.softility.omivertex.api;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins role filtering at the wire: a viewer's request body never declares admin tools. */
class GeminiRoleDeclarationsTest {

    private static final String PROSE = """
            {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""";

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");

    private GeminiHttpClient clientAgainstStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = PROSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return new GeminiHttpClient("test-key", "test-model",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void adminToolsAreDeclaredOnlyForAdmins() throws Exception {
        GeminiHttpClient client = clientAgainstStub();

        client.replyWithTools("ctx", List.of(), "hi", (n, a) -> "rows", false);
        assertThat(lastBody.get()).doesNotContain("list_pending_approvals");
        assertThat(lastBody.get()).contains("search_associates"); // base set still there

        client.replyWithTools("ctx", List.of(), "hi", (n, a) -> "rows", true);
        assertThat(lastBody.get()).contains("list_pending_approvals").contains("get_audit_history");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 5: Run all four touched test classes** → PASS. **Step 6: Full suite + commit**

```bash
git add -A src/main src/test
git commit -m "Register admin-only assistant tools by role: declarations, loop, and dispatch all gate on the caller

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 4: `propose_end_allocation` (+ ProposedAction gains the new fields once)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssistantChatResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Failing tests** — add to `AssistantApiTest`:

```java
@Test
void chat_draftsEndAllocation_withResolvedAllocationAndDefaultToday() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    var alloc = allocation(priya, proj, true);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("Draft ready.",
                    new GeminiClient.ActionCall("propose_end_allocation",
                            Map.of("associateName", "Priya Sharma",
                                    "projectName", "Storefront Revamp"))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"roll priya off storefront","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.type").value("END_ALLOCATION"))
            .andExpect(jsonPath("$.proposedAction.allocationId").value(alloc.getId().intValue()))
            .andExpect(jsonPath("$.proposedAction.endDate").value(java.time.LocalDate.now().toString()))
            .andExpect(jsonPath("$.proposedAction.summary",
                    containsString("End Priya Sharma's allocation on Storefront Revamp")));
}

@Test
void chat_endAllocation_noCurrentAllocation_asksBack() throws Exception {
    var acme = client("Acme Corp");
    project("ACM-100", "Storefront Revamp", acme);
    associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // benched
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_end_allocation",
                            Map.of("associateName", "Priya Sharma",
                                    "projectName", "Storefront Revamp"))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"roll priya off storefront","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction").doesNotExist())
            .andExpect(jsonPath("$.reply",
                    containsString("no current allocation on Storefront Revamp")));
}

@Test
void chat_endAllocation_endBeforeStart_isRejectedAtDraftTime() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    allocation(priya, proj, true); // started 3 months ago
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_end_allocation",
                            Map.of("associateName", "Priya Sharma",
                                    "projectName", "Storefront Revamp",
                                    "endDate", java.time.LocalDate.now().minusMonths(6).toString()))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"end it half a year ago","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction").doesNotExist())
            .andExpect(jsonPath("$.reply", containsString("can't be before")));
}
```

- [ ] **Step 2: Run** — FAIL: reply is "I can't do that yet — …" (unknown action) and `allocationId` doesn't exist. Right failure.

- [ ] **Step 3: DTO — add the four new fields ONCE** (Tasks 5–6 reuse them). In `AssistantChatResponse`:

```java
public enum ActionType { CREATE_ALLOCATION, FILL_POSITION, END_ALLOCATION, EDIT_ALLOCATION, CREATE_POSITION }

/** A draft the user must confirm in the UI; confirming calls the existing endpoints. */
public record ProposedAction(ActionType type,
                             Long associateId, String associateName,
                             Long projectId, String projectName,
                             Long positionId, String positionTitle,
                             Integer percent, Boolean billable,
                             LocalDate startDate, LocalDate endDate,
                             Long allocationId,
                             Long skillId, String skillName,
                             com.softility.omivertex.domain.Proficiency minProficiency,
                             String summary, List<String> warnings) {
}
```

Update the two existing constructor calls in `AssistantService` (`draftAllocation`, `draftFill`): insert `null, null, null, null,` between `endDate`/`pos.getEndDate()` and `summary`.

- [ ] **Step 4: Implement the draft.** In `AssistantService`:

(a) `draft(...)` switch gains `case "propose_end_allocation" -> draftEndAllocation(reply.text(), args);`

(b) Add (near the other resolvers):

```java
private record ResolvedAllocation(Allocation value, String reply) {}

/** The associate's CURRENT allocation on the named project — at most one exists. */
private ResolvedAllocation resolveCurrentAllocation(String associateName, String projectName) {
    Resolved<Associate> associate = resolveAssociate(associateName);
    if (associate.reply() != null) {
        return new ResolvedAllocation(null, associate.reply());
    }
    Resolved<Project> project = resolveProject(projectName);
    if (project.reply() != null) {
        return new ResolvedAllocation(null, project.reply());
    }
    Allocation current = allocationRepository.findByAssociateId(associate.value().getId()).stream()
            .filter(Allocation::isCurrent)
            .filter(a -> a.getProject().getId().equals(project.value().getId()))
            .findFirst().orElse(null);
    if (current == null) {
        return new ResolvedAllocation(null, "%s has no current allocation on %s."
                .formatted(associate.value().getName(), project.value().getName()));
    }
    return new ResolvedAllocation(current, null);
}

private AssistantChatResponse draftEndAllocation(String text, Map<String, Object> args) {
    ResolvedAllocation resolved = resolveCurrentAllocation(
            str(args, "associateName"), str(args, "projectName"));
    if (resolved.reply() != null) {
        return new AssistantChatResponse(resolved.reply(), null);
    }
    Allocation al = resolved.value();
    LocalDate end = dateOrDefault(args.get("endDate"), LocalDate.now());
    if (end.isBefore(al.getStartDate())) {
        return new AssistantChatResponse(
                "That allocation started on %s — the end date can't be before that."
                        .formatted(al.getStartDate()), null);
    }
    String summary = "End %s's allocation on %s effective %s".formatted(
            al.getAssociate().getName(), al.getProject().getName(), end);
    ProposedAction proposed = new ProposedAction(ActionType.END_ALLOCATION,
            al.getAssociate().getId(), al.getAssociate().getName(),
            al.getProject().getId(), al.getProject().getName(),
            null, null, al.getAllocationPercent(), al.isBillable(),
            al.getStartDate(), end, al.getId(), null, null, null, summary, List.of());
    return new AssistantChatResponse(nonBlank(text,
            "Here's the draft — review and confirm below."), proposed);
}
```

(c) `GeminiHttpClient.FUNCTION_DECLARATIONS` gains (after `propose_position_fill`):

```java
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
```

- [ ] **Step 5: Run the class** → PASS. **Step 6: Full suite + commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a propose_end_allocation draft: roll-offs by name, confirmed in the browser

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 5: `propose_edit_allocation` (self-excluding capacity warning)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Failing tests:**

```java
@Test
void chat_draftsEditAllocation_mergingOverCurrentValues() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    var alloc = allocation(priya, proj, true); // 100%, billable, no end date
    alloc.setAllocationPercent(50);
    allocationRepository.save(alloc);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_edit_allocation",
                            Map.of("associateName", "Priya Sharma",
                                    "projectName", "Storefront Revamp",
                                    "percent", 80))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"bump priya to 80%","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.type").value("EDIT_ALLOCATION"))
            .andExpect(jsonPath("$.proposedAction.percent").value(80))
            .andExpect(jsonPath("$.proposedAction.billable").value(true)) // kept from current
            // raising 50 -> 80 must NOT warn against her own 50
            .andExpect(jsonPath("$.proposedAction.warnings").isEmpty());
}

@Test
void chat_editAllocation_capacityWarningCountsOnlyOtherAllocations() throws Exception {
    var acme = client("Acme Corp");
    var p1 = project("ACM-100", "Storefront Revamp", acme);
    var p2 = project("ACM-200", "Data Platform", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    var a1 = allocation(priya, p1, true);
    a1.setAllocationPercent(50);
    allocationRepository.save(a1);
    var a2 = allocation(priya, p2, true);
    a2.setAllocationPercent(40);
    allocationRepository.save(a2);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_edit_allocation",
                            Map.of("associateName", "Priya Sharma",
                                    "projectName", "Storefront Revamp",
                                    "percent", 70))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"bump priya to 70% on storefront","history":[]}"""))
            .andExpect(status().isOk())
            // 70 + the OTHER 40 = 110 -> warn
            .andExpect(jsonPath("$.proposedAction.warnings[0]", containsString("over 100%")));
}
```

- [ ] **Step 2: Run** — FAIL with the unknown-action fallback. Right failure.

- [ ] **Step 3: Implement.** In `AssistantService`:

(a) Generalize the capacity helper (one implementation; the old signature delegates):

```java
private List<String> capacityWarnings(Associate associate, int percent) {
    return capacityWarnings(associate, percent, null);
}

/** {@code excludeAllocationId}: when editing, that allocation's current percent must not count against itself. */
private List<String> capacityWarnings(Associate associate, int percent, Long excludeAllocationId) {
    int current = allocationRepository.findByAssociateId(associate.getId()).stream()
            .filter(Allocation::isCurrent)
            .filter(a -> !a.getId().equals(excludeAllocationId))
            .mapToInt(Allocation::getAllocationPercent)
            .sum();
    List<String> warnings = new ArrayList<>();
    if (current + percent > 100) {
        warnings.add("This would take %s over 100%% — current allocations already total %d%%."
                .formatted(associate.getName(), current));
    }
    return warnings;
}
```

(b) `draft(...)` gains `case "propose_edit_allocation" -> draftEditAllocation(reply.text(), args);` and:

```java
private AssistantChatResponse draftEditAllocation(String text, Map<String, Object> args) {
    ResolvedAllocation resolved = resolveCurrentAllocation(
            str(args, "associateName"), str(args, "projectName"));
    if (resolved.reply() != null) {
        return new AssistantChatResponse(resolved.reply(), null);
    }
    Allocation al = resolved.value();
    int percent = intOrDefault(args.get("percent"), al.getAllocationPercent());
    if (percent < 1 || percent > 100) {
        return new AssistantChatResponse(
                "An allocation must be between 1%% and 100%% — %d%% isn't valid.".formatted(percent), null);
    }
    boolean billable = boolOrDefault(args.get("billable"), al.isBillable());
    LocalDate end = dateOrDefault(args.get("endDate"), al.getEndDate());
    if (end != null && end.isBefore(al.getStartDate())) {
        return new AssistantChatResponse(
                "That allocation started on %s — the end date can't be before that."
                        .formatted(al.getStartDate()), null);
    }
    List<String> warnings = capacityWarnings(al.getAssociate(), percent, al.getId());
    String summary = "Change %s's allocation on %s to %d%% (%s)%s".formatted(
            al.getAssociate().getName(), al.getProject().getName(), percent,
            billable ? "billable" : "non-billable",
            end == null ? "" : ", ending " + end);
    ProposedAction proposed = new ProposedAction(ActionType.EDIT_ALLOCATION,
            al.getAssociate().getId(), al.getAssociate().getName(),
            al.getProject().getId(), al.getProject().getName(),
            null, null, percent, billable,
            al.getStartDate(), end, al.getId(), null, null, null, summary, warnings);
    return new AssistantChatResponse(nonBlank(text,
            "Here's the draft — review and confirm below."), proposed);
}
```

(c) Declaration in `GeminiHttpClient`:

```java
Map.of("name", "propose_edit_allocation",
        "description", "Draft changing an associate's CURRENT allocation on a project"
                + " (percent, billable flag, or end date), for the user to confirm."
                + " Anything not mentioned keeps its current value.",
        "parameters", Map.of("type", "object",
                "properties", Map.of(
                        "associateName", Map.of("type", "string"),
                        "projectName", Map.of("type", "string"),
                        "percent", Map.of("type", "integer",
                                "description", "new allocation percent 1-100"),
                        "billable", Map.of("type", "boolean"),
                        "endDate", Map.of("type", "string", "description", "ISO date")),
                "required", List.of("associateName", "projectName"))),
```

- [ ] **Step 4: Run the class** → PASS. **Step 5: Full suite + commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a propose_edit_allocation draft with a self-excluding capacity warning

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 6: `propose_position` (one required skill, resolved against the taxonomy)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (+ `SkillRepository` dep)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Failing tests:**

```java
@Test
void chat_draftsPosition_withResolvedSkillAndDefaults() throws Exception {
    var acme = client("Acme Corp");
    project("ACM-100", "Storefront Revamp", acme);
    var react = skill("Frontend", "React");
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_position",
                            Map.of("title", "Senior React Developer",
                                    "projectName", "Storefront Revamp",
                                    "skillName", "react",
                                    "minProficiency", "ADVANCE"))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"open a senior react position on storefront","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.type").value("CREATE_POSITION"))
            .andExpect(jsonPath("$.proposedAction.positionTitle").value("Senior React Developer"))
            .andExpect(jsonPath("$.proposedAction.skillId").value(react.getId().intValue()))
            .andExpect(jsonPath("$.proposedAction.skillName").value("React"))
            .andExpect(jsonPath("$.proposedAction.minProficiency").value("ADVANCE"))
            .andExpect(jsonPath("$.proposedAction.percent").value(100))
            .andExpect(jsonPath("$.proposedAction.billable").value(true));
}

@Test
void chat_position_unknownSkill_asksBack() throws Exception {
    var acme = client("Acme Corp");
    project("ACM-100", "Storefront Revamp", acme);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_position",
                            Map.of("title", "Sorcerer", "projectName", "Storefront Revamp",
                                    "skillName", "Dark Arts"))));

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"we need a sorcerer","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction").doesNotExist())
            .andExpect(jsonPath("$.reply", containsString("couldn't find a skill matching \"Dark Arts\"")));
}
```

- [ ] **Step 2: Run** — FAIL with the unknown-action fallback. Right failure.

- [ ] **Step 3: Implement.** In `AssistantService`:

(a) Add `SkillRepository skillRepository` as a constructor dependency (field + param + assignment; import `com.softility.omivertex.repository.SkillRepository` and `com.softility.omivertex.domain.Skill`).

(b) Resolver + draft; `draft(...)` gains `case "propose_position" -> draftPosition(reply.text(), args);`:

```java
private Resolved<Skill> resolveSkill(String name) {
    if (name == null || name.isBlank()) {
        return new Resolved<>(null, null); // no skill requested — a skill-less seat is fine
    }
    List<Skill> matches = matchByName(skillRepository.findAll(), Skill::getName, name);
    if (matches.size() == 1) {
        return new Resolved<>(matches.get(0), null);
    }
    if (matches.isEmpty()) {
        return new Resolved<>(null, "I couldn't find a skill matching \"%s\" in the taxonomy."
                .formatted(name));
    }
    return new Resolved<>(null, "I found more than one skill for \"%s\": %s — which one did you mean?"
            .formatted(name, matches.stream().map(Skill::getName).collect(Collectors.joining(", "))));
}

private AssistantChatResponse draftPosition(String text, Map<String, Object> args) {
    if (str(args, "title") == null || str(args, "title").isBlank()) {
        return new AssistantChatResponse("What should the position be called? Give me a title.", null);
    }
    Resolved<Project> project = resolveProject(str(args, "projectName"));
    if (project.reply() != null) {
        return new AssistantChatResponse(project.reply(), null);
    }
    Resolved<Skill> skill = resolveSkill(str(args, "skillName"));
    if (skill.reply() != null) {
        return new AssistantChatResponse(skill.reply(), null);
    }
    int percent = intOrDefault(args.get("percent"), DEFAULT_PERCENT);
    if (percent < 1 || percent > 100) {
        return new AssistantChatResponse(
                "An allocation must be between 1%% and 100%% — %d%% isn't valid.".formatted(percent), null);
    }
    boolean billable = boolOrDefault(args.get("billable"), true);
    LocalDate start = dateOrDefault(args.get("startDate"), null);
    LocalDate end = dateOrDefault(args.get("endDate"), null);
    // spec: an unparseable minProficiency degrades to INTERMEDIATE (only relevant with a skill)
    com.softility.omivertex.domain.Proficiency minProf = skill.value() == null ? null
            : java.util.Objects.requireNonNullElse(
                    proficiencyOrNull(str(args, "minProficiency")),
                    com.softility.omivertex.domain.Proficiency.INTERMEDIATE);
    String title = str(args, "title").trim();
    String summary = "Open '%s' on %s (%d%%, %s%s)".formatted(
            title, project.value().getName(), percent,
            billable ? "billable" : "non-billable",
            skill.value() == null ? "" : ", requires " + skill.value().getName() + " min " + minProf);
    ProposedAction proposed = new ProposedAction(ActionType.CREATE_POSITION,
            null, null, project.value().getId(), project.value().getName(),
            null, title, percent, billable, start, end, null,
            skill.value() == null ? null : skill.value().getId(),
            skill.value() == null ? null : skill.value().getName(),
            minProf, summary, List.of());
    return new AssistantChatResponse(nonBlank(text,
            "Here's the draft — review and confirm below."), proposed);
}
```

(c) Declaration in `GeminiHttpClient`:

```java
Map.of("name", "propose_position",
        "description", "Draft opening a NEW position on a project, for the user to"
                + " confirm — optionally with one required skill from the taxonomy."
                + " Use when asked to open a seat, create a position, or hire for a role.",
        "parameters", Map.of("type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "projectName", Map.of("type", "string"),
                        "skillName", Map.of("type", "string",
                                "description", "one required skill; optional"),
                        "minProficiency", Map.of("type", "string",
                                "description", "NOVICE, FOUNDATIONAL, INTERMEDIATE,"
                                        + " FUNCTIONAL_USER, ADVANCE or MASTERY"),
                        "percent", Map.of("type", "integer", "description", "default 100"),
                        "billable", Map.of("type", "boolean", "description", "default true"),
                        "startDate", Map.of("type", "string", "description", "ISO date; optional"),
                        "endDate", Map.of("type", "string", "description", "ISO date; optional")),
                "required", List.of("title", "projectName"))),
```

(Note: `Map.of` supports at most 10 pairs — the `properties` map above has 8, fine.)

- [ ] **Step 4: Run the class** → PASS. **Step 5: Full suite + commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a propose_position draft with one taxonomy-resolved required skill

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 7: Frontend — confirm the three new draft types

**Files:**
- Modify: `frontend/src/components/AssistantChat.jsx` (the `confirmAction` function)

- [ ] **Step 1: Extend `confirmAction`.** Replace its `if/else` action branch with:

```js
if (action.type === 'CREATE_ALLOCATION') {
  await api.create('allocations', {
    associateId: action.associateId,
    projectId: action.projectId,
    billable: action.billable,
    allocationPercent: action.percent,
    startDate: action.startDate,
    endDate: action.endDate,
  });
} else if (action.type === 'END_ALLOCATION' || action.type === 'EDIT_ALLOCATION') {
  await api.update('allocations', action.allocationId, {
    billable: action.billable,
    allocationPercent: action.percent,
    startDate: action.startDate,
    endDate: action.endDate,
  });
} else if (action.type === 'CREATE_POSITION') {
  await api.create('positions', {
    title: action.positionTitle,
    projectId: action.projectId,
    billable: action.billable,
    allocationPercent: action.percent,
    startDate: action.startDate,
    endDate: action.endDate,
    headcount: 1,
    skills: action.skillId
      ? [{ skillId: action.skillId, minProficiency: action.minProficiency, required: true }]
      : [],
  });
} else {
  await api.create(`positions/${action.positionId}/fill`, {
    associateId: action.associateId,
  });
}
```

(`api.update('allocations', id, …)` is the same call the Profile page's End-allocation modal makes — role checks, capacity guard, and audit all run server-side as today.)

- [ ] **Step 2: Verify** — `cd frontend && npm run format && npm run build` → clean.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/AssistantChat.jsx
git commit -m "Confirm Mirai's end/edit-allocation and create-position drafts from the chat card

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 8: Docs, graph, live verification, plan deletion

**Files:**
- Modify: `docs/TECHNICAL.md`, `docs/TODO.md`

- [ ] **Step 1: `docs/TECHNICAL.md`.** In the `/assistant/chat` row: extend the read-tool list with `get_position_match_summary` already there; append `; admins additionally get `list_pending_approvals` + `get_audit_history` (declared and executable only for ADMIN)`, and change the proposedAction type list to `{type: CREATE_ALLOCATION|FILL_POSITION|END_ALLOCATION|EDIT_ALLOCATION|CREATE_POSITION, …, allocationId?, skillId?, skillName?, minProficiency?}` with a note that END/EDIT confirm via `PUT /allocations/{id}` and CREATE_POSITION via `POST /positions`. In the numbered "AI assistant" item, append one sentence:

```
Write drafts now cover end/edit allocation and opening a position (one
taxonomy-resolved required skill); tools are role-registered via
`AssistantService.Caller` — a viewer's model never sees admin tools, and
dispatch refuses them anyway.
```

- [ ] **Step 2: `docs/TODO.md`** — add at the top of `## Resolved decisions`:

```
- **Assistant tools are role-registered** (2026-07-17): `GeminiClient.replyWithTools`
  takes the caller's admin flag; admin-only tools (`list_pending_approvals`,
  `get_audit_history`) are absent from a viewer's declarations AND refused at
  dispatch (defense in depth). The write-draft contract is unchanged — Mirai
  still never mutates; new END/EDIT/CREATE_POSITION drafts confirm through the
  existing endpoints under the user's own session.
```

- [ ] **Step 3: Graph refresh** — `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

- [ ] **Step 4: Full verification** — `./mvnw test` green; `cd frontend && npm run format:check && npm run build` clean.

- [ ] **Step 5: Live check** — restart the app on the new build (kill the process on :8080, `./mvnw spring-boot:run`, wait for readiness; sync `frontend/dist` into `target/classes/static` first). Login as admin via curl, ask "what needs my attention?" through `/api/v1/assistant/chat` and confirm a pending-approvals answer; ask "roll Priya Sharma off <a seeded project>" and confirm the reply carries `proposedAction.type == "END_ALLOCATION"` (do NOT confirm the draft — nothing must mutate).

- [ ] **Step 6: Commit docs, then delete this plan**

```bash
git add docs/TECHNICAL.md docs/TODO.md
git commit -m "Document Mirai's write expansion and role-registered tools

Co-Authored-By: <your model> <noreply@anthropic.com>"
git rm docs/superpowers/plans/2026-07-17-mirai-write-expansion.md
git commit -m "Remove the merged Mirai write-expansion plan

Co-Authored-By: <your model> <noreply@anthropic.com>"
```
