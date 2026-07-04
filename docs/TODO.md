# OmiVertex — Remaining Work

Prioritized backlog as of 2026-07-04. Everything above the line blocks calling
this "production"; everything below improves an already-usable pilot.

## P0 — Security & deployment blockers

- [ ] **Verify Google ID tokens server-side.** `/api/v1/auth/google` currently
      trusts the client-posted email/name. Verify a real Google ID token
      (audience + `@softility.com` hosted domain) before wider rollout.
- [ ] **TDD the access-request flow.** `AppUser` approve/reject and the Google
      sign-in path have zero test coverage — the only untested area of the app.
- [ ] **Deploy off the laptop.** `./mvnw package` produces a self-contained jar
      (SPA included); run it as a service or container on an internal server,
      behind HTTPS (reverse proxy), with the session cookie marked Secure.
- [ ] **PostgreSQL backups.** Scheduled `pg_dump` at minimum; the DB becomes the
      business record the day the team starts using it.
- [ ] **Change default passwords** (`omivertex.auth.admin-password` /
      `viewer-password`) — the defaults are printed in the README.
- [ ] **Adopt Flyway** before the next schema-touching feature; `ddl-auto=update`
      can't handle renames/drops safely.

## P1 — Pilot hardening

- [ ] **Per-person accounts everywhere.** Once Google verification lands, retire
      the shared `admin`/`viewer` logins so the audit trail names real people.
- [ ] **Optimistic locking** (`@Version` on Allocation/Associate) so two managers
      editing the same record don't silently overwrite each other.
- [ ] **Server-side pagination + search** on list endpoints (client-side paging
      covers to ~1k rows; move the slicing to the API before then).

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

## Done (for reference)

SkillCloud Integration (structured skill taxonomy, associate profile pages with skills & certs tracking, faceted skill search, skill proficiency reports, cert-expiry dashboard radar, multi-sheet v2 imports, and smart demand matching) · Domain model + enforced rules (capacity guard, protective deletes, uniqueness) · 92 HTTP-level TDD tests · Excel/CSV import with dry-run preview + idempotency · xlsx/csv/pdf/docx export · dashboard (utilization, bench aging, roll-off radar, trend, charts) · demand matching (skills, open positions, one-click fill) · role-gated UI with server-side enforcement · audit trail with admin UI · dark/light themes · animated premium UI + login · docs for devs and sales · graphify knowledge graph integration · Vite build relocated out of `src/`.
