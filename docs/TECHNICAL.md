# OmiVertex — Technical Documentation

*Audience: developers building, maintaining, or extending the system.*
*Last updated: 2026-07-16*

---

## 1. System overview

OmiVertex is Softility's internal resource-management system: it tracks associates
(consultants), the clients and projects they serve, and their allocations —
including billability, capacity, bench status, and roll-off dates — behind a
role-gated web UI.

It ships as a **single Spring Boot jar**: the React SPA is built into
`src/main/resources/static/` and served through one Thymeleaf shell template. There
is no separate frontend deployment.

```
Browser (React SPA, hash-routed)
   │  JSON over /api/v1/**  (session cookie auth)
   ▼
Spring Boot 3.5 (Java 21)
   ├─ Web: REST controllers + Thymeleaf shell (/)
   ├─ Service layer: domain rules (capacity, uniqueness, protective deletes)
   ├─ Spring Data JPA repositories
   └─ Spring Security (in-memory users, session-based)
   ▼
PostgreSQL 15 (database: omivertex)      H2 in PostgreSQL mode for tests
```

## 2. Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5.6 |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL 15 (`ddl-auto=update`) |
| Security | spring-boot-starter-security, session-based, in-memory users |
| Documents | Apache POI 5.2.5 (xlsx/docx), OpenPDF 1.3.30 (pdf) |
| Frontend | React 18, Vite 5, framer-motion 12 (only runtime deps: react, react-dom, framer-motion) |
| Charts | Hand-rolled SVG components (no chart library) |
| Tests | JUnit 5, MockMvc, spring-security-test, H2 (PostgreSQL compatibility mode) |

## 3. Repository layout

```
omivertex/
├─ pom.xml
├─ src/main/java/com/softility/omivertex/
│  ├─ OmivertexApplication.java
│  ├─ config/          SecurityConfig, SeedDataLoader
│  ├─ domain/          Client, Project, Associate, Allocation, OpenPosition + enums
│  ├─ repository/      Spring Data interfaces (one per aggregate, e.g. PositionRepository)
│  ├─ service/         ClientService, ProjectService, AssociateService,
│  │                   AllocationService, DashboardService, PositionService,
│  │                   ImportService, ExportService
│  └─ web/             REST controllers, AuthController, HomeController, PositionController
│     ├─ dto/          request/response records (never entities on the wire)
│     └─ error/        GlobalExceptionHandler + typed exceptions
├─ src/main/resources/
│  ├─ application.properties
│  └─ templates/index.html        ← Thymeleaf shell hosting the SPA
│     (Vite output lives in frontend/dist/, copied to target/classes/static by Maven)
├─ src/test/java/…/api/           HTTP-level tests (one class per resource)
├─ frontend/                      Vite React app
│  ├─ public/logo.png, logo-mark.png
│  └─ src/
│     ├─ App.jsx                  shell: auth gate, sidebar, topbar, routing
│     ├─ api.js                   fetch wrapper (401 → global logout event)
│     ├─ theme.js                 light/dark/system persistence
│     ├─ motion.js                shared animation tokens/variants + reduced-motion hook
│     ├─ components/              Icon, Modal, Badge, DataTable, Field, CollapsibleCard,
│     │                           DataTransfer (import/export), charts.jsx
│     └─ pages/                   Login, Dashboard, Associates, Clients, Projects,
│                                 Staffing (merged "Staffing & Allocations"), Positions
│                                 (Demand), SkillReports, Taxonomy, AccessRequests,
│                                 AuditLog, Profile, MyProfile, ProfileChanges, Settings
└─ docs/                          this file, functional overview, specs, plans
```

## 4. Data model

```
Client 1 ──── * Project 1 ──── * Allocation * ──── 1 Associate
               │
               └──── * OpenPosition
```

| Entity | Key fields | Constraints |
|---|---|---|
| **Client** | name, clientId, industry, location, status (ACTIVE/INACTIVE) | `name` unique (case-insensitive check in service); `clientId` unique |
| **Project** | code, name, client FK, status (ACTIVE/ON_HOLD/COMPLETED), startDate, endDate | `code` unique |
| **Associate** | name, email, employeeId, company, location, workMode (ONSHORE/OFFSHORE), designation, joinedDate, resignationDate, lastWorkingDay, exitReason, status | `email` unique; `employeeId` unique |
| **EmploymentHistory** | associate FK, company, title, startDate, endDate, sortOrder | résumé-extracted previous (external) employers only, applied create-only, never merged with allocation-derived internal history |
| **Allocation** | associate FK, project FK, billable (bool), allocationPercent (1–100), startDate, endDate (null = open) | see business rules |
| **AppUser** | email, name, role (VIEWER/ADMIN/ASSOCIATE), status (PENDING/APPROVED/REJECTED), associateId FK | `email` unique; backs the company-email sign-in flow |
| **SkillCategory** | name | `name` unique |
| **Skill** | name, category FK | `(name, category_id)` unique |
| **AssociateSkill** | associate FK, skill FK, proficiency (NOVICE, FOUNDATIONAL, INTERMEDIATE, FUNCTIONAL_USER, ADVANCE, MASTERY) | `(associate_id, skill_id)` unique |
| **Certification** | associate FK, name, authority, credentialId, issuedDate, expiryDate | — |
| **OpenPosition** | title, project FK, billable, allocationPercent, startDate, endDate, workMode, status (OPEN/FILLED/CANCELLED), requiredSkill, jobDescription | endDate ≥ startDate → else 400 |
| **PositionSkill** | position FK, skill FK, minProficiency, required (bool) | `(position_id, skill_id)` unique |
| **ProfileChangeRequest** | associate FK, type (SKILLS/RESUME), status (PENDING/APPROVED/REJECTED), skillsPayload (text), resumeFilename, resumeContentType, resumeByteSize, resumeContent (blob), note, decidedBy, decidedAt, createdAt | — |
| **Resume** | associateId, filename, contentType, byteSize, content (blob, lazy-loaded), uploadedAt | `associate_id` unique — one résumé per associate, replace-on-upload |

**Derived, never stored:** an associate's `currentProject`, `currentClient`,
`billable`, and `benchDays` are computed from allocations at read time
(`AssociateResponse.from`). An allocation is *current* when
`startDate <= today && (endDate == null || endDate >= today)`.

Schema is Hibernate-managed (`ddl-auto=update`). For production-grade migrations
introduce Flyway before making breaking changes.

## 5. Business rules (enforced in services, all covered by tests)

1. **Uniqueness** — client name, project code, associate email → 409.
2. **No duplicate open allocation** — same associate + project with `endDate IS NULL` → 409.
3. **Capacity guard** — an associate is 100% capacity. On allocation create/update,
   the sum of `allocationPercent` across *date-overlapping* allocations
   (excluding self on update) must not exceed 100 → 409 with the computed total.
   Overlap: `!(a.end < new.start) && !(new.end < a.start)` (null end = ∞). Note the
   end date itself still counts as allocated — capacity frees the day *after*.
   **Import enforces the same guard** (2026-07-10): an over-capacity roster row is
   reported as a row error (the associate still imports; the allocation is skipped).
   One implementation: `AllocationService.assertCapacity` (package-private, annotated
   `noRollbackFor=ConflictException` so a bad row can't doom the import batch).
4. **Protective deletes** — client with projects, project with allocations,
   associate with allocations → 409. Delete order: allocations → projects/associates → clients.
5. **Bench** — associate with no current allocation. `benchDays` = days since the
   latest past `endDate`; if never allocated, since `joinedDate`, falling back to
   `createdAt` (2026-07-10: `joinedDate` added so roster imports don't reset the
   bench clock to the import day).
6. **Skill validation** — on associate create/update, any provided primary or secondary skill must match a recognized skill name in the taxonomy (case-insensitive) → 400.
7. **Exit auto-cleanup** (2026-07-10) — `exitReason` and `lastWorkingDay` must be set
   together (→ 400); `resignationDate ≤ lastWorkingDay` (→ 400). Once the last working
   day has passed, `AssociateService.processExits()` (nightly scheduler + inline when a
   past date is recorded) flips status to INACTIVE, ends open/later-ending allocations
   at the last working day, deletes never-started future allocations, and writes an
   `EXITED` audit entry. Idempotent.
8. **Position matching** (2026-07-10) — candidates need free capacity ≥ the position's
   percent. Full match = all must-have `PositionSkill`s at min proficiency + work-mode
   fit; ranked above partial matches, which carry `missingRequirements` labels. Order:
   full first, then must-haves met, nice-to-haves met, bench days desc. Positions with
   no structured skills fall back to legacy free-text headline matching.
9. **Self-service approval** (2026-07-10) — associate-proposed skill/resume changes stay
   PENDING (live data untouched) until an admin approves (applied through the existing
   services, so validation + audit fire) or rejects with a note. One pending change per
   (associate, type) → 409. Approving as ASSOCIATE requires a roster email match → 400.
10. **AI assistant** (2026-07-10; context narrowed 2026-07-11, enumeration tools added
    2026-07-15, dashboard-grounded tools added 2026-07-16) — `AssistantService` sends a
    standing context of **aggregate counts only**
    (`AssistantContextBuilder.build()`: active/bench/open-position/client/project counts —
    no names, emails, or roster rows) and fetches specifics per question through
    server-side read tools, each capped at `MAX_TOOL_ROWS` (25) with an explicit overflow
    line. `list_clients` and `list_projects` let it *enumerate* clients and projects it has
    never been told the names of — without them it knew how many clients existed but could
    only name the ones that happened to have an open position. Resume file contents are
    never included — user decision, see docs/TODO.md. It calls the vendor-neutral
    `GeminiClient` boundary; the HTTP
    implementation (`GeminiHttpClient`) is config-gated by
    `omivertex.assistant.gemini.api-key` / `.model` (default `gemini-3.1-flash-lite` —
    chosen for free-tier quota headroom, see `docs/TODO.md`) and
    fails closed with 400 "not configured" when the key is unset. Message ≤ 2,000
    chars → else 400; history capped at the last 20 turns; upstream failures → 400
    with a readable message. Tests mock `GeminiClient` — the suite never calls Google.
    Four tools reuse dashboard math instead of recomputing it: `get_skill_gaps`
    (`SkillGapService.dashboardPanel()`), `list_expiring_certifications`
    (`DashboardService.expiringCerts(withinDays)`, default 90, shared with the
    dashboard radar), and `get_workforce_summary` / `list_bench_aging`
    (`DashboardService.summary()`). Every turn writes one interaction-log line
    (`AssistantInteractionLog`, log-file only — no DB, and never the reply
    text):
    `MIRAI user=… outcome=ANSWERED|DRAFTED|ERROR tools=[…] latencyMs=… question="…"`
    (question backslash/quote-escaped, newlines flattened). The username is
    resolved on the servlet thread (`AuditService.currentUsername()`) because
    the `ai-*` pool never sees the `SecurityContext`. The chat conversation
    persists per browser tab (`sessionStorage`, last 40 messages, wiped on
    logout and tab close — never `localStorage`, never the server, matching
    the log-not-DB posture). Write drafts now cover end/edit
    allocation and opening a position (one taxonomy-resolved required skill); tools
    are role-registered via `AssistantService.Caller` — a viewer's model never sees
    admin tools, and dispatch refuses them anyway.

## 6. REST API

Base path `/api/v1`. JSON. Session cookie required (see §7).

| Resource | Endpoints | Filters |
|---|---|---|
| `/clients` | GET, POST, GET/{id}, PUT/{id}, DELETE/{id} (`ClientRequest`/`ClientResponse` carry `clientId`) | — |
| `/projects` | same | `?clientId=` |
| `/associates` | same (`AssociateRequest`/`AssociateResponse` carry `employeeId`, `phone`, and `employmentHistory` — previous external employers; `employmentHistory` is applied on create only, ignored on update, and only the detail `GET /associates/{id}` response returns it — the roster list omits it) | `?workMode=&billable=&bench=&categoryId=&skillId=&minProficiency=&q=` (name/email/company search) `&page=&size=` (25 default; omit `page` for a plain array, backward compatible) |
| `/allocations` | same (PUT uses `AllocationUpdateRequest` — no re-parenting) | `?projectId=&associateId=&active=` |
| `/positions` | GET, POST, GET/{id}, PUT/{id}, DELETE/{id} | `?status=&projectId=` |
| `/positions/{id}/matches` | GET (candidates ranked full-match first, partials labeled with what's missing; ADMIN) | — |
| `/positions/{id}/fill` | POST (fills position by creating an allocation over the position's start–end window, so capacity is consumed for that period and the end date feeds the roll-off radar; start defaults to today when the position has none; ADMIN) | — |
| `/staffing` | GET (client → project → associates tree; per-level billable/non-billable counts always reflect *current* allocations only, "billable wins" per client; the page merges the former Allocations screen — ADMIN gets inline Assign/Edit/Remove reusing `/allocations` CRUD, VIEWER gets read-only; ADMIN+VIEWER) | `?includeEnded=` (also return non-current rows, marked `active:false`, count-neutral) |
| `/taxonomy` | GET (nested alphabetical tree) | — |
| `/taxonomy/categories` | POST, DELETE/{id} (ADMIN) | — |
| `/taxonomy/skills` | POST, DELETE/{id} (ADMIN) | — |
| `/associates/{id}/skills` | PUT (idempotent rated-skills replace; ADMIN) | — |
| `/associates/{id}/certifications` | GET, POST (ADMIN) | — |
| `/associates/{id}/resume` | GET (download), POST (upload/replace; ADMIN), DELETE (remove; ADMIN) | — |
| `/resumes/parse` | POST multipart `file` (stateless suggestions; ADMIN; AI extraction with per-skill `proficiency`+`evidence` and `experienceSummary`, plus extracted `name`/`phone`/`employmentHistory` for the New Associate overview, when Gemini is configured; keyword fallback otherwise, which leaves `name`/`phone`/`employmentHistory` empty; `source: AI\|KEYWORD`) | — |
| `/me/resumes/parse` | POST multipart `file` (same stateless parse for the self-service propose flow; ASSOCIATE) | — |
| `/certifications` | GET (org-wide, alphabetical soonest expiry first) | `?q=` (search by name, authority, associate name) |
| `/certifications/{id}` | DELETE (ADMIN) | — |
| `/reports/skills` | GET (proficiency distribution tree) | — |
| `/reports/skill-gaps` | GET (full supply-vs-demand report; one row per skill with open required demand **or** ≥1 rated associate; fields `skillId`, `skillName`, `category`, `demand` = open seats requiring the skill, `benchSupply`/`totalSupply` = ACTIVE associates at/above the lowest demanded min-proficiency (any proficiency when demand is 0), `gap` = demand − benchSupply, positive = shortage; sorted worst gap first, uncapped; the dashboard `skillGaps` panel is the same math via `SkillGapService`, capped at 20 demand-only rows) | — |
| `/reports/skill-gaps/{skillId}` | GET (the people and positions behind one gap row; 404 unknown skill; fields `threshold` = lowest demanded min-proficiency (NOVICE when no demand) plus four lists, all derived from it: `openDemand` = OPEN positions requiring the skill (`positionId`, `title`, `projectName`, `clientName`, `headcount`, `minProficiency`, `startDate`); `benchSupply` = ACTIVE holders at/above `threshold` with no current allocation (`proficiency`, `benchDays`); `rollingOff` = ACTIVE holders at/above `threshold` whose current allocation ends within `ROLLOFF_HORIZON_DAYS` (30) (`projectName`, `endDate`); `nearMiss` = ACTIVE associates rated exactly one level below `threshold` (`requiredProficiency`, `onBench`), always empty when `threshold` is NOVICE) | — |
| `/dashboard/summary` | GET | — |
| `/data/import` | POST multipart `file` (.xlsx/.csv) | `?ignoreNovice=` |
| `/data/export` | GET | `?format=xlsx|csv|pdf|docx` |
| `/auth` | POST `/login`, POST `/google`, POST `/logout`, GET `/me` | — |
| `/admin/access-requests` | GET, POST `/{id}/approve`, POST `/{id}/reject` (ADMIN) | — |
| `/assistant/chat` | POST (natural-language Q&A via Gemini; ADMIN+VIEWER; standing context is **aggregate counts only** — specifics are fetched per-question through server-side read tools (`search_associates`, `get_associate_detail`, `get_project_detail`, `list_rolloffs`, `list_open_positions`, `get_position_matches`, `list_clients`, `list_projects`, `get_skill_gaps`, `list_expiring_certifications`, `get_workforce_summary`, `list_bench_aging`, `get_position_match_summary`; admins additionally get `list_pending_approvals` + `get_audit_history` (declared and executable only for ADMIN); ≤25 rows each, ≤3 tool rounds + 1 wrap-up), so only the queried slice of personal data reaches Google; may return `proposedAction {type: CREATE_ALLOCATION\|FILL_POSITION\|END_ALLOCATION\|EDIT_ALLOCATION\|CREATE_POSITION, resolved ids/names, percent, billable, dates, allocationId?, skillId?, skillName?, minProficiency?, summary, warnings[]}` — the endpoint itself never mutates; the UI confirms via `POST /allocations`, `PUT /allocations/{id}`, `POST /positions`, or `POST /positions/{id}/fill` under the user's own session) | — |
| `/assistant/chat/stream` | POST (SSE variant of `/assistant/chat`: `tool` events as lookups run, then one `reply` event with the same JSON; failures send an `error` event; 60s emitter timeout; same roles) | — |
| `/me/profile` | GET (own profile; ASSOCIATE) | — |
| `/me/profile-changes` | GET (own change requests list; ASSOCIATE) | — |
| `/me/profile-changes/skills` | POST (submit proposed skills; ASSOCIATE) | — |
| `/me/profile-changes/resume` | POST multipart `file` (submit proposed resume; ASSOCIATE) | — |
| `/profile-changes` | GET (review queue; ADMIN+VIEWER) | `?status=` |
| `/profile-changes/{id}/approve` | POST (approve change request; ADMIN) | — |
| `/profile-changes/{id}/reject` | POST (reject change request with note; ADMIN) | — |

**Error contract** (from `GlobalExceptionHandler`):

```json
{ "message": "…", "fieldErrors": { "field": "reason" } }
```

- 400 validation (`fieldErrors` populated) or bad input file
- 401 not signed in / bad credentials
- 403 viewer attempting a write ("Your account is read-only")
- 404 unknown id
- 409 uniqueness, capacity, duplicate-allocation, protective-delete conflicts
- 503 AI executor saturated (`ServiceUnavailableException`) — retry shortly

**AI execution model:** the three AI endpoints (`/assistant/chat`,
`/resumes/parse`, `/me/resumes/parse`) run asynchronously on a dedicated
4-thread bulkhead (`AiExecutor`, queue 8) so Gemini latency never occupies
servlet threads; the Gemini HTTP client has 5s connect / 30s read timeouts
(`omivertex.assistant.gemini.connect-timeout` / `read-timeout`). Response
contracts are unchanged.

The Gemini endpoint base is configurable
(`omivertex.assistant.gemini.base-url`, default
`https://generativelanguage.googleapis.com`) so tests can stub the upstream;
`GeminiToolLoopCapTest` pins that one turn performs at most
`MAX_TOOL_ROUNDS + 2` upstream calls: when the round cap is hit on a read
tool, the client sends the model a budget-exhausted tool result and takes
exactly one wrap-up call, so the turn ends in prose from the data already
gathered instead of leaking the read tool as a `proposedAction`.
The streaming endpoint shares the same bulkhead, service body, and interaction
log — only the transport differs; the UI falls back to the plain endpoint on
transport failure. A live golden-question eval
(`MiraiGoldenQuestionsTest`) runs only with `MIRAI_GOLDEN_EVAL=true` plus a
real API key: `MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest`.

**`/dashboard/summary` response shape** (all computed live; every KPI counts
**ACTIVE associates only** — INACTIVE leavers and their lingering allocations are
excluded from headcounts, bench, and utilization; the historical `staffingTrend`
is the one series that keeps past allocations regardless of current status):

```
totalAssociates, billableCount, nonBillableCount, benchCount,
onshoreCount, offshoreCount, totalClients, activeProjects,
utilizationPercent            // FTE-weighted: Σ min(billable %,100) / associates
benchAging { days0to30, days31to60, days60plus }
benchAssociates [ { id, name, designation, benchDays } ]   // sorted desc
upcomingRolloffs [ { allocationId, associateId, associateName,
                     projectName, clientName, endDate, daysLeft } ]  // ≤30 days
clientHeadcounts [ { clientName, headcount } ]
staffingTrend    [ { month, total, billable } ]             // trailing 6 months
expiringCertifications [ { certificationId, associateId, associateName, name, expiryDate, daysLeft } ] // ≤90 days
exitsLast12Months             // leavers with lastWorkingDay in the trailing 365 days
skillGaps [ { skillId, skillName, category, demand, benchSupply, totalSupply, gap } ]
                              // per must-have skill on OPEN positions; supply counted at the
                              // lowest demanded proficiency; sorted by gap desc, ≤20 rows
utilizationForecast [ { label, percent, deltaPoints, drivers[], omittedDrivers } ]
                              // Today/+30d/+60d/+90d, deterministic from known allocation
                              // end dates + recorded exits. deltaPoints and drivers are
                              // measured against TODAY (not the previous horizon), so each
                              // row stands alone. drivers[] = { kind, associateId,
                              // associateName, projectName, date }, ≤5 rows then
                              // omittedDrivers. kind = ROLL_OFF | RAMP_UP | BENCH_EXIT |
                              // BILLABLE_EXIT. A BENCH_EXIT RAISES utilization (the leaver
                              // drops out of the denominator, billable FTE is unchanged) —
                              // that is why exits are split by kind. Drivers are named but
                              // never individually scored: utilization is a ratio, so
                              // per-driver effects do not sum to deltaPoints. An associate
                              // who exits in the window is reported once, as the exit — the
                              // allocations ending with them are not also roll-offs.
```

## 7. Security

Two sign-in paths coexist:

1. **Built-in accounts** (in-memory, `SecurityConfig`): `admin` → ROLE_ADMIN,
   `viewer` → ROLE_VIEWER. Passwords default to `Admin@123` / `Viewer@123`;
   override with `omivertex.auth.admin-password` / `omivertex.auth.viewer-password`.
2. **Company-email sign-in with approval workflow** (`POST /api/v1/auth/google`,
   `AppUser` entity): the posted Google ID token is verified server-side (signature +
   audience) by `GoogleApiTokenVerifier` behind the injectable `GoogleTokenVerifier`
   boundary, which fails closed when `omivertex.auth.google.client-id` is unset — the
   client-posted email is never trusted directly. Only `@softility.com` addresses are
   accepted. First sign-in creates an `AppUser` with status PENDING and role VIEWER;
   the user is refused with "pending approval" until a Super Admin approves them under
   **Access Requests** (`/api/v1/admin/access-requests` — list, `/{id}/approve`,
   `/{id}/reject`; ADMIN-only, admin-only sidebar entry in the SPA). Approved
   users authenticate by email; REJECTED users are refused. Associate-role users
   (`AppUser.role = ASSOCIATE`, linked to a roster record) sign in the same way but
   are confined to `/me/**`.

- **Authorization**: `/api/v1/auth/login` and `/api/v1/auth/google` are public;
  `/api/v1/admin/**` needs ADMIN; `GET /api/v1/**` needs any role; **all other
  methods on `/api/v1/**` need ADMIN**. Static assets and the shell are public
  (the SPA itself gates on `/auth/me`).
- Session-based (`HttpSessionSecurityContextRepository` saved explicitly in
  `AuthController.login`). CSRF is disabled (internal tool, JSON API). Entry
  point/denied handler return JSON 401/403 instead of redirects.
- Frontend: `api.js` dispatches `ov-unauthorized` on any 401 → `App` drops to the
  login page. `canEdit` (`role === 'ADMIN'`) is passed to every page to hide
  mutation UI — **the server remains the actual enforcement point**.

## 8. Import / export

**Import** (`ImportService`): accepts `.xlsx` (POI) or `.csv` files. It supports two modes:

1. **Legacy Single-Sheet / CSV Import**:
   Reads the active sheet. Header names matched case-insensitively: `ASSOCIATE NAME, COMPANY, LOCATION, CUSTOMER, BILLABLE, PROJECT, JOINED DATE` (aliases: NAME/CLIENT/WORK MODE/SHORE/BILLING/JOIN DATE/DATE OF JOINING/DOJ). `JOINED DATE` is optional and anchors the bench clock for never-allocated associates.
   - Email is generated as `first.last@softility.com` (idempotency key).
   - Client and project are found-or-created.
   - Allocation created at 100% starting today — subject to the capacity guard
     (business rule 3): an over-capacity row becomes a row error, the associate
     still imports, and the allocation is not created.

2. **Multi-Sheet Excel Workbook Import (v2)**:
   Triggered if the workbook contains a sheet named `Employees`. It reads three sheets:
   - **`Employees`**: Columns `Name, Email, Designation, Company, Location, Work Mode` (Shore).
   - **`EmployeeSkills`**: Columns `Email, Category, Skill, Proficiency` (mapped to `Proficiency` enum). Accepts query parameter `?ignoreNovice=true` to skip importing "Novice" rated skills.
   - **`Certifications`**: Columns `Email, Name, Authority, Credential ID, Issued Date, Expiry Date`.

Per-row failures are collected into `errors[]` and do not abort the batch. Returns `ImportSummaryResponse` (counts of records imported/skipped + error details).

**Export** (`ExportService`): renders the associate roster (with derived fields)
to xlsx (POI, styled header), csv, pdf (OpenPDF, landscape A4, zebra table), or
docx (POI XWPF). Returned as `attachment` with correct MIME type.

## 9. Frontend architecture

- **No router library**: `useHashRoute()` in `App.jsx` maps `#/path` → page
  component from a `ROUTES` table. Deep links work: `#/associates/<id>` opens a profile,
  and `#/skill-reports/<skillId>` opens Skill Reports with that skill's gap row already
  expanded (the dashboard's gap rows link here — the dashboard triages, Skill Reports
  explains). A deep-linked skill also forces the gap panel's triage filter to `all`,
  because the panel otherwise defaults to the `short` bucket and a `tight` skill would
  expand a row that is not in the visible list.
- **Auth gate**: `App` calls `/auth/me` on mount; `undefined` = checking (renders
  nothing), `null` = login page, object = shell. Logout and global 401s reset it.
- **Theming**: CSS custom properties on `:root` and `[data-theme='dark']`
  (`styles.css`). `theme.js` persists light/dark/system in
  `localStorage['ov-theme']`; both HTML shells run a pre-paint inline script to
  avoid theme flash. **Never hardcode colors in components — use the tokens.**
- **Charts** (`components/charts.jsx`): hand-rolled SVG. `TrendChart` (line,
  crosshair + all-series tooltip), `DonutChart`, `VBarChart`, `StackedBar`,
  `HBarChart`. Series colors come from `--chart-1..5`, validated against both
  surfaces (see `docs/superpowers/` dataviz notes). framer-motion drives entrance
  springs and hover states; every animation respects `prefers-reduced-motion`.
- **Role gating**: pages receive `canEdit`; mutation buttons and `DataTable`'s
  edit/delete column render only when true.
- **Toasts / errors**: field errors from the API map 1:1 onto form fields via
  `err.fieldErrors`; general failures surface in a form alert or toast.

## 10. Build, run, develop

```bash
# prerequisites: Java 21, Node 18+, PostgreSQL with database "omivertex"
cd frontend && npm install && npm run build && cd ..   # SPA → static/
./mvnw spring-boot:run                                  # http://localhost:8080
./mvnw test                                             # 230 tests
cd frontend && npm run dev                              # Vite on :5173, /api proxied
```

- `npm run build` outputs to `frontend/dist/` with stable asset names
  (`assets/app.js|css`). A `maven-resources-plugin` execution copies `dist/`
  into `target/classes/static/` at `process-resources` — excluding Vite's
  `index.html`, so the Thymeleaf shell stays canonical for `/`. The bundle never
  lives under `src/`, keeping git and the graphify knowledge graph clean.
- Seeding: `SeedDataLoader` runs when `omivertex.seed=true` **and the clients
  table is empty** (idempotent). Disable in properties for a clean start.
- Packaging: `./mvnw package` → single runnable jar (build the frontend first).

## 11. Testing conventions

- All tests are **HTTP-level** (`@SpringBootTest + MockMvc`) in
  `src/test/java/…/api/`, running on H2 in PostgreSQL mode — no database needed.
- `ApiTestBase` provides entity factory helpers, a clean-DB `@BeforeEach`
  (delete order matters), and class-level `@WithMockUser(roles="ADMIN")`.
  `AuthApiTest` exercises real logins with `MockHttpSession` instead.
- The project is built **strictly TDD**: write the failing test first, watch it
  fail for the right reason, implement to green. Every business rule in §5 has
  both a happy-path and a conflict test. Current suite: **230 tests**.

## 12. Known limitations / next steps

- Built-in `admin`/`viewer` accounts remain in-memory and shared; per-person
  accounts (tracked P1 in `docs/TODO.md`) would let the audit trail name real
  people instead of the shared role login.
- Associates is server-paged + searched (`?page=&q=`); clients/projects/allocations
  (now the merged Staffing view) still return everything client-side — fine at
  current volume, tracked P3 in `docs/TODO.md`.
- No optimistic locking (`@Version`) — two admins editing the same
  associate/allocation concurrently still silently overwrite each other (P1).
- Flyway (`V1`–`V9`) owns the prod schema (`ddl-auto=validate`); dev/tests keep
  Hibernate `ddl-auto=update`/`create-drop` for iteration speed.
- Import treats generated email as identity; a real email column in the sheet
  should take precedence if added.
