# Assistant Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the dashboard assistant *draft* two write actions — allocate an associate to a project, and fill an open position — plus execute one read tool (position matches), with every write confirmed by the human in the browser through the existing REST endpoints. The assistant endpoint itself never mutates data.

**Architecture:** `GeminiClient.reply(...)` becomes `replyWithTools(context, history, message, toolExecutor)` returning `AssistantReply(text, actionCall)`. `GeminiHttpClient` declares three Gemini function tools (`propose_allocation`, `propose_position_fill`, `get_position_matches`); the read tool loops server-side (max 2 rounds), write tools are returned as `ActionCall`s. `AssistantService` resolves names → ids (ambiguity = clarifying reply, no action), pre-validates, and returns `AssistantChatResponse.proposedAction`. `AssistantChat.jsx` renders an action card whose Confirm button calls `POST /api/v1/allocations` or `POST /api/v1/positions/{id}/fill` under the user's own session (role check, ≤100% capacity guard, audit all fire as normal).

**Tech Stack:** Gemini v1beta function calling (REST), Spring Boot, Mockito-stubbed API tests, React 18.

**Branch:** `feature/skill-gaps-and-ai`. Spec: `docs/superpowers/specs/2026-07-11-skill-gaps-resume-ai-assistant-actions-design.md`.

**Security property (spec):** prompt injection in workforce data can at worst draft a fully visible action card — execution only happens browser-side via existing authorized endpoints.

---

### Task 1: Failing API tests

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Switch existing stubs to the new contract.** In each of the 4 existing tests, replace

```java
when(geminiClient.reply(anyString(), anyList(), anyString())).thenReturn("...");
```

with

```java
when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
        .thenReturn(new GeminiClient.AssistantReply("...", null));
```

and the `verify(geminiClient).reply(context.capture(), anyList(), anyString())` with
`verify(geminiClient).replyWithTools(context.capture(), anyList(), anyString(), any())`.

- [ ] **Step 2: Add the new tests** (imports: `java.util.Map`, `com.softility.omivertex.domain.Proficiency` if needed, `static org.mockito.ArgumentMatchers.any`)

```java
@Test
void chat_draftsAllocation_fromToolCall() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_allocation",
                            Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp",
                                    "percent", 50, "billable", true))));

    mockMvc.perform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"allocate priya to storefront at 50%","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.type").value("CREATE_ALLOCATION"))
            .andExpect(jsonPath("$.proposedAction.associateId").value(priya.getId()))
            .andExpect(jsonPath("$.proposedAction.projectId").value(proj.getId()))
            .andExpect(jsonPath("$.proposedAction.percent").value(50))
            .andExpect(jsonPath("$.proposedAction.billable").value(true))
            .andExpect(jsonPath("$.proposedAction.warnings").isEmpty())
            .andExpect(jsonPath("$.reply").isNotEmpty());
}

@Test
void chat_ambiguousAssociate_asksInsteadOfDrafting() throws Exception {
    var acme = client("Acme Corp");
    project("ACM-100", "Storefront Revamp", acme);
    associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    associate("Priya Verma", "priya.v@softility.com", WorkMode.ONSHORE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_allocation",
                            Map.of("associateName", "Priya", "projectName", "Storefront Revamp"))));

    mockMvc.perform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"allocate priya to storefront","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction").doesNotExist())
            .andExpect(jsonPath("$.reply", containsString("Priya Sharma")))
            .andExpect(jsonPath("$.reply", containsString("Priya Verma")));
}

@Test
void chat_overCapacityDraft_carriesWarning() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var other = project("ACM-200", "Data Platform", acme);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    allocation(priya, other, true); // helper creates a current 100% allocation
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_allocation",
                            Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp",
                                    "percent", 50))));

    mockMvc.perform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"add priya to storefront at 50%","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.warnings[0]", containsString("100%")));
}

@Test
void chat_draftsPositionFill() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var pos = new com.softility.omivertex.domain.OpenPosition();
    pos.setTitle("Java Dev");
    pos.setProject(proj);
    openPositionRepository.save(pos);
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenReturn(new GeminiClient.AssistantReply("",
                    new GeminiClient.ActionCall("propose_position_fill",
                            Map.of("positionTitle", "Java Dev", "associateName", "Priya Sharma"))));

    mockMvc.perform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"fill the java dev seat with priya","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedAction.type").value("FILL_POSITION"))
            .andExpect(jsonPath("$.proposedAction.positionId").value(pos.getId()))
            .andExpect(jsonPath("$.proposedAction.associateId").value(priya.getId()));
}

@Test
void chat_readTool_executesPositionMatches() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var pos = new com.softility.omivertex.domain.OpenPosition();
    pos.setTitle("Java Dev");
    pos.setProject(proj);
    openPositionRepository.save(pos);
    associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE); // on bench
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                String result = ex.execute("get_position_matches", Map.of("positionTitle", "Java Dev"));
                return new GeminiClient.AssistantReply("Matches: " + result, null);
            });

    mockMvc.perform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"who matches the java dev seat?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Priya Sharma")));
}
```

Add `import static org.hamcrest.Matchers.containsString;` if missing.

- [ ] **Step 3: Verify red**

Run: `./mvnw test -Dtest=AssistantApiTest`
Expected: COMPILE FAILURE (`replyWithTools`, `AssistantReply`, `ActionCall`, `ToolExecutor` missing).

---

### Task 2: Contract + AssistantService (make it pass)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiClient.java` (replace `reply` with `replyWithTools` + new records)
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssistantChatResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (rename `reply` → private helper reachable from a temporary `replyWithTools` that ignores tools; full function-calling in Task 3)
- Modify: `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java` (`client.reply(...)` → `client.replyWithTools(..., null)`)

- [ ] **Step 1: `GeminiClient`** — replace the `reply` method with:

```java
/**
 * One assistant turn with tool support. Read tools run via {@code tools};
 * a write tool surfaces as {@link AssistantReply#action()} for the caller
 * to turn into a user-confirmable draft. Never mutates anything itself.
 */
AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                              String userMessage, ToolExecutor tools);

/** Executes a read-only tool server-side; returns a compact result for the model. */
interface ToolExecutor {
    String execute(String name, java.util.Map<String, Object> args);
}

/** A write tool the model wants to run — name + raw args, pending resolution. */
record ActionCall(String name, java.util.Map<String, Object> args) {}

/** Model output for one turn: prose text and/or a proposed write action. */
record AssistantReply(String text, ActionCall action) {}
```

- [ ] **Step 2: `AssistantChatResponse`**

```java
package com.softility.omivertex.web.dto;

import java.time.LocalDate;
import java.util.List;

public record AssistantChatResponse(String reply, ProposedAction proposedAction) {

    public enum ActionType { CREATE_ALLOCATION, FILL_POSITION }

    /** A draft the user must confirm in the UI; confirming calls the existing endpoints. */
    public record ProposedAction(ActionType type,
                                 Long associateId, String associateName,
                                 Long projectId, String projectName,
                                 Long positionId, String positionTitle,
                                 Integer percent, Boolean billable,
                                 LocalDate startDate, LocalDate endDate,
                                 String summary, List<String> warnings) {
    }
}
```

- [ ] **Step 3: `AssistantService`** — full replacement (deps: context builder, gemini, `AssociateRepository`, `ProjectRepository`, `OpenPositionRepository`, `AllocationRepository`, `PositionService`, `ObjectMapper`):

Resolution rules: match ACTIVE associates / projects / OPEN positions case-insensitively (exact name first, then contains); zero matches or >1 match → return a clarifying `reply` listing candidates, no action. Allocation defaults: percent 100, billable true, startDate today. Warnings: current allocation sum + percent > 100 → "This would take <name> over 100% ..."; fill target not OPEN → clarifying reply. Read tool `get_position_matches` resolves the position then serializes the top matches (name, fullMatch, missing) as JSON via ObjectMapper. Summary strings: `"Allocate <a> to <p> at <n>% (<billable|non-billable>) from <start>"` and `"Fill '<title>' with <a> — allocates <n>% <billable|non-billable> per the position's terms"`.

(The exact code is written at implementation time following these rules — every branch above has a test from Task 1.)

- [ ] **Step 4: `GeminiHttpClient` temporary bridge** — rename `public String reply(...)` to `private String generate(...)` and add:

```java
@Override
public AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                     String userMessage, ToolExecutor tools) {
    return new AssistantReply(generate(workforceContext, history, userMessage), null);
}
```

Update `GeminiHttpClientTest.withoutApiKey_failsClosedWithClearMessage` to call `client.replyWithTools("context", List.of(), "who is on the bench?", null)`.

- [ ] **Step 5: Verify green + full suite; commit**

Run: `./mvnw test -Dtest=AssistantApiTest` → PASS (9 tests). `./mvnw test` → green.

```bash
git add -A src/main src/test
git commit -m "feat: assistant drafts allocations and position fills as user-confirmable actions

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Real Gemini function calling

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`

- [ ] **Step 1: Implement.** Constants and declarations:

```java
/** Max read-tool round-trips per turn before we force a final answer. */
static final int MAX_TOOL_ROUNDS = 2;
static final String READ_TOOL_MATCHES = "get_position_matches";
```

`replyWithTools` builds `contents` exactly as `generate` does, adds a `tools` block with the three function declarations (`propose_allocation`: associateName*, projectName*, percent int, billable bool, startDate/endDate ISO strings; `propose_position_fill`: positionTitle*, associateName*; `get_position_matches`: positionTitle*), then loops: call the API; if the first candidate part is a `functionCall` — for `READ_TOOL_MATCHES` with `tools != null` and rounds left, execute, append the model's `functionCall` content and a `role:"user"` `functionResponse` part, continue; otherwise return `AssistantReply(textPartsOrEmpty, new ActionCall(name, args))`. A plain text answer returns `AssistantReply(text, null)`. Error handling mirrors the existing catch blocks (fail closed with `BadRequestException`).

- [ ] **Step 2: Full suite green (stubs in tests keep this offline); commit**

```bash
git add src/main/java/com/softility/omivertex/service/GeminiHttpClient.java
git commit -m "feat: Gemini function-calling — read-tool loop + write-tool drafts

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Frontend action card

**Files:**
- Modify: `frontend/src/components/AssistantChat.jsx`
- Modify: `frontend/src/pages/Dashboard.jsx` (pass `canEdit`)

- [ ] **Step 1:** Dashboard already receives `canEdit` from `App` (`<Page ... canEdit={canEdit} />`); accept it in `Dashboard`'s props if not already and render `<AssistantChat showToast={showToast} canEdit={canEdit} />`.

- [ ] **Step 2:** In `AssistantChat`: store `proposedAction` on the model message; render an action card under it — summary line, parameter rows, amber warning rows, and Confirm (primary, only when `canEdit`) / Dismiss buttons. Confirm executes:

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
} else {
  await api.create(`positions/${action.positionId}/fill`, { associateId: action.associateId });
}
```

then toasts success and marks the card done (buttons disappear, "✓ Done" note). Errors surface via `showToast(err.message, true)` and leave the card active. All colors via tokens.

- [ ] **Step 3:** `cd frontend && npm run format && npm run build` → green; commit

```bash
git add frontend/src/components/AssistantChat.jsx frontend/src/pages/Dashboard.jsx
git commit -m "feat: assistant action cards — one-click confirm through existing endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Docs, graph, wrap-up

- [ ] **Step 1:** `docs/TECHNICAL.md` — update the `/assistant/chat` row: response may carry `proposedAction {type: CREATE_ALLOCATION|FILL_POSITION, resolved ids/names, percent, billable, dates, summary, warnings[]}`; the endpoint never mutates; confirmation happens client-side via `POST /allocations` / `POST /positions/{id}/fill`.
`docs/TODO.md` "Resolved decisions": assistant executes nothing server-side by design (prompt-injection can only draft a visible card); name resolution requires a unique ACTIVE match or the assistant asks back; read tool capped at 2 rounds.

- [ ] **Step 2:** `./mvnw test` green → graph refresh → commit docs.
