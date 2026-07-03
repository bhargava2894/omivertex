# OmiVertex — Functional Overview

*Audience: sales, account managers, leadership, and anyone explaining OmiVertex
without touching code.*

---

## What is OmiVertex?

**OmiVertex is Softility's resource management hub — one place that always knows
who is working where, for which client, and whether they're billable.**

IT staffing lives and dies by three questions:

1. **Who is on the bench, and for how long?** Every idle day is unbilled cost.
2. **Who is rolling off soon?** Redeploying someone *before* their project ends is
   the difference between revenue and bench.
3. **How utilized are we?** The single number leadership asks for.

Before OmiVertex, the answers lived in spreadsheets — stale the moment they were
emailed. OmiVertex answers all three live, from one screen.

## Who uses it

| Persona | What they do in OmiVertex |
|---|---|
| **Resource / bench manager** | Watches the roll-off radar and bench aging, reassigns people, keeps utilization up |
| **Delivery / account manager** | Checks who's staffed on their client, headcount per project, onshore/offshore mix |
| **Leadership** | Reads the dashboard: utilization %, billability mix, staffing trend, top clients |
| **Operations / admin** | Maintains the master client & project lists, imports rosters, exports reports |
| **Everyone else** | Read-only access — sees everything, changes nothing |

## A tour of the product

### The Dashboard — your morning briefing

Open OmiVertex and the day's picture is already there:

- **KPI tiles** — total associates, **billable utilization %** (properly weighted: someone allocated at 50% counts as half a person, not a full one), billable headcount, bench count, active projects.
- **Roll-off Radar** — everyone whose engagement ends in the next 30 days, most urgent first, with countdown badges (red = a week or less). This is the "act now" list: find the next seat before the current one closes.
- **Cert Expiry Radar** — a live list of associate certifications expiring in the next 90 days, sorted with the soonest expiry first and color-coded flags (red ≤ 30 days, amber ≤ 60 days, blue ≤ 90 days).
- **Bench Aging** — the bench split into 0–30 / 31–60 / 60+ day buckets with the longest-benched people on top. Nobody quietly sits idle for two months anymore.
- **Live charts** — billability mix, onshore/offshore delivery mix, headcount by client, and a six-month staffing trend. Everything animates in, responds to hover, and can be flipped to a simple list view.

### The Roster & Search — find the right consultant instantly

The Associates page is the always-current version of "the staffing spreadsheet": each person with their company, location, onshore/offshore, **current customer and project, and billability status**.
- **Faceted Skill Search**: Filter the entire roster by Skill Category, specific Skills/Tools, and minimum Proficiency (Novice to Mastery) to target exactly who you need.
- **Associate Profiles**: Click any associate to open their comprehensive profile card showing their current and historic allocations, their verified skills grouped by category, and their certifications list with active warnings for upcoming expirations.

### Skill Reports & Taxonomy Admin — organizational capability overview

- **Skill Reports**: Visualize the distribution of proficiency levels (from Novice to Mastery) across all categories and skills as stacked bar charts. This helps leadership see where the company has a strong talent pool and where upskilling is needed.
- **Taxonomy Administration**: Admins can define and manage a structured taxonomy of skill categories (e.g., Cloud, Frontend, Backend) and nested skills/tools (e.g., AWS, React, Spring Boot).

### Demand Matching — fill open seats with the best candidates

Open positions are created in the Demand tab. When adding a position, specify a required skill from the taxonomy and a minimum proficiency level.
- **Smart Recommendations**: The system matches open roles against the bench roster, ranking candidates first by whether they satisfy the skill and minimum proficiency requirements (ordering by proficiency descending), then by how long they have been on the bench, ensuring maximum utilization.

### Clients, Projects, Allocations — the system of record

Master lists of clients and their projects, plus the allocation workspace where associates are assigned to projects with a billable flag, an allocation percentage, and start/end dates. The system protects the data for you:

- Nobody can be **double-booked past 100%** of their capacity — the system blocks it and says why.
- No duplicate clients, project codes, or associates can be created.
- Nothing with active history can be accidentally deleted.
- Rolling someone off is just setting an end date — history stays intact, and the person automatically appears on the bench.

### Excel in, anything out

- **Import**: drag the staffing spreadsheet (Excel or CSV) onto the import box — OmiVertex creates any missing clients, projects, and associates and allocates everyone in one pass. It also supports multi-sheet workbooks with `Employees`, `EmployeeSkills` (supporting `ignoreNovice` filters), and `Certifications` sheets to seed your talent data.
- **Export**: the complete roster as **Excel, CSV, PDF, or Word** in one click — formatted and ready to attach to a client or leadership email.

### Access control built for a staffing org

- **Super Admin** — full view and edit rights, manages access requests.
- **User (read-only)** — sees every dashboard, list, and export, but cannot change
  a thing. Edit buttons don't just fail — they don't exist.
- New colleagues can request access with their **@softility.com** email; a Super
  Admin approves or rejects them from the Access Requests screen. The rules are
  enforced on the server, not just hidden in the interface.

### Feels premium, works everywhere

A polished, modern interface with **light and dark themes** (or follow the OS),
smooth animations that respect accessibility settings, and a sign-in page that
makes an internal tool feel like a product. Runs in any modern browser — nothing
to install.

## The value story, in one paragraph

Every day an associate sits unnoticed on the bench costs their full loaded cost;
every roll-off discovered late costs weeks of ramp-down revenue. OmiVertex makes
both impossible to miss — bench aging and the roll-off radar surface them on the
first screen every stakeholder sees, while the capacity guard and single source
of record keep the underlying data trustworthy enough to act on. **Fewer idle
days, earlier redeployment, and a utilization number everyone can finally agree
on.**

## Frequently asked questions

**Is our data safe?** OmiVertex is an internal system: it runs on Softility
infrastructure, requires sign-in for everything, and only Super Admins can change
data. Read-only users can look, not touch.

**Do we have to abandon our spreadsheets?** No — day one is literally importing
your current spreadsheet. Exports mean anyone who still wants Excel gets Excel,
generated fresh from live data.

**What does "billable utilization" mean here?** The share of total workforce
capacity currently on billable work, weighted by allocation percentage. It's the
honest version of the number — a person on two half-time projects counts once.

**Can it handle our whole roster?** Yes for typical rosters (hundreds of
associates). Very large datasets and per-person login accounts are on the
roadmap below.

## Where it's heading

Near-term candidates, roughly in order of impact: visa/work-authorization tracking with expiry alerts, bill rates and margin reporting, notification digests (roll-offs and bench alerts by email), and full single-sign-on.

---

*For architecture, APIs, and build instructions see* `docs/TECHNICAL.md`.
