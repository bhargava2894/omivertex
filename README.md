# OmiVertex

Premium internal resource management system for **Softility** — track associates,
clients, projects, and billable allocations in one place.

![Stack](https://img.shields.io/badge/Spring%20Boot-3.5-green) ![React](https://img.shields.io/badge/React-18-blue) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-informational)

## Stack

- **Backend:** Java 21, Spring Boot 3.5 (Web MVC, Data JPA, Validation, Thymeleaf)
- **Database:** PostgreSQL (`omivertex`); H2 (PostgreSQL mode) for tests
- **Frontend:** React 18 + Vite, served as a SPA through a Thymeleaf shell template

## Run

```bash
# 1. Database (once)
createdb omivertex        # or: psql -c "CREATE DATABASE omivertex"

# 2. Frontend build (once, and after UI changes)
cd frontend && npm install && npm run build && cd ..

# 3. App
./mvnw spring-boot:run
```

Open <http://localhost:8080>. On first boot the app seeds realistic demo data
(6 clients, 10 projects, 24 associates, 21 allocations); set `omivertex.seed=false`
in `src/main/resources/application.properties` to disable.

### Frontend dev loop

```bash
cd frontend && npm run dev   # Vite dev server on :5173, /api proxied to :8080
```

## Sign-in

Two internal accounts (session-based, Spring Security):

| Account | Password | Access |
|---|---|---|
| `admin` | `Admin@123` | Super Admin — full view + edit |
| `viewer` | `Viewer@123` | User — read-only (exports allowed) |

Override passwords with `omivertex.auth.admin-password` /
`omivertex.auth.viewer-password` properties. Writes (`POST/PUT/DELETE
/api/v1/**`) require the ADMIN role; reads need any signed-in account.

## Tests

```bash
./mvnw test
```

45 tests — full HTTP-level coverage (MockMvc + H2) of every endpoint: happy paths,
validation (400), missing resources (404), conflict rules (409), dashboard KPIs with
staffing trend, and CSV/JSON import-export.

## API

Base path `/api/v1` — full CRUD on `clients`, `projects` (`?clientId=`),
`associates` (`?workMode=&billable=&bench=`), `allocations`
(`?projectId=&associateId=&active=`), plus `GET /dashboard/summary` for KPIs.

Domain rules enforced by the API:

- Client names, project codes, and associate emails are unique (409 on duplicates).
- An associate cannot hold two open allocations on the same project.
- Clients with projects, projects with allocations, and associates with allocations
  cannot be deleted (protective 409); roll off / reassign first.
- An associate's **customer, project, and billable status are derived** from their
  current allocations; an associate with no current allocation is on the **bench**.

## Documentation

- **Technical reference (developers):** [docs/TECHNICAL.md](docs/TECHNICAL.md) —
  architecture, data model, API contract, security, business rules, build & test.
- **Functional overview (sales / business):** [docs/FUNCTIONAL_OVERVIEW.md](docs/FUNCTIONAL_OVERVIEW.md) —
  what OmiVertex does, who it's for, feature tour, value story, FAQ, roadmap.
- Original design: `docs/superpowers/specs/2026-07-02-omivertex-design.md`
- Implementation plan: `docs/superpowers/plans/2026-07-02-omivertex-implementation-plan.md`
