# OmiVertex — Remaining Work

Prioritized backlog as of 2026-07-06. Everything above the line blocks calling
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

- [ ] **Per-person accounts everywhere.** Once Google verification lands, retire
      the shared `admin`/`viewer` logins so the audit trail names real people.
- [ ] **Optimistic locking** (`@Version` on Allocation/Associate) so two managers
      editing the same record don't silently overwrite each other.
- [~] **Server-side pagination + search** — DONE for the associates roster
      (paged envelope + `q` search, backward compatible). Remaining lists tracked
      in P3.

## P1.5 — Résumé Parsing & Matching (Current Epic)

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
- [ ] Server-side pagination for the remaining lists (clients/projects/allocations).
      Associates is already server-paged+searched; the others are small and use
      derived-field filters, so deferred until volume warrants it.
- [ ] Extract the profile's allocation section (history table + End/Assign modals,
      ~250 lines) into a `ProfileAllocations` component — `Profile.jsx` is ~900
      lines with five modals (flagged in the 2026-07-10 code review).
- [ ] Shared local-date helper: `todayStr()` (Profile) duplicates `today()`
      (Allocations), and both use `toISOString()` which is UTC — evening users west
      of UTC get tomorrow's date. Extract a `dates.js` local-date version.
- [ ] Import runs one transaction per file: a runtime exception thrown *through a
      repository proxy* inside the row loop (not the guard's ConflictException,
      which is handled) could still mark the batch rollback-only. Consider per-row
      savepoints if imports grow (flagged in the 2026-07-10 review).

## Resolved decisions

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

## Done (for reference)

SkillCloud Integration (structured skill taxonomy, associate profile pages with skills & certs tracking, faceted skill search, skill proficiency reports, cert-expiry dashboard radar, multi-sheet v2 imports, and smart demand matching) · Domain model + enforced rules (capacity guard, protective deletes, uniqueness) · 95 HTTP-level TDD tests · Excel/CSV import with dry-run preview + idempotency · xlsx/csv/pdf/docx export · dashboard (utilization, bench aging, roll-off radar, trend, charts) · demand matching (skills, open positions, one-click fill) · role-gated UI with server-side enforcement · audit trail with admin UI · dark/light themes · animated premium UI + login · docs for devs and sales · graphify knowledge graph integration · Vite build relocated out of `src/`.
