# Allocations page — client → project drill-down

**Date:** 2026-07-12 · **Status:** Approved

## What & why

The Staffing page now presents allocations as a client → project → associates
drill-down, and the Projects page groups projects by client. The Allocations page is
still a flat `DataTable` with a project dropdown filter. Reorganize it to the same
drill-down so the three staffing-graph screens read consistently, while keeping the
page's distinct job: **full allocation history (ended included) and
create/edit/delete** — Staffing stays the read-only current-state view.

## Layout

**Toolbar**

- Text search input, placeholder "Search clients, projects, or associates…". Matches
  client name, project name, project code, and associate name (case-insensitive
  substring, like Projects).
- The existing state select: "Current only" (default) / "Including ended".
- "Assign Associate" primary button, hidden when `canEdit` is false. Unchanged.
- The project `SearchSelect` filter is **removed** — grouping plus search replaces it.

**Body**

- One collapsible card per client, same header pattern as Staffing/Projects: chevron,
  client name, billable / non-billable `Badge` counts computed from the rows currently
  visible under the active filters.
- Inside each card, one sub-section per project: a "Project Name · CODE" header line,
  then a nested table with columns
  **Associate | Billing | Allocation | Start | End | State | actions**.
  - Associate name links to `#/associates/:id` (as on Staffing).
  - Billing and State render as `Badge`s (Billable/Non-billable, Current/Ended).
  - Edit and Delete buttons per row, only when `canEdit`; they open the existing modal
    / confirm flow.
- Empty state and skeleton loading rows unchanged from today.

**Behavior**

- Default expansion: first client open, others collapsed (Staffing's default).
- A non-empty search auto-expands every matching section (Projects' behavior);
  clearing it restores manual toggle state.
- Sorting: clients alphabetically; projects alphabetically within a client; rows
  current-first, then associate name.

## Data flow

No backend change. `AllocationResponse` already carries `id`, `associateId`,
`associateName`, `projectId`, `projectName`, `clientName`, `billable`,
`allocationPercent`, `startDate`, `endDate`, `active`.

- Load `GET /allocations?active=` as today; drop the `projectId` query param (search
  is client-side now).
- The page already loads `GET /projects` for the assign form. Join each allocation's
  `projectId` to that list to get `clientId` (stable grouping key) and project `code`.
  Allocations whose project isn't in the list yet (race between loads) group by
  `clientName` as a fallback.
- Group client → project in plain page code, same style as `Projects.jsx`'s
  `sections` computation. No new components; reuse `Badge`, `Icon`, `Modal`, and the
  existing `staffing-toggle` / `client-section` CSS. Colors via CSS tokens only.

## CRUD

The modal, `AllocationForm`, save/remove logic, toasts, and field-error handling are
untouched — only the read view changes.

## Testing & verification

- Backend untouched → no new Java tests; `./mvnw test` must stay green.
- Frontend gate: `cd frontend && npm run format && npm run build` (Prettier + ESLint).
- Manual verification: expand/collapse, search auto-expand across all three name
  kinds, state filter, edit/delete from nested rows, viewer role hides all mutation
  controls, associate links navigate.

## Docs

- `docs/TODO.md` → "Resolved decisions": project dropdown filter removed in favor of
  grouped drill-down + search.
- `docs/TECHNICAL.md`: no API contract change; update only if it describes the
  Allocations UI.

## Out of scope

- `?clientId=` deep-link support (Staffing has it; add to Allocations only if a
  screen needs to link here).
- Server-side pagination/grouping for allocations — stays client-paged per the
  documented deliberate exception in `AGENTS.md`.
