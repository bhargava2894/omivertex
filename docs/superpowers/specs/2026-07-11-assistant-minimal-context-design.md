# Assistant Minimal Context + Read Tools — Design

**Date:** 2026-07-11
**Status:** Approved by user
**Problem:** Every assistant message ships the FULL workforce roster (names,
emails, skills, allocations for every active associate) to the Gemini API. At
production scale (1000+ associates, 100+ clients) this degrades answer
accuracy (needle-in-haystack), adds latency and cost per message, and sends
all employee data to Google on every question.

**Supersedes:** the 2026-07-10 "AI assistant sends FULL workforce detail"
resolved decision in `docs/TODO.md` (mark it superseded there, keep for
history).

## What & why

Replace context-stuffing with **retrieval by tools** (chosen over a vector/RAG
store — the data is structured in Postgres; exact filters beat semantic search
and need no sync infrastructure).

### 1. Standing context = aggregates only

`AssistantContextBuilder.build()` returns only:
- system instructions (answer from data, use tools for specifics, never invent
  people/projects/numbers),
- today's date,
- counts: active associates, bench, open positions, clients, projects.

**No names, emails, skills, or allocations** are included. Personal data
leaves the server only when a question triggers a tool call for it.

### 2. Read tools (server-side, results fed back to the model)

Alongside the existing `get_position_matches`:

| Tool | Args | Returns (compact text/JSON) |
|---|---|---|
| `search_associates` | `name?`, `skill?`, `minProficiency?`, `benchOnly?` | ≤ 25 rows: name, designation, work mode, bench-days or current project@client, matched skills |
| `get_associate_detail` | `name` | one associate's full line: skills+proficiency, current allocations, bench days, upcoming exit (ambiguous name → candidate list) |
| `list_rolloffs` | `withinDays` (default 30) | current allocations ending within the window: who, project@client, end date |
| `list_open_positions` | — | open positions: title, project@client, %, billable, work mode, must-/nice-to-have skills |

Rules:
- **Row cap 25** per tool result (named constant) — a tool reply can never
  blow up the prompt.
- `MAX_TOOL_ROUNDS` in `GeminiHttpClient` goes **2 → 3** (a people question
  needs search → optional detail → answer).
- All five read tools route through the existing `ToolExecutor` loop; unknown
  filters are ignored leniently (model output is untyped).

### 3. Seams

- `AssistantContextBuilder` stays the **only data formatter**: gains one
  public method per tool (`summary()` used by `build()`,
  `searchAssociates(...)`, `associateDetail(name)`, `rolloffs(days)`,
  `openPositions()`).
- `AssistantService.executeReadTool` dispatches tool name → builder method
  (name resolution reuses the service's existing exact-then-contains helper).
- `GeminiHttpClient` adds the four function declarations and a
  `READ_TOOLS` set replacing the single-name check.
- Write-action drafting (`propose_allocation`, `propose_position_fill`) is
  **unchanged**. Frontend is unchanged (same endpoint and response shape).

### 4. Testing & privacy pin

- Context test asserts the standing context **does not** contain any
  associate name or email and **does** contain the counts.
- One API test per tool using the established stubbed-Gemini
  `thenAnswer(inv -> executor.execute(...))` pattern.
- Existing action-draft tests keep passing untouched.
- Full suite green before every commit.

## Out of scope

- Vector embeddings / semantic search over resume contents.
- Gemini context caching.
- Any change to write actions, roles, or the chat UI.
