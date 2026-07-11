# Skill-Gap Heatmap · Resume Intelligence v2 · Assistant Actions — Design

**Date:** 2026-07-11
**Status:** Approved by user (sections reviewed individually)
**Build order:** heatmap → resume v2 → assistant actions (each independently shippable)

## What & why

Three features that turn OmiVertex from a roster of record into a staffing decision
tool:

1. **Skill-gap heatmap** — put skill *supply* (associates) and *demand* (open
   positions) side by side so HR sees hire/train/move decisions at a glance.
2. **Resume intelligence v2** — use the existing Gemini integration to read resumes
   properly (skills + proficiency + evidence + experience), fixing stale profiles at
   the source. Falls back to today's keyword matcher when no API key.
3. **Assistant with actions** — the dashboard assistant drafts allocations and
   position fills for one-click human confirmation instead of only answering
   questions.

**Architecture decision (approved):** extend the existing `GeminiClient` interface
per feature (structured extraction + native function-calling). No new frameworks
(Spring AI/LangChain4j rejected as YAGNI); no prose intent-parsing (rejected as
brittle for mutations). Tests keep stubbing the interface as `AssistantApiTest`
does today.

---

## Feature 1 — Skill-gap heatmap (full report)

> **Already built:** the Dashboard summary already computes a capped (20-row,
> demand-only) skill-gap list with this exact math (`DashboardService`,
> `skillGaps` panel on `Dashboard.jsx`, landed 2026-07-10). This feature is the
> **full-report extension** of that panel; the gap math below restates the
> existing rules and must stay consistent with them (one source of truth —
> extract the shared computation rather than duplicating it).

### Backend

- New `SkillGapService` + `GET /api/reports/skill-gaps`, readable by ADMIN and
  VIEWER (same as other reports). `DashboardService` delegates its capped
  `skillGaps` panel to the same computation (no second copy of the math).
- Unlike the dashboard panel: **no row cap**, and includes surplus rows —
  one row per skill that has open demand **or** at least one rated associate:
  - `demandSeats` — count of OPEN positions listing the skill as a **required**
    `PositionSkill`.
  - `benchCount` — active associates with no current allocation whose proficiency
    meets the **lowest** min-proficiency among those open seats (if no demand, any
    proficiency counts).
  - `allocatedCount` — other active associates meeting the same bar.
  - `gap` = `benchCount − demandSeats` (negative = shortage not staffable from
    bench today).
  - Plus `skillId`, `skillName`, `categoryName` for display/filtering.
- Default sort: worst gap first.
- **v1 exclusions (deliberate):** work-mode and billable are ignored; positions
  with only a legacy free-text `requiredSkill` (no structured `PositionSkill`
  rows) are excluded from demand — consistent with the structured-skills
  direction (`docs/TODO.md` legacy `primarySkill` note). Record in TODO.md
  resolved decisions.

### Frontend

- New "Skill Gaps" section on the existing **SkillReports** page (no new page or
  nav item): a `DataTable` with color-coded gap `Badge`s (CSS tokens only, no raw
  hex) + an `HBarChart` of the top shortages.

---

## Feature 2 — Resume intelligence v2

### Backend

- New method on `GeminiClient`: structured extraction. Input: extracted resume
  text (length-capped) + the skill taxonomy (ids + names). Output: strict JSON —
  matched skills each with estimated proficiency and a short evidence quote, plus
  an experience summary (years, recent roles).
- New `ResumeIntelligenceService` orchestrates:
  `ResumeTextExtractor` → Gemini (if key configured) → map JSON to suggestions.
  **Any failure (no key, timeout, malformed JSON) → fall back to the existing
  `ResumeSkillMatcher` keyword path, unchanged.** No feature regression in any
  environment.
- Existing `POST /api/resumes/parse` response gains optional fields:
  `proficiency`, `evidence`, `experienceSummary`, `source: AI | KEYWORD`.
  Backward compatible; existing callers unaffected.

### Frontend

- **MyProfile** (associate self-service) and **Profile** (admin): parse results
  pre-fill the existing skill editor / propose-skills form. The human reviews,
  adjusts, submits. Approval still flows through the untouched
  `ProfileChangeService`.
- **Invariant: the LLM never writes to the database; it only drafts.** All writes
  go through the existing propose → approve pipeline.

---

## Feature 3 — Assistant with actions

### Backend

- Extend `GeminiClient` with Gemini native function-calling. Three tool
  declarations:
  - `propose_allocation(associate, project, percent, billable, startDate, endDate)` — write-draft
  - `propose_position_fill(position, associate)` — write-draft
  - `get_position_matches(position)` — read; executed server-side via
    `PositionService.matches` and fed back to the model (max 2 tool rounds).
- When the model drafts a write, `AssistantService`:
  1. resolves names → ids (ambiguous name → assistant replies asking which one),
  2. pre-validates (entities exist, capacity headroom, position still OPEN),
  3. returns a `proposedAction` payload (type, resolved params, human-readable
     summary, validation warnings) in the chat response.
- **The assistant endpoint never mutates anything.** It stays read-only,
  role-gated exactly as today (ADMIN + VIEWER may chat; ASSOCIATE forbidden).

### Frontend

- `AssistantChat` renders a `proposedAction` as a card: summary + all parameters +
  warnings, with Confirm / Cancel.
- **Confirm calls the existing REST endpoints** (`POST /api/allocations`,
  `POST /api/positions/{id}/fill`) with the drafted payload under the user's own
  session — role checks, the ≤100% capacity guard, and audit records fire exactly
  as if the user had used the normal forms. Viewers see the card but cannot
  confirm (`canEdit` hides the button; the server rejects regardless).

### Security property

Prompt injection embedded in workforce data can at worst *draft* a fully visible
action the user must read and click — it can never execute one, because execution
only happens browser-side through existing authorized endpoints.

---

## Cross-cutting

- **TDD per AGENTS.md:** failing API test first, stubbed `GeminiClient`
  (existing `AssistantApiTest` pattern); unit tests for gap math and
  extraction-JSON mapping; `./mvnw test` green before every commit.
- **Errors:** typed exceptions via `GlobalExceptionHandler`. Gemini failures
  degrade gracefully: keyword fallback for parse; plain-text reply (no action
  card) for chat.
- **Frontend rules:** CSS tokens only; reuse `DataTable`/`Modal`/`Badge`/`Field`;
  respect `canEdit`; `npm run format && npm run build` before commit.
- **Docs:** `docs/TECHNICAL.md` contract updates per feature;
  `docs/TODO.md` resolved decisions (legacy free-text positions excluded from gap
  demand; assistant executes nothing server-side by design).
- **Graph:** refresh graphify after each feature lands.

## Out of scope (explicitly deferred)

- Cert-expiry notifications / event backbone (feature 2 of the original roadmap).
- What-if scenario planning (feature 5).
- Broader assistant actions (create/update/end allocations or positions).
- Work-mode/billable dimensions in gap math.
- Building on legacy `primarySkill`/`secondarySkill` free-text fields.
