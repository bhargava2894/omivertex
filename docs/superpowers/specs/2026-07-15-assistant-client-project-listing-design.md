# Mirai can enumerate clients and projects

**Date:** 2026-07-15 · **Status:** approved

## Problem

Asked "how many clients do we have?" Mirai answers correctly ("12") — that number comes
from the aggregate line in the standing context (`AssistantContextBuilder.build()`).
Asked the obvious follow-up, "what are they?", it can only name the handful of clients
that happen to have an open position, and then honestly admits it cannot list the rest.

That is not a hallucination or a bug in the model: the standing context deliberately
carries **counts only** (privacy/scale decision, 2026-07-11), and none of the six read
tools can enumerate clients or projects. `get_project_detail` resolves a project the
model already knows the name of; it cannot discover names. The only place a client name
appears at all is the `@Client` suffix on `list_open_positions` rows — hence the partial,
open-position-shaped answer.

So Mirai knows *how many* clients exist and is structurally unable to say *which*.

## What we're building

Two new read tools, alongside the existing five:

- **`list_clients`** — every client: name, industry, location, and how many projects they
  have. No arguments.
- **`list_projects`** — every project: name, code, client, status, dates. Optional
  `clientName` filter so "what is Helios running?" is one call instead of a scan.

Both are read tools in the existing sense: executed server-side, result fed back to the
model, row-capped at `MAX_TOOL_ROWS` with an explicit "…and N more" line so a growing
org can never blow up the prompt.

## Decisions

- **List everything, don't filter to ACTIVE.** The standing context's `Clients:` /
  `Projects:` numbers come from `count()`, i.e. all rows. If the list filtered to active
  it would not reconcile with the count the model just quoted, and Mirai would contradict
  itself. Instead every row carries its status, and non-active rows are marked. The list
  and the count always agree.
- **Aggregate counts stay in the standing context; names stay behind tools.** This
  extends the 2026-07-11 privacy decision rather than reversing it — clients and projects
  are org data, not personal data, but they still leave the server only on request and
  still under the row cap.
- **Advertise the tools in the system prompt.** The prompt names its lookup tools
  explicitly; unnamed tools get under-called. Both new names are added there.

## Out of scope

Client/project *mutations* from chat (drafts like `propose_allocation`). Read-only here.
