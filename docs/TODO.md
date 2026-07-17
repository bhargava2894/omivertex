# OmiVertex — Remaining Work

Prioritized backlog as of 2026-07-16. Everything above the line blocks calling
this "production"; everything below improves an already-usable pilot.

## P0 — Security & deployment blockers — DONE (2026-07-06)

- [x] **Verify Google ID tokens server-side.** `/api/v1/auth/google` now requires
      a real Google ID token, verified server-side (signature + audience) by an
      injectable, config-gated `GoogleTokenVerifier` that fails closed when no
      client ID is set. The client-posted email is no longer trusted. Enabling
      real sign-in is now an ops step: set `OMIVERTEX_AUTH_GOOGLE_CLIENT_ID` +
      `VITE_GOOGLE_CLIENT_ID` (see `docs/DEPLOYMENT.md`).
- [x] **TDD the access-request flow.** `AuthApiTest` covers the Google path
      (unverifiable→401, verified new company user→PENDING, approved→200,
      non-company→400) with a mocked verifier; `AdminAccessRequestApiTest` covers
      list/approve/reject/404 + viewer-403. Suite at 110 tests.
- [x] **Deploy off the laptop.** Multi-stage `Dockerfile` (SPA + jar, non-root
      JRE, `prod` profile) + `.dockerignore`; `docs/DEPLOYMENT.md` documents the
      reverse-proxy/TLS + Secure-cookie setup.
- [x] **PostgreSQL backups.** `ops/backup.sh` — compressed timestamped `pg_dump`
      with retention pruning + cron example + restore docs.
- [x] **Change default passwords.** `prod` profile requires
      `OMIVERTEX_ADMIN_PASSWORD`/`OMIVERTEX_VIEWER_PASSWORD` (no fallback);
      `ProductionSafetyCheck` fails startup if either equals the dev default.
- [x] **Adopt Flyway.** `V1__baseline_schema.sql` + `ddl-auto=validate` in prod;
      dev/tests keep auto-DDL with Flyway disabled. V1 verified against a fresh DB.

Also hardened in this pass: session cookie `HttpOnly` + `SameSite=strict`
(+ `Secure` in prod), a 10 MB import-upload cap, and content-hashed frontend
assets served `immutable` while the shell/API stay `no-store` (fixes stale
dashboard through caches/proxies).

## P1 — Pilot hardening

- [ ] **Per-person accounts everywhere.** Google verification has landed; still
      need to retire the shared `admin`/`viewer` logins so the audit trail names
      real people.
- [ ] **Optimistic locking** (`@Version` on Allocation/Associate) so two managers
      editing the same record don't silently overwrite each other.
- [~] **Server-side pagination + search** — DONE for the associates roster
      (paged envelope + `q` search, backward compatible). Remaining lists tracked
      in P3.

## P1.5 — Résumé Parsing & Matching — DONE (2026-07-11)

- [x] **Résumé Parsing & Extraction Suite**
  - [x] Task 1: resumes table, entity, repository (V3, PDFBox) — DONE
  - [x] Task 2: ResumeTextExtractor (PDFBox + POI) — DONE
  - [x] Task 3: ResumeSkillMatcher (keyword match) — DONE
  - [x] Task 4: POST /resumes/parse endpoint — DONE
  - [x] Task 5: upload/download/delete résumé (replace) — DONE
  - [x] Task 6: resumeFilename on profile response — DONE
  - [x] Task 7: frontend API methods — DONE
  - [x] Task 8: New Associate form résumé field — DONE
  - [x] Task 9: Profile résumé card — DONE
  - [x] Task 10: docs + graph refresh + final verification — DONE

## P2 — Product roadmap (in rough impact order)

- [ ] **Export skills and certifications** — include skill and certification columns in spreadsheet/document exports.
- [x] **Seed data for skill taxonomy** — DONE: SeedDataLoader seeds 18 categories / 126 skills once when the taxonomy is empty.
- [ ] **Visa / work-authorization tracking** — status + expiry with a 90-day
      alert on the dashboard; standard for onshore/offshore staffing.
- [ ] **Bill rates & margin** — rate on the allocation → revenue/margin by
      client/project. Needs a finance-visibility role tier; confirm with ops
      who may see rates before building.
- [ ] **Notification digests** — email/Slack summary of upcoming roll-offs and
      bench > 30 days, so the dashboard comes to the managers.
- [ ] **Monthly snapshot reporting** — "headcount as of <date>" filter (the
      trend chart already computes this logic; expose it as a parameter).

## P3 — Nice to have

- [ ] Documentation stitch-ups the knowledge graph flagged: `ExportService` and
      the duplicate-open-allocation rule are described in docs but weakly
      cross-referenced to their code.
- [ ] `graphify --wiki` for an agent/human-crawlable architecture wiki.
- [ ] Server-side pagination for the remaining lists (clients/projects/staffing).
      Associates is already server-paged+searched; the others are small and use
      derived-field filters, so deferred until volume warrants it.
- [ ] Extract the profile's allocation section (history table + End/Assign modals,
      ~250 lines) into a `ProfileAllocations` component — `Profile.jsx` is ~900
      lines with five modals (flagged in the 2026-07-10 code review).
- [ ] Shared local-date helper: `todayStr()` (Profile) duplicates `today()`
      (Staffing), and both use `toISOString()` which is UTC — evening users west
      of UTC get tomorrow's date. Extract a `dates.js` local-date version.
- [ ] Import runs one transaction per file: a runtime exception thrown *through a
      repository proxy* inside the row loop (not the guard's ConflictException,
      which is handled) could still mark the batch rollback-only. Consider per-row
      savepoints if imports grow (flagged in the 2026-07-10 review).
- [ ] Assistant tool-registration coherence test: a read tool's name must agree
      across `GeminiHttpClient.FUNCTION_DECLARATIONS`, `READ_TOOLS`, and the
      `AssistantService.executeReadTool` switch — today a typo in one of the
      three only surfaces at runtime against the live Gemini API (flagged in the
      2026-07-16 Mirai read-tool reviews; needs a visibility decision on the
      private declarations list).

## Resolved decisions

- **Mirai chat history is per-tab sessionStorage, not server-side** (2026-07-18):
  a refresh restores the conversation (last 40 messages incl. draft cards);
  the tab closing or logout wipes it. Deliberately no server persistence and
  no localStorage — replies contain roster data, which must not get a durable
  home outside the entity tables. Per-reply feedback buttons were considered
  and descoped by the user.

- **Assistant tools are role-registered** (2026-07-17): `GeminiClient.replyWithTools`
  takes the caller's admin flag; admin-only tools (`list_pending_approvals`,
  `get_audit_history`) are absent from a viewer's declarations AND refused at
  dispatch (defense in depth). The write-draft contract is unchanged — Mirai
  still never mutates; new END/EDIT/CREATE_POSITION drafts confirm through the
  existing endpoints under the user's own session.

- **Mirai interactions are logged, not stored** (2026-07-16): one log line per
  assistant turn (user, outcome, tools, latency, question) through Logback into
  the rotating app log. Deliberately no DB table (user decision: storage is
  waste at this scale) and the reply text is never written anywhere — roster
  data must not get a second home outside the entity tables. Known conscious
  gap: a turn rejected by the AiExecutor bulkhead (503) never reaches the
  service, so it produces no MIRAI line. Revisit only if usage analytics need
  querying.
- **Assistant rate limiting consciously deferred** (2026-07-16): the AiExecutor
  bulkhead bounds concurrency but nothing bounds per-user request volume/spend.
  Descoped from the ops-hardening phase by user decision; add before widening
  assistant access beyond the current user base.

- **Staffing & Allocations are one page; the server tree is the single source**
  (2026-07-16): Allocations and Staffing rendered the same client → project →
  associate tree with the same billable rollup — once grouped client-side, once
  server-side (spec: `docs/superpowers/specs/2026-07-16-merge-staffing-allocations-design.md`)
  — violating "one source of truth per concept" (AGENTS.md). Merged into a single
  `staffing` route: viewers get the read-only tree as before, admins get inline
  Assign/Edit/Remove reusing the existing `/allocations` CRUD (capacity guard,
  uniqueness, and audit untouched — no new mutation endpoint). `GET /staffing?includeEnded=`
  also returns non-current allocations marked `active:false`; counts always reflect
  current allocations only so the rollup never mixes in history.

- **MyProfile shows a read-only "My Details" block** (2026-07-16): the ASSOCIATE
  self-service page previously surfaced only what a person can *change* (skills,
  résumé); their own contact/identity fields (email, company, location, work mode,
  designation, joined date + tenure, status) were never shown as a first-class,
  clearly non-editable block (spec:
  `docs/superpowers/specs/2026-07-16-associate-readonly-details-design.md`). Pure
  frontend addition — every field already ships on `/me/profile`. Scope deliberately
  excludes project history and certifications for this pass.

- **The Utilization Forecast names the events behind each number; drivers are never
  individually scored** (2026-07-15): the panel showed four bare percentages and could not
  say why any of them differed from today (spec:
  `docs/superpowers/specs/2026-07-15-utilization-forecast-drivers-design.md`). Each horizon
  now carries a net delta against today and expands to the roll-offs, ramp-ups and exits
  behind it. Exits are split into `BENCH_EXIT` / `BILLABLE_EXIT` because a benched leaver
  *raises* utilization (they leave the denominator, billable FTE is unchanged) — the
  opposite of what readers assume. We publish the net delta only, never a per-driver point
  value: utilization is a ratio, so once an exit moves the denominator the per-driver
  effects genuinely do not sum to the net, and shipping numbers that don't add up would be
  read as a bug. Deltas are measured against Today rather than the previous horizon so each
  row answers "where do I land versus now" on its own.

- **Assistant can enumerate clients and projects; the list always reconciles with the
  count** (2026-07-15): Mirai could answer "how many clients?" (the standing context
  carries the counts) but not "which ones?" — no read tool could enumerate, so it could
  only name clients that happened to appear on an open position, and correctly refused to
  guess the rest. Added `list_clients` and `list_projects` (spec:
  `docs/superpowers/specs/2026-07-15-assistant-client-project-listing-design.md`). Both
  list **every** row rather than filtering to ACTIVE, marking non-active rows inline: the
  standing context's `Clients:`/`Projects:` numbers come from `count()` (all rows), so an
  active-only list would contradict the number Mirai had just quoted. Names still leave the
  server only on request and still under the 25-row cap, so this extends rather than
  reverses the 2026-07-11 aggregates-only privacy decision.

- **A zero gap with zero demand reads "fully deployed", not "tight"** (2026-07-13):
  `gap = demand - benchSupply`, so a skill nobody is hiring for and nobody is free for
  scores 0 — identical to a skill whose demand is exactly met. Both views used to badge
  that as `tight`, which told managers a skill was at knife's edge when nothing was open.
  The rule now branches on demand, and lives in one shared module (`frontend/src/skillGap.js`)
  because the Dashboard panel and the Skill Reports rows had already drifted: the Dashboard
  inlined the logic while the report kept a private copy.

- **Assistant `get_associate_detail` includes past projects + certifications**
  (2026-07-13): the per-associate detail tool now lists ended allocations as
  "past projects" (most-recently-ended first) and the associate's certifications
  (soonest-expiring first, each flagged valid/expired), both capped at
  `MAX_TOOL_ROWS`. Before this the assistant wrongly reported "no previous
  project history" and could not answer "is X certified in …" — it only ever saw
  current staffing. Both are kept out of the shared `appendStaffing` fragment so
  the compact `search_associates` rows stay short — detail-view only. Verified
  end-to-end against live Gemini (Pavan Sista's past project, Rohan Gupta's AWS
  cert, no hallucination on unknown names). Principle: the detail tool must carry
  whatever a user would ask about a person; audit it when adding person-level data.

- **AI bulkhead + timeouts** (2026-07-11): all Gemini calls run on a dedicated
  4-thread executor (queue 8; saturation → fast 503) behind async controllers,
  with 5s connect / 30s read timeouts. Sized small deliberately — the upstream
  is the bottleneck; revisit pool size only with observed contention. Chosen
  over a background job queue (overkill for interactive endpoints).

- **Plan docs are disposable; delete after merge** (2026-07-11, revised 2026-07-12):
  implementation plans in `docs/superpowers/plans/` are never updated after their run
  and are **deleted once the feature merges** — git history is the archive, a kept
  plan is dead weight. Specs (`docs/superpowers/specs/`), `docs/TECHNICAL.md`, and
  this file are the living documentation and are kept. Enforced for all agents in
  `AGENTS.md`. (The 15 already-implemented plans were removed 2026-07-12.)

- **The assistant executes nothing server-side** (2026-07-11): write tools
  (`propose_allocation`, `propose_position_fill`) only ever produce a visible
  draft card; execution happens in the browser through the existing endpoints,
  so role checks, the ≤100% capacity guard, and audit fire exactly as manual
  edits — and prompt injection in workforce data can at worst draft a card the
  user must read and confirm. Name resolution requires a unique ACTIVE match
  (exact-then-contains) or the assistant asks back; the read tool
  (`get_position_matches`) is capped at 2 rounds per turn.

- **AI resume parsing fails open to keyword matching** (2026-07-11): any Gemini
  failure (no key, upstream error, malformed JSON) silently degrades to the
  word-boundary matcher — parsing never breaks an environment. AI input is capped
  at 20k chars; unknown skill ids are dropped, unknown proficiencies degrade to
  INTERMEDIATE. The LLM only drafts: all writes still flow through
  replace-skills / propose+approve.

- **Skill-gap report reuses `DashboardSummaryResponse.SkillGap`** (2026-07-11): one DTO
  for one concept — `/reports/skill-gaps` and the dashboard panel share `SkillGapService`
  math and shape. Positions carrying only the legacy free-text `requiredSkill` (no
  structured `PositionSkill` rows) are excluded from gap demand, consistent with the
  structured-skills direction.

- **Gemini model selection & systemInstruction bypass** (2026-07-10): due to quota limitations on free tiers and deprecations of older models, `gemini-3.1-flash-lite` is chosen as the default model. Additionally, since the `systemInstruction` field is rejected by the upstream stable REST API version, system context is merged directly into the first prompt in the `contents` list, ensuring compatibility across all models.

- **~~AI assistant sends FULL workforce detail to the Gemini API~~ SUPERSEDED
  2026-07-11** (originally 2026-07-10) by the minimal-context decision below. The
  parts that still hold: resume file contents are never sent; the vendor stays
  behind the `GeminiClient` interface; ASSOCIATE-role users cannot reach the
  endpoint.

- **AI assistant sends aggregates only; roster data is fetched per-question via
  read tools** (2026-07-11): the standing context is counts (associates, bench,
  open positions, clients, projects) — no names, emails, skills, or allocations.
  The model pulls specifics through server-side read tools (`search_associates`,
  `get_associate_detail`, `list_rolloffs`, `list_open_positions`,
  `get_position_matches`), each capped at 25 rows, max 3 tool rounds per turn.
  Only the queried slice of personal data ever reaches Google. Chosen over
  vector/RAG (structured Postgres data — exact filters beat semantic search).
  A privacy-pin test asserts the standing context contains no roster rows.

- **Exit auto-cleanup semantics** (2026-07-10): when an associate's last working day passes, a nightly scheduler flips their status to INACTIVE, truncates open-ended/overlapping allocations to end on their last working day, and removes any future allocations that never started.
- **Partials-ranked-lower matching** (2026-07-10): position skill matching prioritizes full matches (possessing all required skills above min proficiency and matching work mode) over partial matches. Candidates are ranked by must-have matches, then nice-to-have matches, then bench age.
- **Deterministic utilization forecast** (2026-07-10): the 30/60/90-day dashboard utilization forecast is computed deterministically from today's active roster and known allocation end-dates, assuming no new projects/allocations are added.
- **Pending-change approval model** (2026-07-10): associates can propose skill changes or upload a new resume. These edits do not apply live but sit in a pending queue until approved or rejected by an administrator.
- **ASSOCIATE role access boundary** (2026-07-10): a new ASSOCIATE role is introduced. AppUsers approved under this role are linked to their roster record. They can access only their own `/me/profile` surface; sidebar navigation is limited to "My Profile".
- **Import capacity rule** (2026-07-10): an import row that would push an associate
  past 100% is **rejected with a row error** (associate still imports) — chosen over
  auto-ending the older allocation (silently rewrites data) or importing at 0%
  (invents data). Same `assertCapacity` implementation as the assign flow; no copy.
  Pre-existing over-allocated data is fixed manually via the profile's End action —
  no migration.
- **Client-level billable counting** (2026-07-10): "billable wins" — a person with
  any billable current allocation under a client counts once, as billable, for that
  client (dashboard split + `/staffing` tree use the same rule). A person on two
  clients appears under both; that reflects reality.
- **Capacity end-date semantics** (2026-07-10): the guard counts an allocation's end
  date as still allocated, so capacity frees the day *after* the end date. The
  profile's Assign dialog defaults its start date to the day after the associate's
  latest current/just-ended end date so End → Assign passes with defaults.

- **Dashboard counts ACTIVE associates only** (2026-07-10): INACTIVE leavers (and
  their lingering open allocations) are excluded from every summary KPI — bench,
  headcounts, utilization denominator. `staffingTrend` deliberately stays
  historical (past allocations count regardless of today's status). Fixes leavers
  permanently inflating the bench and depressing utilization.
- **Position fill uses the seat's engagement window** (2026-07-10): `fill` creates
  the allocation from the position's `startDate` (today if none) to its new
  optional `endDate` (Flyway `V4`) — previously it always started today and open-
  ended, so filled seats consumed capacity early and never surfaced on the
  roll-off radar. A future-dated fill correctly leaves the associate off
  `currentProject` until the seat starts.
- **`joinedDate` anchors the bench clock** (2026-07-10): new optional field on
  Associate (Flyway `V5`, form field, `JOINED DATE`/`DOJ` import column). For
  never-allocated associates `benchDays` counts from it instead of `createdAt`,
  so importing a historical roster no longer resets everyone's bench age to the
  import day. Falls back to `createdAt` when absent.

- **Legacy `primarySkill`/`secondarySkill`** (2026-07): KEPT, deliberately demoted
  to informal free-text "headline" fields (roster quick-glance + CSV `SKILL`-column
  import + text-match fallback in `PositionService`). The structured `AssociateSkill`
  graph is authoritative for search, reports, and matching. Not removed because that
  would drop the fallback + import convenience (a functionality loss). Do not build
  new features on the free-text fields. See `Associate.java`.
- **Skills entry unified** (2026-07-07): the Add/Edit form's Primary/Secondary skill
  dropdowns were replaced by the shared `SkillEditor` (skill + proficiency + a
  "primary" star), used on both the form and the Profile page. `primarySkill`/
  `secondarySkill` are now **derived** server-side (`AssociateService.deriveHeadline`)
  from the starred skill — no longer hand-entered. New `AssociateSkill.is_primary`
  flag (Flyway `V2`). CSV import still populates the text fields in bulk (unchanged).
  See `docs/superpowers/specs/2026-07-07-unify-associate-skills-entry-design.md`.

- **~~Allocations page is a grouped drill-down~~ SUPERSEDED 2026-07-16** (originally
  2026-07-12): Allocations grew its own client-side grouping mirroring Staffing's
  server-side tree — two implementations of one concept. The 2026-07-16 merge
  (see the "Staffing & Allocations are one page" entry above) deleted `Allocations.jsx`
  entirely; the server-side `StaffingService` tree is now the only implementation.

- **Drill-down scaffolding extracted; motion via shared tokens** (2026-07-12):
  the client-card expand/collapse shell (header button, chevron, animated
  body) lives in `CollapsibleCard.jsx`, used by Staffing and Projects (also
  Allocations until its 2026-07-16 removal — see above). All framer-motion
  animation goes through `frontend/src/motion.js`
  tokens/variants and its `useMotionVariants` reduced-motion hook (the CSS
  `prefers-reduced-motion` kill-switch cannot reach framer-motion's inline
  styles). Route transitions, DataTable row animations, and dashboard tile
  staggers were deliberately excluded.

## Done (for reference)

SkillCloud Integration (structured skill taxonomy, associate profile pages with skills & certs tracking, faceted skill search, skill proficiency reports, cert-expiry dashboard radar, multi-sheet v2 imports, and smart demand matching) · Domain model + enforced rules (capacity guard, protective deletes, uniqueness) · 95 HTTP-level TDD tests · Excel/CSV import with dry-run preview + idempotency · xlsx/csv/pdf/docx export · dashboard (utilization, bench aging, roll-off radar, trend, charts) · demand matching (skills, open positions, one-click fill) · role-gated UI with server-side enforcement · audit trail with admin UI · dark/light themes · animated premium UI + login · docs for devs and sales · graphify knowledge graph integration · Vite build relocated out of `src/`.
