# Skill-Gap Full Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the dashboard's capped skill-gap panel to a full report — a dedicated `GET /api/v1/reports/skill-gaps` endpoint (uncapped, includes surplus skills with zero open demand) rendered as a new section on the SkillReports page, with the gap math extracted to one shared service.

**Architecture:** Extract the gap computation currently inlined in `DashboardService.summary()` (lines ~177-204) into a new `SkillGapService` with two entry points: `dashboardPanel()` (demand-only, capped 20, existing behavior) and `fullReport()` (uncapped, plus surplus rows). `DashboardService` delegates; a new `SkillGapController` exposes the full report. Frontend adds a "Skill Gaps" card to `SkillReports.jsx` reusing the dashboard panel's row/badge idiom plus an `HBarChart` of top shortages.

**Tech Stack:** Spring Boot 3.5 / Java 21, H2-backed MockMvc API tests, React 18 (existing `charts.jsx`, `Badge`, `useLoad`).

**Branch:** `feature/skill-gaps-and-ai` (already created). Spec: `docs/superpowers/specs/2026-07-11-skill-gaps-resume-ai-assistant-actions-design.md`.

**Conventions that apply (AGENTS.md):** TDD — failing test first, `./mvnw test` green before every commit; controllers return DTOs; constructor injection; no magic numbers; CSS tokens only in frontend; refresh graphify + update `docs/TECHNICAL.md` at the end.

---

### Task 1: Failing API test for the full report

**Files:**
- Create: `src/test/java/com/softility/omivertex/api/SkillGapReportApiTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillGapReportApiTest extends ApiTestBase {

    /** Demand seat: one OPEN position requiring the skill at the given minimum. */
    private void demand(String title, com.softility.omivertex.domain.Skill skill,
                        com.softility.omivertex.domain.Project project, Proficiency min) {
        OpenPosition position = new OpenPosition();
        position.setTitle(title);
        position.setProject(project);
        openPositionRepository.save(position);
        PositionSkill req = new PositionSkill();
        req.setPosition(position);
        req.setSkill(skill);
        req.setMinProficiency(min);
        req.setRequired(true);
        positionSkillRepository.save(req);
    }

    @Test
    void report_includesDemandAndSurplusRows_sortedWorstGapFirst() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var react = skill("Frontend", "React");

        // Java: 1 open seat, nobody qualified -> gap +1 (worst, sorts first)
        demand("Java Dev", java, proj, Proficiency.INTERMEDIATE);

        // React: no open demand, one bench holder -> surplus row, gap -1
        var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(bench, react, Proficiency.ADVANCE);

        mockMvc.perform(get("/api/v1/reports/skill-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].skillName").value("Java"))
                .andExpect(jsonPath("$[0].demand").value(1))
                .andExpect(jsonPath("$[0].benchSupply").value(0))
                .andExpect(jsonPath("$[0].gap").value(1))
                .andExpect(jsonPath("$[1].skillName").value("React"))
                .andExpect(jsonPath("$[1].demand").value(0))
                .andExpect(jsonPath("$[1].benchSupply").value(1))
                .andExpect(jsonPath("$[1].totalSupply").value(1))
                .andExpect(jsonPath("$[1].gap").value(-1));
    }

    @Test
    void report_isReadableByViewer() throws Exception {
        var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(bench, skill("Backend", "Java"), Proficiency.ADVANCE);

        mockMvc.perform(get("/api/v1/reports/skill-gaps")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void report_excludesInactiveAssociatesAndUnratedSkills() throws Exception {
        var java = skill("Backend", "Java");
        skill("Backend", "Kotlin"); // in taxonomy but never rated, no demand -> no row
        var leaver = associate("Old Timer", "old@softility.com", WorkMode.ONSHORE);
        rateSkill(leaver, java, Proficiency.MASTERY);
        leaver.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(leaver);

        mockMvc.perform(get("/api/v1/reports/skill-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))          // Java row: rated once, holder inactive
                .andExpect(jsonPath("$[0].skillName").value("Java"))
                .andExpect(jsonPath("$[0].totalSupply").value(0))
                .andExpect(jsonPath("$[0].gap").value(0));      // 0 demand - 0 bench
    }
}
```

Note on the third test: a skill rated only by an INACTIVE associate still yields a row (the rating row exists) but with zero supply. A never-rated skill with no demand yields no row. This pins the "open demand OR at least one rated associate" rule from the spec.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=SkillGapReportApiTest`
Expected: FAIL — all three tests get 404/401-style failures (`status().isOk()` assertion fails) because the endpoint does not exist.

---

### Task 2: SkillGapService + SkillGapController (make it pass)

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/SkillGapService.java`
- Create: `src/main/java/com/softility/omivertex/web/SkillGapController.java`

- [ ] **Step 1: Write the service** (the math is a verbatim extraction of `DashboardService.java:177-204`, generalized with the surplus option)

```java
package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One source of truth for skill supply-vs-demand math. Demand = required
 * skills on OPEN positions; supply counts ACTIVE associates at or above the
 * lowest demanded proficiency (any proficiency when there is no demand);
 * gap = demand - benchSupply (positive = hire or train, negative = spare).
 */
@Service
@Transactional(readOnly = true)
public class SkillGapService {

    /** Cap on the dashboard skill-gap panel rows (full report is uncapped). */
    static final int DASHBOARD_ROW_CAP = 20;

    private final AssociateRepository associateRepository;
    private final AllocationRepository allocationRepository;
    private final PositionSkillRepository positionSkillRepository;
    private final AssociateSkillRepository associateSkillRepository;

    public SkillGapService(AssociateRepository associateRepository,
                           AllocationRepository allocationRepository,
                           PositionSkillRepository positionSkillRepository,
                           AssociateSkillRepository associateSkillRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.positionSkillRepository = positionSkillRepository;
        this.associateSkillRepository = associateSkillRepository;
    }

    /** Dashboard panel: demand-only rows, worst gap first, capped. */
    public List<DashboardSummaryResponse.SkillGap> dashboardPanel() {
        return compute(false).stream().limit(DASHBOARD_ROW_CAP).toList();
    }

    /** Full report: also skills with rated supply but no open demand (surplus rows). */
    public List<DashboardSummaryResponse.SkillGap> fullReport() {
        return compute(true);
    }

    private List<DashboardSummaryResponse.SkillGap> compute(boolean includeSurplus) {
        Set<Long> activeIds = associateRepository.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .map(Associate::getId)
                .collect(Collectors.toSet());
        Set<Long> allocatedIds = allocationRepository.findAllWithDetails().stream()
                .filter(Allocation::isCurrent)
                .map(a -> a.getAssociate().getId())
                .filter(activeIds::contains)
                .collect(Collectors.toSet());

        Map<Long, List<PositionSkill>> demandBySkill = positionSkillRepository.findAllWithDetails().stream()
                .filter(PositionSkill::isRequired)
                .filter(ps -> ps.getPosition().getStatus() == PositionStatus.OPEN)
                .collect(Collectors.groupingBy(ps -> ps.getSkill().getId()));
        List<AssociateSkill> ratedSkills = associateSkillRepository.findAllWithDetails();

        Map<Long, Skill> skillsById = new LinkedHashMap<>();
        demandBySkill.values().forEach(reqs ->
                skillsById.putIfAbsent(reqs.get(0).getSkill().getId(), reqs.get(0).getSkill()));
        if (includeSurplus) {
            ratedSkills.forEach(s -> skillsById.putIfAbsent(s.getSkill().getId(), s.getSkill()));
        }

        return skillsById.values().stream()
                .map(skill -> {
                    List<PositionSkill> reqs = demandBySkill.getOrDefault(skill.getId(), List.of());
                    Proficiency threshold = reqs.stream()
                            .map(r -> r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency())
                            .min(Comparator.comparingInt(Enum::ordinal)).orElse(Proficiency.NOVICE);
                    Set<Long> holders = ratedSkills.stream()
                            .filter(s -> s.getSkill().getId().equals(skill.getId()))
                            .filter(s -> s.getProficiency().ordinal() >= threshold.ordinal())
                            .map(s -> s.getAssociate().getId())
                            .filter(activeIds::contains)
                            .collect(Collectors.toSet());
                    long benchSupply = holders.stream().filter(h -> !allocatedIds.contains(h)).count();
                    return new DashboardSummaryResponse.SkillGap(skill.getId(), skill.getName(),
                            skill.getCategory().getName(), reqs.size(), benchSupply, holders.size(),
                            reqs.size() - benchSupply);
                })
                .sorted(Comparator.comparingLong(DashboardSummaryResponse.SkillGap::gap).reversed()
                        .thenComparing(DashboardSummaryResponse.SkillGap::skillName))
                .toList();
    }
}
```

Deliberate choice (record in TODO.md later): the report reuses the `DashboardSummaryResponse.SkillGap` DTO instead of minting a duplicate record — one shape for the same concept.

- [ ] **Step 2: Write the controller**

```java
package com.softility.omivertex.web;

import com.softility.omivertex.service.SkillGapService;
import com.softility.omivertex.web.dto.DashboardSummaryResponse.SkillGap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/skill-gaps")
public class SkillGapController {

    private final SkillGapService skillGapService;

    public SkillGapController(SkillGapService skillGapService) {
        this.skillGapService = skillGapService;
    }

    /** Full skill supply-vs-demand report, incl. surplus skills. Admin + viewer (GET). */
    @GetMapping
    public List<SkillGap> report() {
        return skillGapService.fullReport();
    }
}
```

No `SecurityConfig` change needed: `GET /api/v1/**` is already `hasAnyRole("ADMIN", "VIEWER")` (`SecurityConfig.java:74`).

- [ ] **Step 3: Run the new test — verify it passes**

Run: `./mvnw test -Dtest=SkillGapReportApiTest`
Expected: PASS (3 tests).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/SkillGapService.java \
        src/main/java/com/softility/omivertex/web/SkillGapController.java \
        src/test/java/com/softility/omivertex/api/SkillGapReportApiTest.java
git commit -m "feat: GET /api/v1/reports/skill-gaps — uncapped supply-vs-demand report incl. surplus skills

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: DashboardService delegates to SkillGapService

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java` (remove lines ~177-204 block, the `MAX_SKILL_GAP_ROWS` constant, and now-unused imports/repositories)
- Test: existing `src/test/java/com/softility/omivertex/api/DashboardApiTest.java` (`summary_reportsSkillGaps` is the regression net — do not modify it)

- [ ] **Step 1: Refactor**

In `DashboardService`:
1. Add constructor param + field `private final SkillGapService skillGapService;` (drop `positionSkillRepository` and `associateSkillRepository` fields **if** nothing else in the class uses them — verify with a search inside the file before deleting).
2. Delete the `// skill gaps: ...` block (the `demandBySkill`/`ratedSkills`/`skillGaps` stream) and the `MAX_SKILL_GAP_ROWS` constant.
3. Where `skillGaps` was passed into the response, use:

```java
List<DashboardSummaryResponse.SkillGap> skillGaps = skillGapService.dashboardPanel();
```

4. Remove imports that became unused (`PositionSkill`, `PositionStatus`, `Skill`, `Proficiency` — only if genuinely unused; Spotless will flag them).

- [ ] **Step 2: Run the full suite — refactor must be behavior-neutral**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all ~183 tests green — especially `DashboardApiTest.summary_reportsSkillGaps` unchanged and passing. If Spotless complains: `./mvnw spotless:apply` and rerun.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/DashboardService.java
git commit -m "refactor: dashboard skill-gap panel delegates to shared SkillGapService

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: "Skill Gaps" section on the SkillReports page

**Files:**
- Modify: `frontend/src/pages/SkillReports.jsx`

> Deviation from spec (deliberate): the spec sketched a `DataTable`; this uses the
> `radar-row` + `Badge` list idiom instead so the report is visually identical to the
> dashboard's Skill Gaps panel (same concept, same look). Noted per AGENTS.md.

- [ ] **Step 1: Add the section**

In `SkillReports.jsx`:

1. Add imports (top of file, `HBarChart` from charts):

```jsx
import { HBarChart } from '../components/charts.jsx';
```

2. In the `SkillReports` component, load the report alongside the existing loads:

```jsx
const { data: gaps } = useLoad(() => api.list('reports/skill-gaps'), []);
```

3. Add a `GapBadge` helper above the component (mirrors the Dashboard panel's tones — keep the two consistent):

```jsx
function gapBadge(gap) {
  if (gap > 0) return { tone: 'red', label: `short ${gap}` };
  if (gap === 0) return { tone: 'amber', label: 'tight' };
  return { tone: 'green', label: `+${-gap} spare` };
}
```

4. Render the section as the FIRST child of the page's outer `<div style={{ display: 'grid', gap: '24px' }}>`, before the category panels:

```jsx
{gaps && gaps.length > 0 && (
  <div className="card" style={{ padding: '24px' }}>
    <h3
      style={{
        margin: '0 0 6px 0',
        fontSize: '16px',
        fontWeight: '700',
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
      }}
    >
      Skill Gaps — open demand vs supply
    </h3>
    <p className="stat-hint" style={{ marginTop: 0 }}>
      Every skill with open demand or rated associates. Gap = open seats minus bench
      supply at the required proficiency.
    </p>
    {gaps.filter((g) => g.gap > 0).length > 0 && (
      <HBarChart
        rows={gaps
          .filter((g) => g.gap > 0)
          .map((g) => ({ label: g.skillName, value: g.gap }))}
        color="var(--chart-1)"
        unit=" short"
      />
    )}
    <div style={{ marginTop: '12px' }}>
      {gaps.map((g) => {
        const badge = gapBadge(g.gap);
        return (
          <div className="radar-row" key={g.skillId}>
            <div>
              <div className="cell-main">{g.skillName}</div>
              <div className="cell-sub">
                {g.category} · {g.demand} open · {g.benchSupply} on bench · {g.totalSupply} total
              </div>
            </div>
            <Badge tone={badge.tone} label={badge.label} />
          </div>
        );
      })}
    </div>
  </div>
)}
```

(`Badge`, `api`, `useLoad` are already imported in this file. All colors go through tokens — `var(--chart-1)` — per AGENTS.md.)

- [ ] **Step 2: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds (Prettier + ESLint clean). Check there is no `HBarChart` unused-import warning if the demand list is empty — the import is used unconditionally in the JSX, so this is fine.

- [ ] **Step 3: Verify visually (if a dev environment is running)**

Run: `./mvnw spring-boot:run` (seeded data) and open `#/skill-reports`.
Expected: gaps card on top, worst shortage first, badges matching the Dashboard panel's tones.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/SkillReports.jsx
git commit -m "feat: Skill Gaps section on Skill Reports — full uncapped report with shortage chart

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Docs, graph refresh, wrap-up

**Files:**
- Modify: `docs/TECHNICAL.md` (endpoints/contract section)
- Modify: `docs/TODO.md` ("Resolved decisions")

- [ ] **Step 1: Document the contract**

In `docs/TECHNICAL.md`, alongside the other report/dashboard endpoints, add:

```markdown
### GET /api/v1/reports/skill-gaps (ADMIN, VIEWER)
Full skill supply-vs-demand report. One row per skill with open required demand
**or** at least one rated associate. Fields: `skillId`, `skillName`, `category`,
`demand` (open seats requiring the skill), `benchSupply` / `totalSupply` (ACTIVE
associates at or above the lowest demanded min-proficiency; any proficiency when
demand is zero), `gap` = `demand - benchSupply` (positive = shortage). Sorted
worst gap first, uncapped. The dashboard `skillGaps` panel is the same math via
`SkillGapService`, capped at 20 demand-only rows.
```

In `docs/TODO.md` under "Resolved decisions", add:

```markdown
- **Skill-gap report reuses `DashboardSummaryResponse.SkillGap`** (2026-07-11):
  one DTO for one concept; positions with only legacy free-text `requiredSkill`
  are excluded from gap demand (consistent with the structured-skills direction).
```

- [ ] **Step 2: Full suite + graph refresh**

Run: `./mvnw test` → BUILD SUCCESS.
Run: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

- [ ] **Step 3: Commit**

```bash
git add docs/TECHNICAL.md docs/TODO.md graphify-out
git commit -m "docs: skill-gap report contract + resolved decisions; graph refresh

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
