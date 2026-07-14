# Dashboard skill-gap rows open the drill-down that already exists

**Date:** 2026-07-15 · **Status:** approved

## Problem

The Dashboard's "Skill Gaps — open demand vs supply" rows are dead markup — plain
`radar-row` divs showing `Kubernetes · 3 open · 1 on bench · 6 total · short 2` and nothing
else. To find out *who* those people are, or *which* positions are open, you have to know
that a completely separate page (Skill Reports) has a drill-down, navigate there, find the
skill, and expand it.

The drill-down is already built and good (`SkillGapPanel`, commits 6713fa7 / 4241e6b: it
opens each row into the people and positions behind it). It is simply unreachable from the
screen where you first see the gap. This is a connection problem, not a feature gap.

## What we're building

A Dashboard skill-gap row becomes a link to `#/skill-reports/<skillId>`, which opens the
Skill Reports page with that skill's row already expanded and scrolled into view.

Nothing about the drill-down is duplicated or reimplemented: the dashboard triages, the
reports page explains. That is the split the skill-gap redesign already chose.

## Decisions

- **Deep link as a path segment (`skill-reports/<skillId>`), not a query string.** The hash
  router already parses `associates/<id>` this way (`App.jsx`), so this follows the
  precedent rather than introducing a second convention.
- **The deep link must also switch the triage filter to "all".** `SkillGapPanel` defaults
  to the `short` bucket when any skill is short (`active = filter ?? (counts.short > 0 ?
  'short' : 'all')`). Deep-linking to a skill that is `tight` or `spare` would expand a row
  that is not in the visible list — a link that silently does nothing. Landing on `all`
  guarantees the target row is present whatever its bucket.
- **Seed the expansion from props, not from a `useEffect`.** `useState(focusSkillId)`
  rather than expanding after mount: no flash of the wrong bucket, and the opened state is
  observable in a server-rendered snapshot, which is the only way we can test this without
  a browser test runner. An effect is still needed to *fetch* the detail body, which is
  inherently async.
- **The full list stays visible, with the target expanded.** Landing on a single isolated
  skill would lose the surrounding context (what else is short) for no benefit — and it
  would mean a second rendering path through the panel.

## Out of scope

Making the drill-down itself richer, and any change to what the dashboard rows *say*. This
is purely about reachability.
