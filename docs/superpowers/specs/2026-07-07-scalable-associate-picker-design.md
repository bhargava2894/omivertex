# Scalable Associate Picker (allocation form) — Design

**Date:** 2026-07-07
**Status:** Approved (brainstorming), pending implementation

## Problem

The "Assign associate to project" form (`Allocations.jsx`, create mode) loads
**every** associate via `api.list('associates')` and renders them in a native
`<select>`. At 500+ people that dropdown is unusable, and the payload is heavy
(each associate carries allocations, skills, and bench data). Same native
type-ahead limitation the project picker had.

## Goal

The associate field searches the server as you type and shows the top matches —
nothing loaded until you type, scales to any roster size, tiny payloads.

## Approach

Reuse the `SearchSelect` combobox (built for the project fix), extended with an
optional **async mode**. No backend change — the associates endpoint already
supports `q` search + paging (`GET /api/v1/associates?q=&size=`, matching name /
email / company).

### `SearchSelect` — add async mode

- New optional prop `onSearch(query) => Promise<[{ value, label }]>`.
- When `onSearch` is present: on each query change, **debounce ~250ms**, call
  `onSearch`, show a transient "Searching…" row, then render the returned
  options. When absent, behaves exactly as today (local filter over `options`) —
  the project picker is unaffected.
- Remember the **chosen option's label** internally so the input shows the
  selected person's name after picking (async results change per query, so the
  label can't be looked up from the current list).
- Guard against out-of-order responses: ignore a resolved search if the query
  has changed since it was issued.

### Allocation form wiring

- Associate field (create mode only) uses async mode:
  `onSearch = (q) => api.list('associates', { q, size: 20 }).then(r => (r.content||[]).map(a => ({ value: a.id, label: a.currentProject ? `${a.name} — on ${a.currentProject}` : `${a.name} — bench` })))`
- Empty query returns the first page (top 20) so opening the field still shows
  some people to pick from.
- Remove the eager `api.list('associates')` load from the page (no longer needed).
- ~20 results per search; if the roster has more, the user refines the query.

## Non-goals

- Not retrofitting other dropdowns; only the allocation associate picker.
- No multi-select, no new backend endpoint (existing paged/`q` endpoint suffices).
- Edit mode is unaffected (it doesn't show the associate/project pickers).

## Testing

Frontend-only; the project has no JS test suite, so verification is the
build/lint gate plus a manual live check: open Assign, type a partial name →
matches appear, pick one → name shows, save → allocation created. Backend is
untouched, so the 117 backend tests remain green.

## Risks

- Debounce + async introduces loading/racing; mitigated by the stale-response
  guard and a small debounce.
- If `onSearch` errors (network/401), the menu shows "No matches" and the error
  surfaces via the page's existing toast on save; the picker itself stays usable.
