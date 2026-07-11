# AI Bulkhead + Timeouts — Design

**Date:** 2026-07-11
**Status:** Approved by user
**Problem:** All Gemini calls (assistant chat, resume extraction) run on servlet
request threads through a `RestClient` with **no timeouts** — a slow or hung
upstream call blocks a Tomcat thread indefinitely, and enough concurrent AI
requests could starve every other endpoint.

## What & why

1. **HTTP timeouts** — `GeminiHttpClient` builds its `RestClient` with a
   request factory configured from two new `Duration` properties:

   ```properties
   omivertex.assistant.gemini.connect-timeout=${OMIVERTEX_ASSISTANT_GEMINI_CONNECT_TIMEOUT:5s}
   omivertex.assistant.gemini.read-timeout=${OMIVERTEX_ASSISTANT_GEMINI_READ_TIMEOUT:30s}
   ```

   A timeout surfaces through the existing catch blocks as the current
   "unavailable right now" 400 — no new error paths in the client.

2. **Bulkhead executor** — new `AiExecutor` component wrapping a dedicated
   `ThreadPoolTaskExecutor`: 4 core/max threads, queue capacity 8, thread names
   `ai-*`, abort on saturation. It exposes
   `<T> CompletableFuture<T> submit(Supplier<T>)`; a
   `RejectedExecutionException` becomes a new typed
   `ServiceUnavailableException` ("The AI assistant is busy right now — try
   again shortly") mapped to **503** in `GlobalExceptionHandler`, matching the
   existing typed-exception error contract. Constants are named and commented
   (no magic numbers).

3. **Async endpoints** — the three AI endpoints return
   `CompletableFuture<...>` supplied via `AiExecutor`:
   - `POST /api/v1/assistant/chat` (`AssistantController`)
   - `POST /api/v1/resumes/parse` (`ResumeController`)
   - `POST /api/v1/me/resumes/parse` (`MeController`)

   Servlet threads are released while Gemini responds. Bean-validation
   failures (blank/oversized message) still reject synchronously before any
   async work. Multipart resources remain valid for the async duration
   (request completes only after dispatch). The HTTP contract (status codes,
   bodies) is unchanged — **the frontend needs no changes.**

4. **Plans convention** — `AGENTS.md` documents: plan docs
   (`docs/superpowers/plans/`) are **write-once scaffolding** for a single
   implementation run; never update an old plan. Specs and `docs/TECHNICAL.md`
   are the living documentation.

## Testing

- Existing tests for the three endpoints move to MockMvc's two-step async
  pattern (`request().asyncStarted()` → `asyncDispatch(...)`); sync
  validation-error tests stay as-is.
- New unit test: saturated `AiExecutor` → `ServiceUnavailableException`.
- New handler mapping test: `ServiceUnavailableException` → 503 with the
  standard error body.
- Full suite green before every commit (AGENTS.md).

## Out of scope

- Background job queue / submit-then-poll (rejected as overkill for
  interactive endpoints).
- Retries, circuit breakers, streaming responses.
- Changing assistant/resume feature behavior in any way.
