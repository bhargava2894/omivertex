# Skill Gap drill-down — from a number to a shortlist

Date: 2026-07-13
Status: approved

## Why

The Skill Gaps panel on the Skill Reports page renders one flat row per skill:

```
React · Frontend · 2 open · 2 on bench · 5 total          [tight]
```

The badge is a verdict with no evidence. It names no person and no position, so a
manager who reads `short 2` cannot act on it: they don't know which projects are
waiting, who already holds the skill, who is about to come free, or who is one level
away from qualifying. Every answer requires leaving the page and cross-referencing the
roster by hand.

This work makes each row openable into the four groups that explain the number. The gap
arithmetic itself does not change — we are only showing its workings.

## What

### Backend

A new endpoint `GET /api/v1/reports/skill-gaps/{skillId}` returns the detail for one
skill, served by a new method on `SkillGapService` — the class that already owns the
supply/demand math, so the proficiency threshold rule stays in one place. Unknown
skillId returns 404 via `NotFoundException`. Admin and viewer may both read it, like the
existing report.

`SkillGapDetailResponse` carries the skill, its category, the computed `threshold`
(lowest demanded proficiency; `NOVICE` when there is no demand), and four lists. All four
are derived from that same threshold:

- **openDemand** — OPEN positions with this skill as a *required* skill: position id and
  title, project name, client name, headcount, min proficiency, start date. Itemizes the
  `demand` figure.
- **benchSupply** — ACTIVE associates rated at/above threshold with no current
  allocation: associate id, name, designation, proficiency, bench days. Itemizes the
  `benchSupply` figure, and on a spare row it is the whole point of the drill-down.
- **rollingOff** — ACTIVE associates rated at/above threshold whose current allocation
  ends within `ROLLOFF_HORIZON_DAYS` (30, reusing the constant in `DashboardService`):
  name, proficiency, current project, end date. Supply about to free up — the reason a
  `short 2` may not need a hire.
- **nearMiss** — ACTIVE associates rated exactly one proficiency level *below* threshold:
  name, current proficiency, required proficiency. The train-vs-hire shortlist. Empty by
  definition when threshold is `NOVICE`.

Bench days come from the existing `AssociateResponse.benchDays(...)` helper rather than a
second copy of that math.

### Frontend

Each row in the Skill Gaps panel of `SkillReports.jsx` becomes a disclosure button. On
first expand it lazily fetches that skill's detail and renders the four groups; the
result is cached per skill for the session, so re-expanding is instant. The summary list
stays light — no change to the existing summary payload, and the Dashboard panel that
shares its DTO is untouched.

Names link to `#/associates/:id` and positions to `#/positions`, so every row is a
launchpad. An empty group renders a one-line reason ("nobody within one level of
Advanced") rather than vanishing — an absent group is itself information.

### Badge fix

`gapBadge` currently reads `gap === 0` as `tight`. That is wrong when there is no demand
and nobody on the bench (`0 - 0 = 0`), which today renders identically to a skill whose
demand is exactly met. Zero demand with zero bench supply is **fully deployed**, not
tight. The rule becomes:

| condition                        | badge          |
| -------------------------------- | -------------- |
| gap > 0                          | short N        |
| gap === 0 and demand > 0         | tight          |
| gap === 0 and demand === 0       | fully deployed |
| gap < 0                          | +N spare       |

`gapBadge` is duplicated in spirit between the Dashboard panel and this page; the fixed
version moves to one shared module so the two views cannot drift.

## Demo data

`SeedDataLoader` seeded no open positions at all, so the report could only ever produce
surplus rows and looked broken. It now seeds a scenario with one skill in each state —
Kubernetes short, React tight, Tableau spare — rated and allocated so every drill-down
group has rows. It is idempotent via a marker position, seeds into a database that
already has hand-created positions, and never overwrites a rating a user entered.

## Testing

TDD, API-level as the repo does it. `SkillGapDetailApiTest` covers: a short skill
(demand itemized, bench empty, near-miss populated), a spare skill (bench populated,
demand empty), the roll-off horizon boundary (day 30 in, day 31 out), the near-miss
level rule (one below in, two below out), and 404 for an unknown skillId.

## Out of scope

No hire-vs-train recommendation engine, no cost modelling, and no change to how the gap
number is calculated. Deduplicating the two `Kubernetes` skills that exist in the dev
taxonomy (one under *Containers & Orchestration*, one under *DevOps & Cloud*) is a data
problem, tracked separately.
