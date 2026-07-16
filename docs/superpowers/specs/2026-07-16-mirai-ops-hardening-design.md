# Mirai — ops hardening (interaction logging, loop-cap test, golden-question eval)

**Date:** 2026-07-16 · **Status:** Approved

## What & why

Mirai answers and forgets: nothing records that the assistant was used, the
loop cap that bounds paid Gemini round-trips is untested, and prompt or tool
changes have no regression net beyond wiring tests. This phase adds the
observability seatbelts **before** the assistant gains more write verbs
(Phase 3 of the 2026-07-16 Mirai review). Three items:

1. **Interaction logging** — one log line per chat turn (log file only).
2. **Tool-loop cap test** — pin `MAX_TOOL_ROUNDS` with a real HTTP-level test.
3. **Golden-question eval suite** — an opt-in live-model exam Mirai must pass.

Explicitly descoped by the user: **no rate limiting** (revisit before opening
Mirai to more users), **no database storage** for interactions, and **reply
text is never persisted or logged** anywhere — the log records that a question
was answered, not what the answer said.

## 1. Interaction logging

**No DB.** A new `AssistantInteractionLog` component (`service` package) wraps
an SLF4J logger; lines flow through the existing Logback setup into
`logs/omivertex.log` and age out with normal rotation. One line per
`/assistant/chat` turn, always — success or failure:

```
MIRAI user=viewer@softility.com outcome=ANSWERED tools=[search_associates, get_associate_detail] latencyMs=2413 question="who is free for Acme?"
```

- **Fields:** `user` (authenticated principal), `outcome`
  (`ANSWERED` = prose reply · `DRAFTED` = reply carries a `ProposedAction` ·
  `ERROR` = the turn threw; logged, then rethrown unchanged), `tools`
  (read-tool names in call order, `[]` when none), `latencyMs` (wall time of
  the whole turn including Gemini), `question` (the raw message — already
  ≤2000 chars by request validation; double quotes escaped so the line stays
  parseable). The reply text is **never** included.
- **Threading:** `AssistantService.chat()` runs on the `ai-*` bulkhead pool
  where Spring's `SecurityContext` is absent. `AssistantController` therefore
  resolves the username on the servlet thread
  (`SecurityContextHolder`-based, same null-safe pattern as
  `AuditService` — falls back to `"system"`) and passes it into
  `chat(request, username)`. The public service signature changes; all
  callers are in this repo.
- **Tool capture:** `chat()` wraps its `this::executeReadTool` executor in a
  collecting decorator that appends each invoked tool name to a per-turn list,
  then delegates. No change to the executor contract.
- **Placement:** the log call sits in a `try/finally`-shaped flow inside
  `AssistantService.chat()` so an upstream Gemini failure still produces an
  `ERROR` line with the tools called so far.

## 2. Tool-loop cap test (+ one testability property)

`GeminiHttpClient.replyWithTools` already caps tool round-trips at
`MAX_TOOL_ROUNDS` (= 3): on the round after the cap, a read-tool call falls
through to the `ActionCall` return and `AssistantService` answers with its
polite fallback. Nothing pins this — a refactor that drops the guard would
only surface as a runaway API bill.

- **Testability change:** the hardcoded endpoint constant becomes a
  configurable base: new property `omivertex.assistant.gemini.base-url`,
  default `https://generativelanguage.googleapis.com` (the path suffix
  `/v1beta/models/%s:generateContent` stays in code). Default config is
  byte-identical in behavior; no other production change.
- **The test** (`GeminiToolLoopCapTest`, `api` test package like every other
  test): starts a JDK `com.sun.net.httpserver.HttpServer` on an ephemeral
  port whose handler **always** returns a valid Gemini JSON response
  requesting `search_associates`, counting requests. It constructs
  `GeminiHttpClient` directly (base-url = the stub server, dummy key) and
  calls `replyWithTools` with a counting no-op `ToolExecutor`. Assertions:
  the call **returns** (no infinite loop), the server received exactly
  `MAX_TOOL_ROUNDS + 1` requests (initial + 3 tool rounds), the executor ran
  exactly `MAX_TOOL_ROUNDS` times, and the reply surfaces the uncompleted
  call as an `ActionCall` (the documented fall-through). A guard timeout
  (JUnit `@Timeout`) keeps a regression from hanging the suite.

## 3. Golden-question eval suite

An opt-in exam against the **real** Gemini model — never part of a normal
build, so `./mvnw test` stays free and deterministic.

- **Gate:** `@EnabledIfEnvironmentVariable(named = "MIRAI_GOLDEN_EVAL",
  matches = "true")` on the test class; it also requires
  `OMIVERTEX_ASSISTANT_GEMINI_API_KEY`. Absent gate → skipped silently.
  Run: `MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest`.
- **Fixture:** the class seeds a small deterministic workforce into H2
  (reusing `ApiTestBase` helpers): a benched associate, an allocated
  associate at 100%, an open position with a required skill, a certification
  expiring in 30 days.
- **Golden questions** (each one test method, asserting on the reply and on
  which tools ran — the latter captured from the new interaction log via a
  Logback `ListAppender`, which makes the eval a consumer of item 1):
  1. "Who is on the bench right now?" → reply names the benched associate;
     a roster tool ran (`search_associates` or `list_bench_aging`).
  2. "Whose certifications expire soon?" → reply names the cert holder;
     `list_expiring_certifications` ran.
  3. "Allocate <allocated associate> to <project> at 50%" → response carries
     a `ProposedAction` whose `warnings` include the over-capacity warning;
     nothing was mutated (allocation count unchanged).
  4. "Who is our CEO?" → reply does **not** contain any seeded associate
     name presented as an answer; accepts a can't-answer style reply (the
     assertion is the absence of invention, not exact wording).
- **Tolerance:** live-model assertions check *properties* (a name present, a
  warning present, a tool called), never exact prose — the suite must not
  flake on wording. If the model legitimately answers question 1 via a
  different roster tool, the assertion accepts either listed tool.

## Error handling

- Logging must never break the turn: `AssistantInteractionLog.record(...)`
  catches and swallows its own failures (a logging bug must not take Mirai
  down).
- `ERROR` outcome logs before the exception propagates to the existing
  `GlobalExceptionHandler` path — no change to user-facing error behavior.
- The loop-cap stub server shuts down in `@AfterEach` even on assertion
  failure.

## Testing & verification

TDD per AGENTS.md — every new behavior starts from a failing test.

- **`AssistantInteractionLogTest` / additions to `AssistantApiTest`:** via a
  Logback `ListAppender` on the interaction logger — an answered turn logs
  `outcome=ANSWERED` with the right user, tools in call order, and question;
  a draft turn logs `DRAFTED`; a Gemini failure logs `ERROR` and the API
  still returns the mapped error; the reply text never appears in the line;
  quotes in questions are escaped.
- **`GeminiToolLoopCapTest`:** as specified above.
- **Golden suite:** skipped in normal builds (asserted by its gate); manual
  live run documented in `docs/TECHNICAL.md`.
- **Definition of done:** full `./mvnw test` green (Spotless + ArchUnit);
  no frontend changes in this phase; `docs/TECHNICAL.md` updated (interaction
  log line format, `base-url` property, golden-eval run instructions);
  `docs/TODO.md` decisions updated (log-not-DB decision, rate limiting
  consciously deferred); graph refreshed; small focused commits.

## Out of scope

Rate limiting (user decision — revisit before widening access), DB-backed
interaction history or any UI over the log, reply-text capture, streaming,
write-tool expansion, role-aware tools — later phases of the 2026-07-16
Mirai review.
