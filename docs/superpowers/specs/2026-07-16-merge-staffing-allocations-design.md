# Merge Allocations + Staffing into one page

**Date:** 2026-07-16 · **Status:** approved

## What & why

Two pages show the same thing. **Allocations** (`Allocations.jsx`, ~368 lines) renders a
client → project → associate collapsible tree with billable/non-billable counts, grouped
**client-side** from `/allocations`, plus filters, search, an active-only toggle, and
Add/Edit/Remove. **Staffing** (`Staffing.jsx`, ~130 lines) renders essentially the same
tree with the same counts and styling, but **read-only**, grouped **server-side**
(`StaffingService`, incl. the "billable wins per client" rule), and available to viewers.

So the tree + billable rollup exists twice — once client-side, once server-side — which
is exactly the "one source of truth per concept" rule (AGENTS.md) being violated. The
only real differences: Allocations can **edit** and see **ended/historical** allocations;
Staffing is **read-only**, **viewer-accessible**, and **current-only**.

We merge them into **one page**: viewers see the read-only tree as today; admins get
inline Assign/Edit/Remove on the same tree. The server-side staffing tree becomes the
single source; the client-side regrouping is deleted.

**Name:** nav label **"Staffing & Allocations"**; route path stays `staffing` so the
dashboard's `#/staffing?clientId=` deep link keeps working.

## What we're building

### Backend
- **Extend `StaffedAssociate`** (in `StaffingDtos`) with `allocationId` (Long),
  `endDate` (LocalDate), and `active` (boolean). Inline edit/remove need the allocation
  id (PUT/DELETE target) and end date (edit field); `active` lets the UI render and
  exclude ended rows.
- **`StaffingService.staffing(boolean includeEnded)`**:
  - `includeEnded=false` (default): current allocations only — today's behavior.
  - `includeEnded=true`: also include non-current (ended) allocations, each marked
    `active=false`.
  - **Billable/non-billable counts at every level always reflect CURRENT allocations
    only.** Ended rows are shown (marked, greyed) but never move the counts, so the
    rollup remains a true "who is staffed now" number. Sorting/grouping unchanged.
- **`GET /staffing?includeEnded=`** (`StaffingController`) — remains ADMIN+VIEWER and
  read-only. **No new mutation endpoints:** edits reuse the existing `/allocations`
  POST/PUT/DELETE, so the capacity guard, uniqueness, protective-delete, and audit rules
  are untouched.

### Frontend
- **`Staffing.jsx` becomes the single page.** For `canEdit` only, it adds:
  - per-associate **Edit** and **Remove**,
  - per-project **+ Assign**,
  - toolbar **New allocation**, an **Include ended** toggle, and search.
  These open a shared **`AllocationModal`** (extracted from the current Allocations
  modal: associate search-picker, client→project picker, %, billable, start/end dates)
  and call `/allocations` CRUD, then reload the tree. Capacity 409s surface as a toast.
  Viewers see the tree with no controls (server enforces this regardless).
- **Delete `Allocations.jsx`** and its client-side grouping.
- **Nav:** collapse the two Delivery entries into one (`staffing`, label "Staffing &
  Allocations"). Remove the `allocations` route; if any `#/allocations` link remains,
  redirect it to `#/staffing`.

## Decisions

- **Server tree is the single source.** The client-side regrouping in Allocations is
  deleted; `StaffingService` already computes the tree and the client-level "billable
  wins" rule, so there is one implementation.
- **Counts = current only, even when ended rows are shown.** Mixing ended allocations
  into the headline counts would make "billable now" wrong. Ended rows are visible for
  management but visually distinct and count-neutral.
- **Reuse `/allocations` for writes.** No new mutation surface; every existing invariant
  and audit path stays exactly as-is.
- **Path stays `staffing`.** Preserves the dashboard deep link; only the nav label and
  the removal of the `allocations` entry change.

## Testing / verification

- Extend `StaffingApiTest`: rows now include `allocationId`/`endDate`/`active`;
  `includeEnded=true` returns ended rows with `active=false` and excluded from counts;
  `includeEnded=false` (and the no-arg default) is current-only as before; endpoint stays
  ADMIN+VIEWER. Existing `AllocationApiTest` (the reused CRUD) is unchanged.
- Live check: viewer sees the read-only tree; admin assigns/edits/removes inline and the
  tree + counts update; toggling "Include ended" shows ended rows marked; a capacity
  breach surfaces as a toast.

## Out of scope (YAGNI)

- A flat/sortable table view (the merge intentionally replaces Allocations' flat table
  with the grouped tree; a tree/flat toggle can be added later only if asked).
- Any change to allocation business rules or the `/allocations` contract.
