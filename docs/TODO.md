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

## Resolved decisions

- **Legacy `primarySkill`/`secondarySkill`** (2026-07): KEPT, deliberately demoted
  to informal free-text "headline" fields (roster quick-glance + CSV `SKILL`-column
  import + text-match fallback in `PositionService`). The structured `AssociateSkill`
  graph is authoritative for search, reports, and matching. Not removed because that
  would drop the fallback + import convenience (a functionality loss). Do not build
  new features on the free-text fields. See `Associate.java`.

## Done (for reference)

SkillCloud Integration (structured skill taxonomy, associate profile pages with skills & certs tracking, faceted skill search, skill proficiency reports, cert-expiry dashboard radar, multi-sheet v2 imports, and smart demand matching) · Domain model + enforced rules (capacity guard, protective deletes, uniqueness) · 95 HTTP-level TDD tests · Excel/CSV import with dry-run preview + idempotency · xlsx/csv/pdf/docx export · dashboard (utilization, bench aging, roll-off radar, trend, charts) · demand matching (skills, open positions, one-click fill) · role-gated UI with server-side enforcement · audit trail with admin UI · dark/light themes · animated premium UI + login · docs for devs and sales · graphify knowledge graph integration · Vite build relocated out of `src/`.
