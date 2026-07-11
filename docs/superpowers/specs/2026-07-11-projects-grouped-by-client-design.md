# Projects Page — Grouped by Client — Design

**Date:** 2026-07-11
**Status:** Approved by user (layout chosen visually: option A of A/B/C)
**Problem:** The Projects page is a flat `DataTable` of every project with only a
client dropdown — at production scale it reads as an unorganized dump of all
clients and projects.

## What & why

Frontend-only reorganization of `frontend/src/pages/Projects.jsx`:

1. **Collapsible client sections.** One card per client, alphabetical. Header:
   client name + summary ("N projects · M active") + chevron; click toggles
   collapse (all expanded by default). Clients with zero projects still render,
   with "No projects yet".
2. **Project rows** inside each section: name, code, status `Badge`,
   `start → end` dates, edit/delete icons (`canEdit` only). Sort: ACTIVE first,
   then name.
3. **Toolbar:** search box replaces the client dropdown — matches client name,
   project name, or code (case-insensitive); while searching, non-matching
   sections hide and matching ones auto-expand. Status filter dropdown
   (All / Active / On hold / Completed). "New Project" button unchanged.
4. **Unchanged:** create/edit modal, inline add-client (`SearchSelect`),
   API calls (`api.list('projects')`, `api.list('clients')` — client-side
   grouping; lists are deliberately client-paged per AGENTS.md), backend.

## Error handling

Same as today: modal shows field errors from the API; delete confirms via
`window.confirm` and toasts failures.

## Testing / verification

No frontend unit-test runner exists in this repo; verification is
`npm run format && npm run build` (Prettier + ESLint gate) plus a visual check
of grouping, search, collapse, and the edit/delete/create flows. Backend
untouched — `./mvnw test` must stay green (no changes expected).

## Out of scope

- Server-driven pagination/search for projects (tracked P3 in docs/TODO.md).
- Per-project headcount/allocation info (would need a backend change).
- Any change to the Clients page.
