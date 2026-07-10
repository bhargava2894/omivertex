# OmiVertex — Exit Tracking, Multi-Skill Matching, Gap/Forecast Dashboard, Associate Self-Service

**Date:** 2026-07-10
**Status:** Approved (user confirmed design in session)
**Build order:** F1 → F2 → F3 → F4 (F3's heatmap consumes F2's demand data; F3's forecast consumes F1's exits)

Money/rate features are explicitly out of scope (future enhancement per user).

---

## F1 — Exit tracking

**Problem:** The system cannot represent someone leaving. Leavers linger as ACTIVE with
open allocations, and "how many people quit this year" is unanswerable.

### Data model (Flyway V6)

Three nullable columns on `associates`:

| Field | Type | Meaning |
|---|---|---|
| `resignation_date` | date | Day notice was given (optional) |
| `last_working_day` | date | Final day of employment |
| `exit_reason` | varchar enum | `RESIGNED, TERMINATED, CONTRACT_ENDED, RETIRED, OTHER` |

Validation (service layer): `exitReason` and `lastWorkingDay` must be set together → 400
otherwise; `resignationDate ≤ lastWorkingDay` → 400 otherwise.

### Behavior

- **Auto-cleanup** (user choice: "full auto-cleanup"): once `today > lastWorkingDay` and
  the associate is still ACTIVE — flip status to INACTIVE, end every open-ended or
  later-ending allocation at `lastWorkingDay`, write one audit entry
  (`EXITED`, "… left on … (reason); N allocation(s) ended").
- Implemented in `AssociateService.processExits()`:
  - invoked by a daily `@Scheduled` job (new `@EnableScheduling`), and
  - invoked inline on associate create/update when the entered `lastWorkingDay` is
    already past — no waiting for the scheduler.
  - Idempotent: already-INACTIVE associates are skipped.
- Tests call `processExits()` directly (no scheduler in tests).

### Surfacing

- Associate edit form: new "Exit" section (reason dropdown + two date fields), admin-only
  like the rest of the form.
- `AssociateResponse` carries the three fields; profile shows an "Exited" banner when set.
- Dashboard KPI tile **"Exits (last 12 mo)"**: count of associates with
  `lastWorkingDay` in `[today−365d, today]`. Field `exitsLast12Months` on
  `/dashboard/summary`.

---

## F2 — Multi-skill positions & smarter matching

**Problem:** A position holds exactly one structured skill. Real demand is
"Java AND AWS must-have, Kubernetes nice-to-have, onshore only".

### Data model (Flyway V7)

New table `position_skills`:

| Field | Type | Notes |
|---|---|---|
| `position_id` | FK → open_positions | |
| `skill_id` | FK → skills | |
| `min_proficiency` | varchar enum | nullable → treated as NOVICE |
| `required` | boolean | true = must-have, false = nice-to-have |

Unique `(position_id, skill_id)`. `open_positions` gains nullable `work_mode`
(`ONSHORE`/`OFFSHORE`, null = any).

**Migration:** V7 copies each existing `required_skill_id` + `min_proficiency` into
`position_skills` as a must-have, then drops those two columns from `open_positions`.
The entity's `requiredSkillRef`/`minProficiency` fields are removed. The legacy
free-text `requiredSkill` stays as the documented text-match fallback for positions with
no structured skills (existing deliberate exception — do not extend it).

### API

- `PositionRequest`: `skills: [{skillId, minProficiency, required}]` (replaces
  `requiredSkillId`/`minProficiency`), plus `workMode`. Duplicate skillIds → 400.
- `PositionResponse`: `skills: [{skillId, skillName, category, minProficiency, required}]`,
  `workMode`.
- `MatchCandidateResponse` gains: `fullMatch` (boolean), `matchedSkills` (names),
  `missingRequirements` (human strings: `"AWS (min Intermediate)"`,
  `"onshore required"`), keeps `availablePercent`, `benchDays`, `matchedProficiency`
  dropped in favor of the lists.

### Matching algorithm (user choice: partials shown, ranked lower)

1. Candidates: ACTIVE associates with `availablePercent ≥ position.allocationPercent`
  (unchanged capacity rule).
2. Per candidate compute: must-haves met / total, nice-to-haves met, work-mode ok
  (`position.workMode == null` or equals associate's), missing list.
3. **Full match** = all must-haves met AND work-mode ok.
4. Sort: full matches first; within each group — must-haves met desc, nice-to-haves met
  desc, bench days desc (nulls last), name. Limit 10 (unchanged).
5. Positions with no structured skills fall back to today's legacy text matching
  (text hit ⇒ treated as full match).

### Frontend

`Positions.jsx`: the single skill picker + proficiency select is replaced by a
requirements editor — rows of (taxonomy skill picker, min proficiency, Must/Nice toggle),
add/remove row; plus a Work-mode select (Any/Onshore/Offshore). Match modal shows a
"Full match" badge or the missing list per candidate.

---

## F3 — Dashboard: skill gap heatmap + utilization forecast

Both are additions to `/dashboard/summary` (computed live like everything else) and
rendered in the existing hand-rolled SVG chart style. All counts follow the dashboard's
ACTIVE-associates-only rule.

### Skill gap heatmap

For every skill that appears as a **must-have on an OPEN position**:

| Column | Definition |
|---|---|
| `demand` | # open positions requiring the skill |
| `benchSupply` | # bench associates (no current allocation) holding the skill at ≥ threshold |
| `totalSupply` | # active associates holding the skill at ≥ threshold |
| `gap` | `demand − benchSupply` |

Threshold per skill = the **lowest** `min_proficiency` among the open positions demanding
it ("could fill at least one seat"). Response field `skillGaps`, sorted `gap` desc,
capped at 20 rows. UI: table-style heatmap, gap tone red (> 0) / amber (= 0) /
green (< 0), with category + skill name.

### Utilization forecast

Field `utilizationForecast: [{label, percent}]` for `Today, +30d, +60d, +90d`.
`percent` at date D = the existing FTE-weighted formula evaluated **as of D**:

- denominator: ACTIVE associates, excluding those with `lastWorkingDay < D` (F1 data),
- numerator: billable allocations active at D (`start ≤ D` and `end null or ≥ D`) for
  those associates.

Deterministic — no probabilities. UI: small line chart titled "Utilization forecast",
captioned **"assumes no new assignments"**.

---

## F4 — Associate self-service (role + approval queue)

**Problem:** Associates cannot log in; their skills/resumes go stale because only admins
can edit.

### Role & sign-in (user choice: reuse existing approval flow)

- `Role` gains `ASSOCIATE`. The Access Requests approve payload already carries a role —
  the admin UI dropdown gains "Associate".
- Approving as ASSOCIATE requires the request email to match an `associates.email`
  (case-insensitive) → else 400 with a clear message. On approval the `AppUser` is linked
  via new nullable `app_users.associate_id` FK (Flyway V8).
- Security (`SecurityConfig`): `/api/v1/me/**` → `hasAnyRole("ASSOCIATE")`;
  ASSOCIATE gets **no** other `/api/v1/**` access (existing GET rule stays
  ADMIN+VIEWER-only). `/api/v1/auth/**` unchanged. Associates therefore see only their
  own data — roster/bench/staffing of colleagues stays admin/viewer-only.

### Pending-change model (approach A: live data never changes without approval)

New table `profile_change_requests` (Flyway V8):

| Field | Notes |
|---|---|
| `associate_id` FK | requester |
| `type` | `SKILLS` or `RESUME` |
| `skills_payload` | JSON text `[{skillId, proficiency, primary}]` (SKILLS only) |
| `resume_filename / content_type / byte_size / content` | (RESUME only) |
| `status` | `PENDING / APPROVED / REJECTED` |
| `note` | admin's rejection note, optional |
| `created_at, decided_at, decided_by` | audit fields |

Rule: at most one PENDING request per (associate, type) → 409 on a second submission.

### Endpoints

Associate (`/api/v1/me`, ASSOCIATE role):
- `GET /me/profile` — own `AssociateResponse`
- `GET /me/profile-changes` — own requests, newest first
- `POST /me/profile-changes/skills` — body `{skills:[…]}`, validated against taxonomy
- `POST /me/profile-changes/resume` — multipart file (same type/size rules as the
  existing resume upload)

Admin (`/api/v1/profile-changes`, ADMIN role):
- `GET ?status=PENDING` — the queue
- `POST /{id}/approve` — applies the change through the **existing** services
  (`AssociateService.replaceSkills` / `ResumeService` upload), so taxonomy validation,
  headline derivation, and audit all fire exactly as if an admin made the edit
- `POST /{id}/reject` — body `{note}` optional

### Frontend

- **Associate experience:** after login with role ASSOCIATE, the app shows a single
  "My Profile" view (reusing the existing profile components) with current skills,
  certifications, resume — plus "Propose skill changes" (the existing `SkillEditor`,
  prefilled) and "Upload new resume". Pending/rejected requests show as a status banner.
- **Admin experience:** new nav item **"Profile Changes"** — queue page in the Access
  Requests style: requester, type, submitted date, a diff-style preview (current vs
  proposed skills; resume filename/size), Approve / Reject buttons.

---

## Cross-cutting

- **Migrations:** V6 (exit columns) · V7 (position_skills + work_mode, drop old columns)
  · V8 (app_users.associate_id + profile_change_requests).
- **TDD:** every behavior lands with a failing HTTP-level MockMvc test first, in the
  existing `src/test/java/.../api/` style. Scheduled exit job tested via direct service
  call.
- **Conventions:** enums not strings; DTOs at the web boundary; business rules + audit in
  services; no magic numbers (horizon/threshold constants named); frontend uses shared
  components (`SkillEditor`, `DataTable`, `Modal`, `Badge`) and CSS tokens.
- **Docs:** `TECHNICAL.md` (entities, endpoints, dashboard shape, roles) and `TODO.md`
  (resolved decisions: auto-cleanup on exit, partials-ranked-lower matching, deterministic
  forecast, pending-change approval model; future: reporting lines/manager approval,
  money layer, notifications) updated as each feature lands.

## Explicitly out of scope (deferred by user)

Bill rates / margins / bench cost in dollars · manager reporting lines (approver is any
Admin for now) · notifications/digests · predictive (probabilistic) forecasting ·
ATS/timesheet integrations.
