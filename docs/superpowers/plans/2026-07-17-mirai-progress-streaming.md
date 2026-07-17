# Mirai Progress Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stream live tool-progress events over SSE while Mirai works, then the full reply — shipping today for the demo.

**Architecture:** Additive `POST /api/v1/assistant/chat/stream` endpoint returning Spring MVC `SseEmitter`; `AssistantService.chat` gains a `ToolProgressListener` seam (no-op on the existing endpoint, so it stays byte-identical). Frontend gets a `fetch`-streaming SSE client that falls back to the plain endpoint on any transport failure, and the typing dots gain a live activity label.

**Tech Stack:** Spring MVC SseEmitter (no WebFlux), JUnit 5 + MockMvc async, fetch ReadableStream SSE parsing, existing CSS tokens.

**Spec:** `docs/superpowers/specs/2026-07-17-mirai-progress-streaming-design.md`

**Rules** (AGENTS.md): TDD — failing test first, right reason. Full `./mvnw test` green before every commit; `npm run format && npm run build` for frontend changes. Commit messages end with `Co-Authored-By: <your model> <noreply@anthropic.com>`.

---

### Task 1: Listener seam + stream endpoint (backend)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java`
- Modify: `src/main/java/com/softility/omivertex/web/AssistantController.java`
- Create: `src/test/java/com/softility/omivertex/api/AssistantStreamApiTest.java`

- [ ] **Step 1: Write the failing tests** — create `AssistantStreamApiTest.java`:

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The SSE variant of /assistant/chat: tool progress events, then the reply. */
class AssistantStreamApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    /** The emitter completes on the ai-* thread — poll the mock response until it has the terminal event. */
    private String awaitSse(MvcResult result) throws Exception {
        for (int i = 0; i < 200; i++) {
            String body = result.getResponse().getContentAsString();
            if (body.contains("event:reply") || body.contains("event:error")) {
                return body;
            }
            Thread.sleep(25);
        }
        return result.getResponse().getContentAsString();
    }

    private MvcResult startStream(String message) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\",\"history\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    void stream_sendsToolEventsInOrder_thenTheReply() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    ex.execute("list_open_positions", Map.of());
                    ex.execute("get_position_match_summary", Map.of());
                    return new GeminiClient.AssistantReply("Two positions lack matches.", null);
                });

        String body = awaitSse(startStream("which open positions have no bench match?"));

        assertThat(body).contains("event:tool");
        assertThat(body.indexOf("data:list_open_positions"))
                .isLessThan(body.indexOf("data:get_position_match_summary"));
        assertThat(body).contains("event:reply");
        assertThat(body).contains("Two positions lack matches.");
    }

    @Test
    void stream_replyEventCarriesProposedActionJson() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenReturn(new GeminiClient.AssistantReply("Here's the draft.",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp"))));

        String body = awaitSse(startStream("allocate priya to storefront"));

        assertThat(body).contains("event:reply");
        assertThat(body).contains("CREATE_ALLOCATION"); // same JSON shape as the plain endpoint
    }

    @Test
    void stream_failureBecomesAnErrorEvent() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenThrow(new com.softility.omivertex.web.error.BadRequestException(
                        "The AI assistant is unavailable right now — try again shortly"));

        String body = awaitSse(startStream("hello"));

        assertThat(body).contains("event:error");
        assertThat(body).contains("unavailable right now");
    }

    @Test
    void stream_associateRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat/stream")
                        .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"history\":[]}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./mvnw test -Dtest=AssistantStreamApiTest`
Expected: FAIL — 404/405 on `/chat/stream` (endpoint missing); the role test may already pass (the `/**` matcher covers it). That is the right failure.

- [ ] **Step 3: Add the listener seam to `AssistantService`** — inside the class add:

```java
/** Notified as each read tool runs during a turn — lets transports show live progress. */
public interface ToolProgressListener {
    void toolCalled(String name);

    ToolProgressListener NONE = name -> {};
}
```

Change the existing 2-arg `chat` to delegate, and thread the listener through the decorator:

```java
public AssistantChatResponse chat(AssistantChatRequest request, String username) {
    return chat(request, username, ToolProgressListener.NONE);
}

public AssistantChatResponse chat(AssistantChatRequest request, String username,
                                  ToolProgressListener progress) {
```

and in the executor lambda, after `toolsCalled.add(name);` add `progress.toolCalled(name);`. Nothing else in the method changes.

- [ ] **Step 4: Add the stream endpoint to `AssistantController`:**

Add imports: `org.springframework.web.servlet.mvc.method.annotation.SseEmitter`, `java.io.IOException`, `com.softility.omivertex.service.AssistantService.ToolProgressListener` (or qualify inline).

```java
/** SSE stream: emitter completes when the reply (or error) event has been sent. */
static final long STREAM_TIMEOUT_MS = 60_000;

/**
 * Streaming variant of {@link #chat}: {@code tool} events as lookups run, then one
 * {@code reply} event with the exact same JSON the plain endpoint returns. Errors
 * surface as an {@code error} event carrying the user-facing message.
 */
@PostMapping("/chat/stream")
public SseEmitter chatStream(@Valid @RequestBody AssistantChatRequest request) {
    // Resolved here, on the servlet thread: the ai-* pool never sees the SecurityContext.
    String username = AuditService.currentUsername();
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    emitter.onTimeout(emitter::complete);
    aiExecutor.submit(() -> {
        try {
            AssistantChatResponse response = assistantService.chat(request, username,
                    name -> send(emitter, "tool", name));
            send(emitter, "reply", response);
        } catch (RuntimeException e) {
            trySend(emitter, "error", e.getMessage() == null
                    ? "The AI assistant hit an error — try again." : e.getMessage());
        }
        emitter.complete();
        return null;
    });
    return emitter;
}

/** A failed send means the client went away — abandon the turn quietly. */
private void send(SseEmitter emitter, String event, Object data) {
    try {
        emitter.send(SseEmitter.event().name(event).data(data));
    } catch (IOException | IllegalStateException e) {
        throw new IllegalStateException("SSE client disconnected", e);
    }
}

/** Best-effort send for the error path — the client may already be gone. */
private void trySend(SseEmitter emitter, String event, Object data) {
    try {
        send(emitter, event, data);
    } catch (IllegalStateException ignored) {
        // nothing left to tell a departed client
    }
}
```

(A saturated bulkhead throws from `aiExecutor.submit` before the emitter is returned → the normal 503 JSON. A mid-turn disconnect throws from the listener, aborts the turn, and the interaction log records the turn as ERROR — acceptable per spec.)

- [ ] **Step 5: Run to verify green** — `./mvnw test -Dtest=AssistantStreamApiTest` → PASS (4 tests).

- [ ] **Step 6: Full suite + commit**

`./mvnw test` → PASS (the 2-arg `chat` delegation keeps every existing test green).

```bash
git add src/main/java/com/softility/omivertex src/test/java/com/softility/omivertex
git commit -m "Stream Mirai turns over SSE: tool progress events, then the reply

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 2: Frontend — SSE client with plain-endpoint fallback

**Files:**
- Modify: `frontend/src/api.js` (next to `askAssistant`, line ~161)

- [ ] **Step 1: Add the stream client** below `askAssistant`:

```js
// Streams a chat turn: onTool(name) fires as each lookup runs; resolves with the
// same payload askAssistant returns. Any TRANSPORT failure falls back to the
// plain endpoint so a chat always gets an answer; a server-sent error event is
// a real business error and is rethrown, not retried.
askAssistantStream: async (message, history, onTool) => {
  try {
    const res = await fetch(`${BASE}/assistant/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, history }),
    });
    if (!res.ok || !res.body) throw new Error('stream unavailable');
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let event = 'message';
    let reply = null;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();
      for (const line of lines) {
        if (line.startsWith('event:')) {
          event = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (event === 'tool' && onTool) onTool(data);
          else if (event === 'reply') reply = JSON.parse(data);
          else if (event === 'error') {
            const err = new Error(data);
            err.fromStream = true; // business error — do NOT retry on the plain endpoint
            throw err;
          }
        }
      }
    }
    if (reply) return reply;
    throw new Error('stream ended without a reply');
  } catch (err) {
    if (err.fromStream) throw err;
    return api.askAssistant(message, history); // transport fallback (also restores 401 handling)
  }
},
```

- [ ] **Step 2: Verify** — `cd frontend && npm run format && npm run build` → clean. (No JS test infra; Task 4 rehearses live.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api.js
git commit -m "Add the streaming assistant client with plain-endpoint fallback

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 3: Frontend — live activity line in the chat card

**Files:**
- Modify: `frontend/src/components/AssistantChat.jsx`
- Modify: `frontend/src/styles.css` (two small rules near the `.mirai-typing` block, ~line 1162)

- [ ] **Step 1: Tool labels + state.** In `AssistantChat.jsx`, below the `SUGGESTIONS` array add:

```js
/** Friendly progress labels per read tool; anything unknown gets the generic line. */
const TOOL_LABELS = {
  search_associates: 'Searching associates…',
  get_associate_detail: 'Reading a profile…',
  get_project_detail: 'Reading a project…',
  list_rolloffs: 'Checking upcoming roll-offs…',
  list_open_positions: 'Looking up open positions…',
  get_position_matches: 'Ranking bench matches…',
  get_position_match_summary: 'Checking bench matches for every open position…',
  list_clients: 'Listing clients…',
  list_projects: 'Listing projects…',
  get_skill_gaps: 'Analyzing skill gaps…',
  list_expiring_certifications: 'Checking certifications…',
  get_workforce_summary: 'Compiling the workforce summary…',
  list_bench_aging: 'Reviewing the bench…',
};
```

In the component add `const [activity, setActivity] = useState('');`.

- [ ] **Step 2: Stream in `ask()`.** Replace the `api.askAssistant` call:

```js
setActivity('Thinking…');
try {
  const { reply, proposedAction } = await api.askAssistantStream(question, history, (tool) =>
    setActivity(TOOL_LABELS[tool] || 'Looking things up…')
  );
  setMessages((m) => [...m, { role: 'model', content: reply, action: proposedAction }]);
} catch (err) {
  setMessages((m) => m.slice(0, -1));
  showToast(err.message, true);
} finally {
  setBusy(false);
  setActivity('');
  setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50);
}
```

- [ ] **Step 3: Show the activity beside the dots.** `.mirai-typing span` styles every child as a dot, so the label needs its own element. Replace the busy block:

```jsx
{busy && (
  <div className="mirai-typing-row">
    <div className="mirai-typing" aria-hidden="true">
      <span />
      <span />
      <span />
    </div>
    <span className="mirai-activity" aria-live="polite">
      {activity}
    </span>
  </div>
)}
```

- [ ] **Step 4: CSS.** In `styles.css`, immediately after the `.mirai-typing span:nth-child(3)` rule add (tokens only, no raw hex):

```css
.mirai-typing-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mirai-activity {
  font-size: 12.5px;
  font-style: italic;
  color: var(--color-muted-fg);
}
```

- [ ] **Step 5: Verify + commit** — `cd frontend && npm run format && npm run build` → clean.

```bash
git add frontend/src/components/AssistantChat.jsx frontend/src/styles.css
git commit -m "Show Mirai's live tool activity while it works, via the SSE stream

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 4: Docs, graph, demo rehearsal

- [ ] **Step 1: `docs/TECHNICAL.md`** — in the endpoint table below the `/assistant/chat` row add:

```
| `/assistant/chat/stream` | POST (SSE variant of `/assistant/chat`: `tool` events as lookups run, then one `reply` event with the same JSON; failures send an `error` event; 60s emitter timeout; same roles) | — |
```

and in the "AI execution model" section note: "The streaming endpoint shares the same bulkhead, service body, and interaction log — only the transport differs; the UI falls back to the plain endpoint on transport failure."

- [ ] **Step 2: Graph refresh** — `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

- [ ] **Step 3: Full verification** — `./mvnw test` green; `cd frontend && npm run format:check && npm run build` clean.

- [ ] **Step 4: Commit docs**

```bash
git add docs/TECHNICAL.md
git commit -m "Document the assistant SSE stream endpoint

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

- [ ] **Step 5: Demo rehearsal (live).** Identify how the running app was started (`ps` on the pid listening on 8080), restart it on the new build, then: login via `/api/v1/auth/login` (curl, cookie jar), `curl -N` the stream endpoint with the bench-match chip question and confirm `event:tool` lines appear before `event:reply`; finally confirm in the browser that the chip question shows live activity lines. Leave the app running for the demo.

- [ ] **Step 6: Delete this plan** (after the demo work is confirmed)

```bash
git rm docs/superpowers/plans/2026-07-17-mirai-progress-streaming.md
git commit -m "Remove the merged Mirai progress-streaming plan

Co-Authored-By: <your model> <noreply@anthropic.com>"
```
