# OmiVertex — Design Document

**Date:** 2026-07-02
**Product:** Premium internal resource management system for Softility (IT staffing company).
**Stack:** Spring Boot 3.5 (Java 21) · PostgreSQL 15 · React 18 (Vite) served through a Thymeleaf shell template.

## 1. Purpose

Softility needs one place to track its associates (consultants), the clients they serve,
the projects they are staffed on, and whether that staffing is billable. The primary
users are resource managers who answer questions like: *Who is on the bench? What is our
billable ratio? What is the onshore/offshore split for client X?*

## 2. Data model

The request lists Customer / Project / Billable as Associate fields. In a staffing
domain these are properties of an **allocation**, not of the person — an associate can
roll off one project and onto another without their identity changing, and history
matters. So the model normalizes them:

```
Client 1 ──── * Project 1 ──── * Allocation * ──── 1 Associate
```

### Entities

**Client** (master client list)
- `id` (bigint, PK), `name` (unique, required), `industry`, `location`,
  `status` (ACTIVE | INACTIVE), `createdAt`

**Project** (master project list)
- `id`, `code` (unique, e.g. "SFT-1024", required), `name` (required),
  `client` (FK → Client, required), `status` (ACTIVE | ON_HOLD | COMPLETED),
  `startDate`, `endDate`, `createdAt`

**Associate**
- `id`, `name` (required), `email` (unique, required), `company` (employing entity,
  e.g. Softility or a sub-vendor), `location`, `workMode` (ONSHORE | OFFSHORE),
  `designation`, `status` (ACTIVE | INACTIVE), `createdAt`

**Allocation** (assign associates to projects)
- `id`, `associate` (FK, required), `project` (FK, required),
  `billable` (boolean, required), `allocationPercent` (1–100, default 100),
  `startDate` (required), `endDate` (nullable = open-ended), `createdAt`
- Rule: an associate may not have two **open** allocations to the same project.
- An allocation is *current* when `startDate <= today` and (`endDate` is null or `>= today`).

### Derived fields (returned on Associate API, never stored)
- `currentProject`, `currentClient` (customer), `billable` — from the associate's
  current allocations. An associate with no current allocation is **on the bench**.

Schema is created by JPA (`ddl-auto=update`) against PostgreSQL database `omivertex`;
tests run on H2 in PostgreSQL compatibility mode.

## 3. API design

Base path `/api/v1`, JSON, standard CRUD per resource:

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/clients` | list / create |
| GET/PUT/DELETE | `/clients/{id}` | fetch / update / delete |
| GET/POST | `/projects` (`?clientId=`) | list (filterable) / create |
| GET/PUT/DELETE | `/projects/{id}` | fetch / update / delete |
| GET/POST | `/associates` (`?workMode=&billable=&bench=`) | list (filterable) / create |
| GET/PUT/DELETE | `/associates/{id}` | fetch / update / delete |
| GET/POST | `/allocations` (`?projectId=&associateId=&active=`) | list / create (assign) |
| GET/PUT/DELETE | `/allocations/{id}` | fetch / update (e.g. end-date to roll off) / delete |
| GET | `/dashboard/summary` | KPI aggregate for the dashboard |

`/dashboard/summary` returns: total/active associates, billable vs non-billable vs bench
counts, onshore/offshore counts, active clients, active projects, and per-client
headcount (top clients).

Conventions:
- Requests use flat DTOs (`clientId`, `associateId` — never nested entities in).
- Responses use DTOs with resolved names (`clientName`, `associateName`) so the UI
  never needs join lookups.
- Errors: 400 with `{message, fieldErrors{}}` for validation, 404 `{message}` for
  missing ids, 409 `{message}` for uniqueness/conflict violations — all from one
  `@RestControllerAdvice`.

## 4. Frontend structure

React SPA (Vite) built into `src/main/resources/static/assets`; Spring serves it via a
single Thymeleaf template `index.html` (satisfies the React + Thymeleaf requirement and
keeps one deployable jar). Client-side routing with a tiny hash router (no
react-router dependency needed).

```
frontend/src/
  main.jsx            — bootstrap
  App.jsx             — shell: Sidebar + Topbar + routed page
  api.js              — fetch wrapper for /api/v1
  components/         — StatCard, DataTable, Modal, Badge, EmptyState, Toast
  pages/
    Dashboard.jsx     — KPI tiles, billability & shore-mix bars, top clients
    Associates.jsx    — table + create/edit modal + filters
    Clients.jsx       — table + create/edit modal
    Projects.jsx      — table + create/edit modal (client dropdown)
    Allocations.jsx   — table + assign modal (associate/project dropdowns)
```

Design language (enterprise, premium — via UI/UX Pro Max): dark sidebar + light
content, deep indigo/slate palette, Inter font, generous whitespace, subtle shadows,
status badges, no gimmicks. Full design tokens in CSS custom properties.

## 5. Testing strategy (TDD)

- **Backend first, test first**: `@DataJpaTest` for repository queries,
  `@WebMvcTest`-style full `@SpringBootTest + MockMvc` integration tests per
  controller covering happy path, validation failure, 404, and conflict cases.
  H2 (PostgreSQL mode) so tests need no running database.
- Frontend verified end-to-end against the running app (build + manual API smoke).

## 6. Approaches considered

1. **Store customer/project/billable directly on Associate** — matches the request
   literally, but denormalized: no history, updates in two places, bench = magic null.
   Rejected.
2. **Normalized Allocation model (chosen)** — one extra table, clean history,
   dashboard queries fall out naturally; derived fields keep the requested view.
3. **Separate SPA deployment (Node server / nginx)** — more moving parts than an
   internal tool needs. Rejected in favor of single Spring Boot jar serving the
   built React bundle through Thymeleaf.
