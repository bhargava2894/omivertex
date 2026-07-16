# Staffing Trend Drivers explains historical staffing changes

**Date:** 2026-07-16 · **Status:** approved

## Problem

The "Staffing Trend — last 6 months" panel shows aggregate counts of allocated and billable associates for the trailing 6 months. It displays numbers (e.g., `23 billable` in March vs `22 billable` in April), but does not tell the user *why* the numbers changed or *who* was responsible for the shift.

To understand what happened, a manager has to manually hunt through historical records, audit logs, or project allocations. The panel should let them see the actual events (Ramp-ups, Roll-offs, Exits) that caused the movement in any historical month.

## What we're building

In the **Card/Text View** of the Staffing Trend, each month card will be hoverable/clickable. Clicking a card will expand it downwards to list the actual events (historical drivers) that occurred during that month.

### Backend Changes

1. **TrendPoint DTO**: Update `TrendPoint` in [DashboardSummaryResponse.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java) to include `List<ForecastDriver> drivers`.
2. **DashboardService**: In `staffingTrend()`, for each month:
   - Identify associates who **exited** in this month (`lastWorkingDay` lies within the month's start/end dates).
     - If the associate had any overlapping billable allocation in the month, classify them as a `BILLABLE_EXIT`. Otherwise, `BENCH_EXIT`.
   - Identify **ramp-ups** (billable allocations starting in the month) and **roll-offs** (billable allocations ending in the month) for associates who did *not* exit in the month.
   - Map these to `ForecastDriver` objects and attach them to the `TrendPoint`.

### Frontend Changes

1. **Dashboard.jsx**:
   - Add state `expandedMonth` to keep track of which month is currently expanded.
   - Support clicking on a card to toggle its expanded state.
   - If expanded, render a smooth slide-down panel containing the list of drivers (using the shared Framer Motion `collapse` variants).
   - Display a nice badge and descriptive text for each driver (reusing icons and styling conventions).

## Decisions

- **Reuse `ForecastDriver` and `DriverKind`**: Since the structural contract for a historical trend change (Associate, Project, Date, Kind) matches the future forecast changes, we reuse the existing `ForecastDriver` record and `DriverKind` enum to maintain clean code and avoid duplicate structures.
- **De-duplication**: To prevent double-counting, if an associate exits in a month, their billable allocations ending in that same month are not reported as separate `ROLL_OFF` events. Instead, they are represented solely as a single `BILLABLE_EXIT` or `BENCH_EXIT` event.
- **Visual Design**: The card layout keeps its clean, compact form. Expandable details are only shown in the Text/Card view when toggled. The Chart view continues to show the high-level trend line.

## Out of scope

Interactive links to edit historical allocations from the trend panel. The view is read-only.
