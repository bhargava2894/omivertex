# Allocation Capacity Integrity & Staffing Visibility — Design

**Date:** 2026-07-10
**Status:** Approved design (pre-plan)
**Author:** bhargava.sista28@gmail.com (with Claude)

## Goal

Three related improvements to allocation data and its visibility:

1. **Bug fix:** stop Excel/CSV import from over-allocating people past 100%.
2. **Profile actions:** end a current allocation and assign to a new project directly
   from the Associate Profile page.
3. **Staffing visibility:** per-company billable/non-billable splits on the dashboard,
   with a drill-down Staffing page showing company → project → associates.

## Why

An associate was found allocated 100% on two concurrent projects (200% total). The
capacity guard in `AllocationService.assertCapacity` protects the UI assign flow and
the position-fill flow, but `ImportService` creates allocations with no capacity check
— the import path silently violates a protective invariant that AGENTS.md declares
non-negotiable. Separately, managers cannot end/reassign an allocation from the
profile where they notice the problem, and the dashboard cannot answer "how many
billable vs non-billable people does this company/project have?" even though
non-billable associates legitimately sit inside projects.

## Part 1 — Close the capacity hole in Import

### Behavior
- When an import row's allocation would push the associate's total concurrent
  allocation over 100%:
  - The **associate is still created/updated** (and skills/certs still import).
  - The **allocation is not created**.
  - A **row error** is added, e.g. `Row 7: Priya Sharma would be allocated 200% in
    this period; the maximum is 100%. Roll off or reduce another allocation first.`
- Dry-run previews report the same errors without writing anything (existing dry-run
  semantics unchanged).
- Rows that merely repeat an existing open allocation on the same project continue to
  be counted as `skipped` (existing idempotency behavior unchanged).

### Implementation
- **One implementation per cross-cutting rule** (AGENTS.md): the existing
  `AllocationService.assertCapacity(...)` becomes callable by `ImportService` (widen
  visibility or extract; no second copy of the rule). Import allocations are created
  at 100%, start `LocalDate.now()`, no end date — capacity is checked with exactly
  those values.
- `ImportService` catches the capacity `ConflictException` per row, records the error
  message in the summary's `errors` list, and continues with the next row.

### Existing bad data
- No migration. Over-allocated associates (e.g. Priya) are fixed manually with the
  Part 2 "End" action. The guard prevents new violations from any path.

### Tests
- Import a sheet allocating an associate (already 100% on project A) to project B →
  summary shows 1 error containing "maximum is 100%", `allocationsCreated` excludes
  the row, the associate still exists/updates, only the project-A allocation exists.
- Same import as dry-run → same error reported, nothing persisted.
- A row for the same associate+project remains `skipped` (not an error).

## Part 2 — End & Assign on the Associate Profile

No new backend endpoints. Both actions reuse existing, guard-protected APIs:
`PUT /api/v1/allocations/{id}` (end) and `POST /api/v1/allocations` (assign).

### End
- Each **Current** row in the profile's Allocation & Engagement History table gets an
  **End** button (rendered only when `canEdit`).
- Clicking opens a small dialog: end date input, pre-filled with today; Cancel / End
  allocation. Save sends the allocation's existing `billable`, `allocationPercent`,
  `startDate` plus the chosen `endDate` to the update endpoint.
- On success: toast, history reloads, the row shows **Ended**. Rows are never deleted
  — history is preserved indefinitely (existing behavior, now documented).
- End date before the start date is rejected by the dialog before submit.

### Assign to Project
- An **Assign to Project** button above the history table (admins only).
- Opens the same Company → Project assign form used on the Allocations page
  (searchable `SearchSelect` pickers, allocation %, billable checkbox, start date),
  with the associate **pre-selected and locked**.
- Submitting calls the existing create endpoint; the capacity guard applies, so
  assigning someone still at 100% fails with the server's clear message shown in the
  form. Natural flow: End first, then Assign.

### Shared form component
- Extract the assign-form fields from `Allocations.jsx` into a shared
  `AllocationForm` component (same extraction pattern as `SkillEditor`), used by both
  the Allocations page and the profile dialog. The Allocations page keeps its
  associate picker; the profile passes a fixed associate.

### Tests
- Backend behavior is already covered (update endpoint sets end dates; create
  enforces capacity). Frontend verified by build + manual smoke (no JS test runner in
  this repo — stated exception, consistent with prior features).

## Part 3 — Dashboard split + Staffing page

### Dashboard card upgrade
- `DashboardSummaryResponse.ClientHeadcount` gains fields:
  `clientId`, `billable`, `nonBillable` (existing `headcount` stays = billable +
  nonBillable).
- Counting rules (current allocations only, distinct associates per client):
  - An associate counts as **billable for a client** if *any* of their current
    allocations under that client is billable; otherwise they count non-billable.
  - The same person may appear under multiple clients (they hold allocations at
    each); within one client they are counted once.
- The dashboard card renders `Acme Corp · 12 — 9 billable / 3 non-billable`; each row
  links to the Staffing page anchored at that client.

### Staffing page
- New sidebar entry **Staffing** (visible to admin and viewer), route `#/staffing`;
  `#/staffing/{clientId}` deep-links/expands that client.
- Layout: one collapsible section per company (header: name + B/NB counts) →
  projects inside (header: project name, code + B/NB counts) → a table of that
  project's associates: name (links to `#/associates/{id}`), designation,
  allocation %, billable/non-billable badge, start date.
- Companies with no current allocations show an empty state inside their section.

### New endpoint
- `GET /api/v1/staffing` (read-only; admin + viewer per existing security rules —
  no security config changes).
- Response shape (`StaffingDtos`):

```
[
  { clientId, clientName, billable, nonBillable,
    projects: [
      { projectId, projectName, projectCode, billable, nonBillable,
        associates: [
          { associateId, name, designation, allocationPercent, billable, startDate }
        ] }
    ] }
]
```

- Built from **current** allocations only (`Allocation::isCurrent`), via the existing
  `findAllWithDetails()` fetch. Clients sorted by headcount desc then name; projects
  by name; associates by name. Per-project counts are distinct associates within that
  project; per-client counts follow the dashboard counting rules above.
- Service: new `StaffingService` (thin, read-only), controller `StaffingController`.

### Tests
- `StaffingApiTest`: tree shape; a non-billable allocation inside an otherwise
  billable project appears with `billable=false` and is counted in the project's and
  client's `nonBillable`; an associate billable on one project and non-billable on
  another under the same client counts once as billable at client level; ended
  allocations are excluded; viewer role can GET.
- `DashboardApiTest` (extend): per-client `billable`/`nonBillable` values.

## Non-goals
- No allocation history redesign (history already persists; unchanged).
- No auto-fixing of existing over-allocated data.
- No cross-client deduplication in staffing totals (a person on two clients appears
  under both — that reflects reality).
- No pagination on the staffing endpoint (data volume mirrors the client-paged
  Clients/Projects lists; tracked P3 pattern).
- No changes to position-fill or assign flows (already guarded).

## Build order
Part 1 (bug fix) → Part 2 (profile actions) → Part 3 (dashboard + staffing), each
TDD with focused commits; full suite green before every commit.
