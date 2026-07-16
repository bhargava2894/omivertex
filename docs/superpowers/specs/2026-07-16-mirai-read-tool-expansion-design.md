# Mirai — read-tool expansion (skill gaps, certifications, workforce summary, bench aging)

**Date:** 2026-07-16 · **Status:** Approved

## What & why

Mirai's read tools cover people, projects, positions, and roll-offs, but the app
computes far more than the assistant can see. Most visibly, the suggestion chip
"Summarize our biggest skill gaps" promises an answer the model has no tool to
ground — it improvises from `search_associates` and does it badly. This phase
adds **four read tools**, each a thin text formatter over service math that
already exists and is already tested:

| Tool | Grounds questions like | Backing source |
|---|---|---|
| `get_skill_gaps` | "What are our biggest skill gaps?" | `SkillGapService.dashboardPanel()` |
| `list_expiring_certifications` | "Whose certs expire in the next 60 days?" | `DashboardService` cert-expiry filter (extracted) |
| `get_workforce_summary` | "How healthy is the org? Where is utilization heading?" | `DashboardService.summary()` |
| `list_bench_aging` | "Who has been on the bench longest?" | `summary()`'s `benchAging` + `benchAssociates` |

No new math anywhere: **one source of truth per concept** holds. Write tools,
security, and the draft-only contract are unchanged.

## Architecture

The established pipeline gains four entries, nothing structural changes:

- **`AssistantContextBuilder`** — four new formatter methods; two new injected
  dependencies (`SkillGapService`, `DashboardService`). It stays the one place
  that turns workforce data into model-facing text; all four results respect
  `MAX_TOOL_ROWS` (25) and reuse the shared overflow line where applicable.
- **`GeminiHttpClient.READ_TOOLS`** — four new function declarations. Only
  `list_expiring_certifications` takes an argument (`withinDays`, integer,
  optional). The other three take no arguments.
- **`AssistantService.executeReadTool`** — four new dispatch cases, using the
  existing lenient coercion helpers (`intOrDefault`).
- **Standing prompt** (`AssistantContextBuilder.build()`) — the tool-name list
  in the instructions mentions the four new tools so the model knows to call
  them.

Rejected alternatives: recomputing gap/utilization math from repositories
inside `AssistantContextBuilder` (duplicates tested math; drift risk), and a
separate formatter component (premature — the class still has one purpose).

## Tool contracts

**`get_skill_gaps`** (no args) — formats `SkillGapService.dashboardPanel()`:
demand-driven rows, worst gap first, capped by that panel's own limit of 20
(under `MAX_TOOL_ROWS`). Row shape:
`- React (Frontend) · demand 4 · bench supply 1 · total holders 6 · gap 3`.
Empty state: `No open positions demand any skills right now.`

**`list_expiring_certifications`** (`withinDays`, default **90** = the
dashboard's `CERT_EXPIRY_HORIZON_DAYS`) — the expiring-cert filter currently
inlined in `DashboardService.summary()` moves to a public
`DashboardService.expiringCerts(int withinDays)`; `summary()` calls it with the
constant so the dashboard behavior is byte-identical, and the tool calls it
with the requested window. Upcoming expiries only (not already-expired), sorted
soonest first, capped at `MAX_TOOL_ROWS` + overflow line. Row shape:
`- Priya Sharma — AWS Solutions Architect, expires 2026-08-02 (in 17 days)`.
Empty state names the window: `No certifications expire within N days.`

**`get_workforce_summary`** (no args) — formats `DashboardService.summary()`
into compact sections:

- **KPIs:** active associates, billable / non-billable / bench counts,
  onshore / offshore, active clients & projects, open positions,
  utilization %, exits in the last 12 months.
- **Bench aging buckets:** ≤30d / 31–60d / >60d counts (boundaries come from
  the DTO, which owns them).
- **Trend:** the six trailing months, `MMM total/billable` per point.
- **Forecast:** the 0/30/60/90-day utilization points with delta and each
  point's named drivers (roll-off / ramp-up / exit, person, project, date),
  already capped at 5 drivers + "…and N more" by the service.

The DTO's roll-off, expiring-cert, and skill-gap sections are deliberately
**not** formatted — those have their own tools, and repeating them here would
bloat every summary answer. `summary()` computing them anyway is accepted
waste at this scale (correctness and reuse over micro-efficiency).

**`list_bench_aging`** (no args) — formats the same summary DTO: the three
bucket counts as a headline, then the bench roster (already sorted
longest-first) capped at `MAX_TOOL_ROWS` + overflow line. Row shape:
`- Anil Kumar · Senior Engineer · 74 days on bench`.
Empty state: `No one is on the bench.`

## Frontend

One suggestion chip swap in `AssistantChat.jsx` so the new surface is
discoverable: "Who matches our open positions?" (redundant with the
open-positions chip) becomes **"Whose certifications expire soon?"**. The
existing "Summarize our biggest skill gaps." chip stays — it finally works.
No other UI changes.

## Error handling

Existing patterns only: lenient arg coercion (bad `withinDays` falls back to
90), per-tool plain-text empty states, and the dispatch `default` already
answers unknown tool names. Tool failures never throw into the model loop.

## Testing & verification

TDD per AGENTS.md — each formatter starts from a failing test.

- **`AssistantContextBuilderTest`:** per formatter — seeded data produces the
  documented row shape; empty states; row caps + overflow line
  (`list_expiring_certifications`, `list_bench_aging`); the `withinDays`
  window includes a cert expiring on the boundary day and excludes one past
  it and one already expired; `get_workforce_summary` includes KPIs, buckets,
  trend, and forecast drivers but **not** roll-off/cert/gap rows; `build()`
  names all four new tools.
- **`AssistantApiTest`:** with the stubbed `GeminiClient`, each new tool name
  dispatches to its formatter and the result reaches the model turn;
  `expiringCerts` extraction keeps the dashboard summary response unchanged.
- **Definition of done:** `./mvnw test` green (Spotless + ArchUnit);
  `cd frontend && npm run format && npm run build` for the chip swap;
  `docs/TECHNICAL.md` assistant tool table updated; graph refreshed; small
  focused commits.

## Out of scope

Write-tool expansion (end/edit allocation, propose position), role-aware
tool filtering, admin-only tools (audit, pending approvals), streaming,
history persistence, feedback capture, rate limiting, interaction logging —
all tracked as later phases of the 2026-07-16 Mirai review.
