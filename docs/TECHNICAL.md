# OmiVertex — Technical Documentation

*Audience: developers building, maintaining, or extending the system.*
*Last updated: 2026-07-03*

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
│     ├─ components/              Icon, Modal, Badge, DataTable, Field,
│     │                           DataTransfer (import/export), charts.jsx
│     └─ pages/                   Login, Dashboard, Associates, Clients,
│                                 Projects, Allocations, Settings
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
| **Client** | name, industry, location, status (ACTIVE/INACTIVE) | `name` unique (case-insensitive check in service) |
| **Project** | code, name, client FK, status (ACTIVE/ON_HOLD/COMPLETED), startDate, endDate | `code` unique |
| **Associate** | name, email, company, location, workMode (ONSHORE/OFFSHORE), designation, status | `email` unique |
| **Allocation** | associate FK, project FK, billable (bool), allocationPercent (1–100), startDate, endDate (null = open) | see business rules |
| **AppUser** | email, name, role (VIEWER/ADMIN), status (PENDING/APPROVED/REJECTED) | `email` unique; backs the company-email sign-in flow |
| **SkillCategory** | name | `name` unique |
| **Skill** | name, category FK | `(name, category_id)` unique |
| **AssociateSkill** | associate FK, skill FK, proficiency (NOVICE, FOUNDATIONAL, INTERMEDIATE, FUNCTIONAL_USER, ADVANCE, MASTERY) | `(associate_id, skill_id)` unique |
| **Certification** | associate FK, name, authority, credentialId, issuedDate, expiryDate | — |
| **OpenPosition** | title, project FK, requiredSkillRef FK, minProficiency, billable, allocationPercent, startDate, status (OPEN/FILLED/CANCELLED) | — |

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
   Overlap: `!(a.end < new.start) && !(new.end < a.start)` (null end = ∞).
4. **Protective deletes** — client with projects, project with allocations,
   associate with allocations → 409. Delete order: allocations → projects/associates → clients.
5. **Bench** — associate with no current allocation. `benchDays` = days since the
   latest past `endDate`, or since `createdAt` if never allocated.
6. **Skill validation** — on associate create/update, any provided primary or secondary skill must match a recognized skill name in the taxonomy (case-insensitive) → 400.

## 6. REST API

Base path `/api/v1`. JSON. Session cookie required (see §7).

| Resource | Endpoints | Filters |
|---|---|---|
| `/clients` | GET, POST, GET/{id}, PUT/{id}, DELETE/{id} | — |
| `/projects` | same | `?clientId=` |
| `/associates` | same | `?workMode=&billable=&bench=&categoryId=&skillId=&minProficiency=` |
| `/allocations` | same (PUT uses `AllocationUpdateRequest` — no re-parenting) | `?projectId=&associateId=&active=` |
| `/positions` | GET, POST, GET/{id}, PUT/{id}, DELETE/{id} | `?status=&projectId=` |
| `/positions/{id}/matches` | GET (returns scored candidates; ADMIN) | — |
| `/positions/{id}/fill` | POST (fills position by creating allocation; ADMIN) | — |
| `/taxonomy` | GET (nested alphabetical tree) | — |
| `/taxonomy/categories` | POST, DELETE/{id} (ADMIN) | — |
| `/taxonomy/skills` | POST, DELETE/{id} (ADMIN) | — |
| `/associates/{id}/skills` | PUT (idempotent rated-skills replace; ADMIN) | — |
| `/associates/{id}/certifications` | GET, POST (ADMIN) | — |
| `/certifications` | GET (org-wide, alphabetical soonest expiry first) | `?q=` (search by name, authority, associate name) |
| `/certifications/{id}` | DELETE (ADMIN) | — |
| `/reports/skills` | GET (proficiency distribution tree) | — |
| `/dashboard/summary` | GET | — |
| `/data/import` | POST multipart `file` (.xlsx/.csv) | `?ignoreNovice=` |
| `/data/export` | GET | `?format=xlsx|csv|pdf|docx` |
| `/auth` | POST `/login`, POST `/google`, POST `/logout`, GET `/me` | — |
| `/admin/access-requests` | GET, POST `/{id}/approve`, POST `/{id}/reject` (ADMIN) | — |

**Error contract** (from `GlobalExceptionHandler`):

```json
{ "message": "…", "fieldErrors": { "field": "reason" } }
```

- 400 validation (`fieldErrors` populated) or bad input file
- 401 not signed in / bad credentials
- 403 viewer attempting a write ("Your account is read-only")
- 404 unknown id
- 409 uniqueness, capacity, duplicate-allocation, protective-delete conflicts

**`/dashboard/summary` response shape** (all computed live):

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
```

## 7. Security

Two sign-in paths coexist:

1. **Built-in accounts** (in-memory, `SecurityConfig`): `admin` → ROLE_ADMIN,
   `viewer` → ROLE_VIEWER. Passwords default to `Admin@123` / `Viewer@123`;
   override with `omivertex.auth.admin-password` / `omivertex.auth.viewer-password`.
2. **Company-email sign-in with approval workflow** (`POST /api/v1/auth/google`,
   `AppUser` entity): only `@softility.com` addresses are accepted. First sign-in
   creates an `AppUser` with status PENDING and role VIEWER; the user is refused
   with "pending approval" until a Super Admin approves them under
   **Access Requests** (`/api/v1/admin/access-requests` — list, `/{id}/approve`,
   `/{id}/reject`; ADMIN-only, admin-only sidebar entry in the SPA). Approved
   users authenticate by email; REJECTED users are refused.
   *Note: this endpoint currently trusts the client-supplied email/name — wire it
   to real Google ID-token verification before exposing beyond the intranet.*

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
   Reads the active sheet. Header names matched case-insensitively: `ASSOCIATE NAME, COMPANY, LOCATION, CUSTOMER, BILLABLE, PROJECT` (aliases: NAME/CLIENT/WORK MODE/SHORE/BILLING).
   - Email is generated as `first.last@softility.com` (idempotency key).
   - Client and project are found-or-created.
   - Allocation created at 100% starting today.

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
  component from a `ROUTES` table. Deep links work.
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
./mvnw test                                             # 94 tests
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
  both a happy-path and a conflict test. Current suite: **94 tests**.

## 12. Known limitations / next steps

- Company-email sign-in does not yet verify a Google ID token — it trusts the
  posted email/name. Add real OAuth/OIDC verification before wider rollout.
- Built-in accounts remain in-memory; access-request flow has no test coverage yet.
- No pagination — list endpoints return everything (fine ≤ ~1k rows).
- No audit trail or optimistic locking (`@Version`).
- Hibernate-managed schema; adopt Flyway before schema-breaking changes.
- Import treats generated email as identity; a real email column in the sheet
  should take precedence if added.
