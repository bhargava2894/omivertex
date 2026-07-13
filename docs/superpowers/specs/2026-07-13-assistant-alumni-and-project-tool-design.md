# Assistant: former-employee lookups + project roster tool

**Date:** 2026-07-13 · **Status:** Approved

## What & why

Two follow-ups from the assistant QA pass. Both are "the assistant can't answer
a reasonable question" gaps:

1. **Former employees aren't queryable.** `get_associate_detail` resolves only
   `ACTIVE` associates (via the shared `resolveAssociate`), so asking about
   someone who has exited returns "couldn't find an active associate" — even
   though their record (skills, past projects, certs) is still in the system.
2. **No project roster tool.** The assistant has no way to answer "who is
   staffed on Project X?" — it can only look people up individually, so it
   declines.

## Fix 1 — former employees in `get_associate_detail` (drafts stay active-only)

- Split resolution: keep `resolveAssociate(name)` (ACTIVE-only) for the write
  drafts (`propose_allocation`, `propose_position_fill`) — you must not be able
  to allocate someone who has left. Add `resolveAssociate(name, boolean
  activeOnly)`; `get_associate_detail` calls it with `activeOnly = false`, so it
  resolves across all associates.
- `associateDetail` marks departed people: when `status == INACTIVE`, prepend
  "· FORMER EMPLOYEE (left <lastWorkingDay>, <exitReason>)" so the model speaks
  in the past tense and never implies they're available. The existing
  upcoming-exit line (future last working day) is unchanged.
- Update the `get_associate_detail` tool description to say it covers former
  employees and to reflect the data it now returns (skills, current + **past
  projects**, **certifications**, bench, exit) — the description was stale after
  the past-projects/cert fixes.

## Fix 2 — `get_project_detail` read tool

- New server-side read tool `get_project_detail(projectName)`, declared in
  `GeminiHttpClient` (added to `READ_TOOLS` and `FUNCTION_DECLARATIONS`) and
  executed in `AssistantService.executeReadTool` via the existing
  `resolveProject` (which already searches all projects and returns a clarifying
  reply when the name is ambiguous or unknown).
- `AssistantContextBuilder.projectDetail(Project)` returns:
  - header: `Name · CODE · @Client · <status> · <start>–<end|open>`
  - current roster (each current allocation): `associateName (percent%,
    billable/non-billable)`, or "No one is currently allocated." when empty
  - open positions on the project: `title (percent%)`, listed only if any
  - all lists capped at `MAX_TOOL_ROWS`
- Roster = `allocations.findAllWithDetails()` filtered to this project and
  `isCurrent()`; open positions = `positions.findAllWithDetails()` filtered to
  this project and `OPEN`.

## Testing

- Unit (`AssistantContextBuilderTest`): `associateDetail` marks a former
  employee and still shows their history; `projectDetail` lists the current
  roster with percents and excludes ended allocations; empty-roster wording.
- API (`AssistantApiTest`, mocked Gemini): `get_associate_detail` resolves a
  former employee instead of "couldn't find"; a draft tool still refuses an
  exited associate; `get_project_detail` executes and returns the roster.
- Full `./mvnw test` green; then a live end-to-end check against Gemini
  (former-employee question + "who's on Project X").

## Out of scope

Client-level roster ("what projects does client Y have"), and making exited
associates appear in `search_associates` or drafts — deliberately not changed.
