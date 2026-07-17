# Mirai — live progress streaming (SSE)

**Date:** 2026-07-17 · **Status:** Approved

## What & why

Mirai's replies arrive as one block after the whole tool loop finishes —
users stare at typing dots for 3–10 seconds. This ships **stage streaming**
for today's demo: the moment a question is sent, the card shows what Mirai
is doing ("Looking up open positions…", "Ranking bench matches…"), one line
per tool call, then the full reply renders at once.

Chosen over true token streaming deliberately (user decision, demo-day
risk): no change to the Gemini HTTP client, additive endpoint, the existing
chat endpoint stays untouched as a fallback. The SSE transport built here is
reused when token streaming lands later — nothing is throwaway.

## Backend

**New endpoint** `POST /api/v1/assistant/chat/stream` on `AssistantController`,
returning Spring MVC `SseEmitter` (timeout 60s; no WebFlux). The existing
`/api/v1/assistant/**` security rule already covers it (ADMIN + VIEWER).

**Event protocol** (in order):

1. `event: tool`, `data: <toolName>` — one per read-tool execution, sent as it happens.
2. `event: reply`, `data: <AssistantChatResponse JSON>` — the same shape the
   plain endpoint returns, so proposed-action draft cards work unchanged.
3. Emitter completes. On failure: `event: error`, `data: <message>` (the same
   user-facing message the JSON endpoint's exception mapping produces), then complete.

**Service seam:** `AssistantService` gains

```java
/** Notified as each read tool runs during a turn — lets transports show live progress. */
public interface ToolProgressListener { void toolCalled(String name); }
```

and a 3-arg `chat(request, username, listener)`; the existing tool-collecting
decorator additionally invokes the listener. The 2-arg `chat` delegates with a
no-op listener — the plain endpoint's behavior is byte-identical. Interaction
logging is unchanged (same body, one MIRAI line per turn on both endpoints).

**Threading:** the controller resolves the username on the servlet thread (as
today), creates the emitter, and submits the turn to the `AiExecutor`
bulkhead. `SseEmitter.send` is called from the `ai-*` thread (supported).
A send failure (client disconnected) aborts the turn quietly. A saturated
bulkhead rejects synchronously before the stream opens → the normal 503 JSON.

## Frontend

- `api.js`: `askAssistantStream(message, history, onTool)` — `fetch` +
  `ReadableStream` SSE parsing (EventSource cannot POST). Resolves with the
  reply object; calls `onTool(name)` per tool event; rejects on `error`
  events/HTTP failures. **On transport failure it falls back to the existing
  `askAssistant`** so the demo cannot be taken down by a proxy or regression.
- `AssistantChat.jsx`: while busy, the typing indicator shows a live activity
  line driven by tool events — friendly labels per tool name (e.g.
  `get_position_match_summary` → "Checking bench matches for every open
  position…"), generic "Looking things up…" for unknown names, and
  "Thinking…" before the first event. Reply rendering, draft cards, error
  toasts, reduced-motion, and theme tokens all unchanged.

## Error handling

- Service exceptions → `event: error` with the exception message, emitter
  completed; the UI toasts it and removes the pending user bubble (existing
  behavior).
- Client disconnect mid-turn → send throws, turn abandoned, no retry; the
  interaction log still records the turn's outcome.
- Emitter timeout (60s) → `error` event via the emitter's timeout callback.

## Testing & verification

TDD; every new behavior starts from a failing test.

- **`AssistantApiTest` (or a sibling class):** with the mocked `GeminiClient`
  executing tools — stream response has `Content-Type: text/event-stream`;
  body contains the `tool` events in execution order, then one `reply` event
  whose JSON matches the plain endpoint's response shape (including a
  `proposedAction` case); a throwing turn yields an `error` event with the
  message; ASSOCIATE role gets 403; the plain `/chat` endpoint's tests stay
  untouched and green.
- **Frontend:** `npm run format && npm run build` (no JS test infra).
- **Demo rehearsal:** restart the app, ask the bench-match chip question via
  the UI, confirm live progress lines then the reply; confirm a draft
  (propose_allocation) still renders its confirm card over the stream.
- Full `./mvnw test` green; `docs/TECHNICAL.md` updated (stream endpoint row +
  event protocol); graph refreshed.

## Out of scope

True token streaming (follow-up in this phase, reusing this transport), the
write-tool expansion and role-aware tools (separate spec, right after the
demo), history persistence, feedback buttons.
