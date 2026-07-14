# Utilization Forecast explains why the number moves

**Date:** 2026-07-15 · **Status:** approved

## Problem

The Utilization Forecast panel shows four bare numbers — `Today 62% · +30d 62% · +60d 62%
· +90d 60%` — and a subtitle saying where they come from. It never says *why* any of them
differs from today.

Utilization at a horizon is `billable FTE ÷ people still present on that date`
(`DashboardService.utilizationForecast`). Only three things move it: a billable allocation
**ends** (numerator falls), an allocation **starts** (numerator rises), or someone
**exits** — which removes them from the numerator *and* the denominator.

That third case is why bare numbers are not enough. An exit does not reliably push
utilization down: a **benched** person leaving *raises* it (denominator shrinks, billable
FTE unchanged), while a billable person leaving lowers it. A manager cannot infer from
"62 → 60" which of these is happening.

Worse, the effects cancel. A flat `62%` can mean "nothing is scheduled to change" **or**
"a roll-off is being masked by an exit". The panel renders those identically today, and
they call for opposite responses.

## What we're building

Each forecast row gains a **net delta** badge against today (`▼ −2`, `▲ +3`, or `—`),
always visible, and **expands on click** to name the events behind it.

Backend: `ForecastPoint` becomes `(label, percent, deltaPoints, drivers)`. Drivers are
computed in the loop that already walks the horizons, from data already loaded — no new
queries. Three kinds:

- **ROLL_OFF** — a billable allocation live today that has ended by the horizon. Lowers.
- **RAMP_UP** — an allocation that starts between today and the horizon. Raises.
- **EXIT** — a recorded last working day in the window. Labelled **bench exit** (raises)
  or **billable exit** (lowers), so the counterintuitive case is stated, not implied.

Each driver carries associate, project (where it has one), and date. The list is capped
with an "…and N more" line, matching the row-cap convention used elsewhere.

The expanded row has three distinct messages:

- **events present** → the named drivers.
- **no events** → "Nothing scheduled to change by then." (Flat because it is quiet.)
- **events that net to zero** → "1 roll-off and 1 bench exit cancel out." (Flat because
  two things are hiding each other — the case the panel currently cannot express.)

## Decisions

- **Net delta is the only number; drivers are named, not scored.** Utilization is a ratio,
  so when an exit moves the denominator the per-driver effects genuinely do not sum to the
  net delta. Printing "−1 pt" per driver would ship arithmetic that does not add up. Each
  driver states its direction via its kind instead.
- **Delta and drivers are measured against Today, not the previous horizon.** Each row then
  answers "where do I land versus now, and why" on its own, without the reader summing the
  rows above it. The cost — a long-horizon row repeats events from the shorter ones — is
  acceptable because rows are collapsed by default.
- **List view only.** The `charts` view keeps the plain trend line: a line already conveys
  direction, and expandable drivers would mean changing the shared `TrendChart` for one
  caller.
- **Click to expand, never hover.** Must work on touch and by keyboard; the delta itself —
  the part that provokes the question — is never hidden behind an interaction.

## Out of scope

Acting on a driver from the panel (e.g. staffing a roll-off). Read-only.
