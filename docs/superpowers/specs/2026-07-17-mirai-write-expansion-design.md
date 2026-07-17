# Mirai — write expansion + role-aware tools

**Date:** 2026-07-17 · **Status:** Approved

## What & why

Mirai can start engagements (allocate, fill a position) but cannot wind them
down, adjust them, or open new seats — and every caller gets the same tool
set. This phase adds **three write drafts** and **two admin-only read tools**
behind a new **role-aware tool registration** seam.

The core safety contract is unchanged: **write tools only ever produce a
`ProposedAction` draft**; the user confirms in the browser through the
existing REST endpoints, so role checks, the capacity guard, and audit stay
exactly where they are.

## 1. New write drafts

All three follow the existing resolution pattern: exact-then-contains name
matching, ambiguity or no-match returns a clarifying reply, never a guess.

**`propose_end_allocation(associateName, projectName, endDate?)`**
Resolves the associate's **current** allocation on the named project (the
duplicate-open-allocation guard means at most one exists). No current
allocation → clarifying reply naming what was checked. Draft:
`ActionType.END_ALLOCATION` carrying `allocationId`, the allocation's existing
`billable`/`percent`/`startDate`, and the requested `endDate` (default today).
An `endDate` before the allocation's start date is rejected at draft time with
a clarifying reply (the server would 400 anyway; Mirai should not draft it).
Confirm calls the existing `PUT /allocations/{id}` with
`{billable, allocationPercent, startDate, endDate}`.

**`propose_edit_allocation(associateName, projectName, percent?, billable?, endDate?)`**
Same resolution. The draft merges requested changes over the allocation's
current values (anything not mentioned keeps its current value). Capacity
warning applies when the new percent would take the associate over 100% —
computed **excluding the allocation being edited** from their current total
(raising someone 50→80 must not warn against their own 50). Confirm calls the
same `PUT /allocations/{id}`.

**`propose_position(title, projectName, skillName?, minProficiency?, percent?, billable?, startDate?, endDate?)`**
Resolves the project, and (user decision) **one required skill** against the
skill taxonomy with the same exact-then-contains matching — unknown or
ambiguous skill name → clarifying reply listing the near matches. Unparseable
`minProficiency` degrades to `INTERMEDIATE`. Defaults: percent 100, billable
true, headcount 1, no dates. Draft: `ActionType.CREATE_POSITION` carrying
`projectId/projectName`, `positionTitle` (the title field reused), and the new
skill fields. Confirm calls the existing `POST /positions` with a one-entry
`skills` list (`required: true`); a skill-less draft (no skillName given)
sends an empty skills list.

**DTO changes** — `AssistantChatResponse.ProposedAction` gains nullable
fields: `allocationId`, `skillId`, `skillName`, `minProficiency`.
`ActionType` gains `END_ALLOCATION`, `EDIT_ALLOCATION`, `CREATE_POSITION`.
The record is `@JsonInclude(NON_NULL)` at class level via the response —
absent fields stay absent for old action types, so existing UI paths are
untouched.

**Frontend** — `AssistantChat.confirmAction` gains the three cases:
END/EDIT → `api.update('allocations', action.allocationId, {...})`;
CREATE_POSITION → `api.create('positions', {...})`. Draft cards render
exactly as today (summary + warnings + Confirm/Dismiss); viewers still see
"Requires admin to confirm".

## 2. Role-aware tool registration

**Caller identity** — `AssistantController` resolves a
`AssistantService.Caller(String username, boolean admin)` record on the
servlet thread (`admin` = authorities contain `ROLE_ADMIN`; username via
`AuditService.currentUsername()` as today) and passes it through both
endpoints. The `chat(request, username, …)` signatures become
`chat(request, caller, …)`; the interaction log keeps logging
`caller.username()`.

**Declaration filtering** — `GeminiClient.replyWithTools` gains a
`boolean adminTools` parameter. `GeminiHttpClient` splits its declarations
into the base list and an admin-only list (`list_pending_approvals`,
`get_audit_history`), sending the admin declarations **only** when
`adminTools` is true — a viewer's model never sees them. `READ_TOOLS` gains
an `ADMIN_READ_TOOLS` sibling; the loop executes an admin tool only when
`adminTools` is true (otherwise it falls through like an unknown action).

**Dispatch defense in depth** — `AssistantService.executeReadTool` also gates:
the admin cases check `caller.admin()` and return `Unknown tool: <name>` for
non-admins, so even a hallucinated call cannot leak data.

**Standing prompt** — `AssistantContextBuilder.build(boolean adminTools)`
appends the admin tool names to the tool list only for admins; the no-arg
`build()` remains (non-admin) so existing tests and callers stay valid.

## 3. Admin-only read tools

Thin formatters in `AssistantContextBuilder`, row-capped at `MAX_TOOL_ROWS`
with the shared overflow line, following every existing convention:

**`list_pending_approvals`** — pending `ProfileChangeRequest` rows (associate
name, change type, requested date) and PENDING `AppUser` access requests
(email, name). Empty state: "Nothing is waiting for approval." Needs two new
constructor deps: `ProfileChangeRequestRepository`, `AppUserRepository`.

**`get_audit_history(entityType?, limit?)`** — recent `AuditEntry` rows newest
first: timestamp, username, action, entityType/id, summary. `limit` defaults
to 25 and is capped at `MAX_TOOL_ROWS`; `entityType` filters when given.
Empty state names the filter.

## Error handling

Existing patterns only: clarifying replies for resolution failures, lenient
arg coercion, per-tool empty states. Draft-time pre-validation (end-before-
start, capacity) mirrors what the confirm endpoint would enforce — warnings
warn, invalid drafts are not offered.

## Testing & verification

TDD per AGENTS.md — failing test first for every behavior.

- **Per verb (`AssistantApiTest`):** resolution failure → clarifying reply;
  happy path → draft JSON with the right type, ids, merged/default values,
  and summary; `propose_edit_allocation` capacity warning fires at >100
  excluding its own allocation and stays silent when the edit is within
  capacity; `propose_end_allocation` rejects end-before-start;
  `propose_position` resolves the skill and defaults percent/billable.
- **Role seam:** non-admin dispatch of `list_pending_approvals` /
  `get_audit_history` returns `Unknown tool`; admin dispatch returns rows.
  A stub-server test (loop-cap style) captures the request body sent to
  Gemini and asserts admin declarations are present for `adminTools=true`
  and absent for `false`.
- **Formatters (`AssistantContextBuilderTest`):** pending approvals (both
  queues, empty state), audit history (ordering, entityType filter, cap),
  `build(true)` advertises admin tools / `build()` does not.
- **Frontend:** `npm run format && npm run build`; live check of one
  end-allocation draft confirm.
- **Docs:** `TECHNICAL.md` (new action types, admin tools, Caller/role
  filtering) and `TODO.md` decisions (role-aware registration seam). Graph
  refreshed; full `./mvnw test` green throughout.

## Out of scope

Golden-suite questions for the new verbs (extend later), an ASSOCIATE-scoped
Mirai, bulk edits ("end everyone on project X"), position headcount >1
drafts, token streaming, history persistence, feedback buttons.
