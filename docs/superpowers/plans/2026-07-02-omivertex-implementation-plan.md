# OmiVertex — Implementation Plan

Spec: `docs/superpowers/specs/2026-07-02-omivertex-design.md`

## Phase 1 — Backend foundation
1. Configure `application.properties` (PostgreSQL `omivertex`, JPA update) and
   `application-test.properties` / test config (H2 PostgreSQL mode).
2. Entities + enums: `Client`, `Project`, `Associate`, `Allocation`;
   `EntityStatus`, `ProjectStatus`, `WorkMode`.
3. Repositories with derived/custom queries (current allocations, counts for dashboard).

## Phase 2 — API, test-driven (test first per resource, then implement to green)
4. Shared web layer: DTOs, `GlobalExceptionHandler` (400/404/409), `NotFoundException`,
   `ConflictException`.
5. `ClientController` + service — CRUD tests: create, validation 400, duplicate-name 409,
   list, get, 404, update, delete.
6. `ProjectController` + service — CRUD + `?clientId=` filter + unknown client 404 +
   duplicate code 409.
7. `AssociateController` + service — CRUD + filters + derived current
   project/client/billable in response.
8. `AllocationController` + service — assign, double-open-allocation 409, roll-off via
   PUT endDate, filters.
9. `DashboardController` — `/dashboard/summary` aggregates.
10. Seed data loader (dev profile): ~6 clients, ~10 projects, ~24 associates, allocations
    with a realistic bench.

## Phase 3 — Frontend
11. Vite React scaffold in `frontend/`; build outputs to Spring `static/`;
    Thymeleaf `templates/index.html` + `HomeController` serving `/`.
12. Design tokens + shell (Sidebar, Topbar) per UI/UX Pro Max direction.
13. Shared components: DataTable, Modal, StatCard, Badge, Toast, EmptyState.
14. Pages: Dashboard, Associates, Clients, Projects, Allocations (CRUD wired to API).

## Phase 4 — Verification
15. `./mvnw test` green; run app against PostgreSQL; curl smoke of every endpoint;
    build frontend and verify UI serves and CRUD round-trips.

Definition of done: all tests pass, app boots on :8080 against PostgreSQL with seed
data, dashboard shows live numbers, every entity has working list/create/edit/delete
in the UI.
