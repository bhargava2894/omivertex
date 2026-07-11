# Exit Tracking, Multi-Skill Matching, Gap/Forecast Dashboard, Self-Service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the four features approved in `docs/superpowers/specs/2026-07-10-hr-lifecycle-selfservice-design.md`: associate exit tracking with auto-cleanup, multi-skill position matching, skill-gap + utilization-forecast dashboard, and an ASSOCIATE self-service role with an admin approval queue.

**Architecture:** Spring Boot service layer owns all rules + audit; web boundary stays DTO-only; every behavior lands test-first as an HTTP-level MockMvc test in `src/test/java/com/softility/omivertex/api/`. Three Flyway migrations (V6–V8). React frontend reuses shared components (`SkillEditor`, `DataTable`, `Modal`, `Badge`, `Field`).

**Tech Stack:** Spring Boot 3.5 / Java 21, Flyway, H2 (tests) / PostgreSQL, React 18 + Vite.

**Conventions reminder (AGENTS.md):** enums not Strings; constants not magic numbers; constructor injection; `./mvnw test` green before every commit; `./mvnw spotless:apply` if hygiene fails; commit messages end with the Co-Authored-By line.

---

## Feature 1 — Exit tracking

### Task 1: Exit fields on Associate (entity + API)

**Files:**
- Create: `src/main/java/com/softility/omivertex/domain/ExitReason.java`
- Create: `src/main/resources/db/migration/V6__add_associate_exit_fields.sql`
- Modify: `src/main/java/com/softility/omivertex/domain/Associate.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateRequest.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java` (`apply`)
- Test: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java`

- [ ] **Step 1: Write the failing tests** (append to `AssociateApiTest`)

```java
@Test
void updateAssociate_recordsExit() throws Exception {
    var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                             "workMode":"OFFSHORE","exitReason":"RESIGNED",
                             "resignationDate":"%s","lastWorkingDay":"%s"}"""
                            .formatted(java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(30))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitReason").value("RESIGNED"))
            .andExpect(jsonPath("$.lastWorkingDay").value(java.time.LocalDate.now().plusDays(30).toString()));
}

@Test
void exitReasonWithoutLastWorkingDay_returns400() throws Exception {
    var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                             "workMode":"OFFSHORE","exitReason":"RESIGNED"}"""))
            .andExpect(status().isBadRequest());
}

@Test
void resignationAfterLastWorkingDay_returns400() throws Exception {
    var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                             "workMode":"OFFSHORE","exitReason":"RESIGNED",
                             "resignationDate":"%s","lastWorkingDay":"%s"}"""
                            .formatted(java.time.LocalDate.now().plusDays(10), java.time.LocalDate.now())))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=AssociateApiTest` — expect the three new tests FAIL (unknown JSON property / missing 400).

- [ ] **Step 3: Implement.**

`ExitReason.java`:
```java
package com.softility.omivertex.domain;

public enum ExitReason {
    RESIGNED, TERMINATED, CONTRACT_ENDED, RETIRED, OTHER
}
```

`V6__add_associate_exit_fields.sql`:
```sql
-- Exit lifecycle: who left, when, and why. lastWorkingDay drives auto-cleanup
-- (status flip + allocation end) and the exits KPI.
ALTER TABLE associates ADD COLUMN resignation_date date;
ALTER TABLE associates ADD COLUMN last_working_day date;
ALTER TABLE associates ADD COLUMN exit_reason varchar(255);
ALTER TABLE associates ADD CONSTRAINT associates_exit_reason_check
    CHECK (exit_reason IN ('RESIGNED','TERMINATED','CONTRACT_ENDED','RETIRED','OTHER'));
```

`Associate.java` — after `joinedDate`, add fields + accessors:
```java
    private LocalDate resignationDate;

    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;
```

`AssociateRequest` — add after `joinedDate`:
```java
        java.time.LocalDate resignationDate,
        java.time.LocalDate lastWorkingDay,
        ExitReason exitReason,
```

`AssociateResponse` — add `resignationDate`, `lastWorkingDay`, `exitReason` (types `LocalDate, LocalDate, ExitReason`) after `joinedDate` in the record and in `from(...)`.

`AssociateService.apply` — after `setJoinedDate`:
```java
        if ((request.exitReason() == null) != (request.lastWorkingDay() == null)) {
            throw new BadRequestException("Exit reason and last working day must be provided together");
        }
        if (request.resignationDate() != null && request.lastWorkingDay() != null
                && request.resignationDate().isAfter(request.lastWorkingDay())) {
            throw new BadRequestException("Resignation date cannot be after the last working day");
        }
        associate.setResignationDate(request.resignationDate());
        associate.setLastWorkingDay(request.lastWorkingDay());
        associate.setExitReason(request.exitReason());
```
(import `com.softility.omivertex.web.error.BadRequestException`)

- [ ] **Step 4: Run** `./mvnw test -Dtest=AssociateApiTest` — expect PASS (all).
- [ ] **Step 5: Commit** `feat: exit fields on associate (reason, resignation date, last working day)`

### Task 2: Auto-cleanup on last working day

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Create: `src/main/java/com/softility/omivertex/config/ExitScheduler.java`
- Modify: `src/main/java/com/softility/omivertex/OmivertexApplication.java` (`@EnableScheduling`)
- Test: `src/test/java/com/softility/omivertex/api/ExitProcessingTest.java` (new)

- [ ] **Step 1: Write the failing test**

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.service.AssociateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExitProcessingTest extends ApiTestBase {

    @Autowired AssociateService associateService;

    @Test
    void processExits_flipsStatusAndEndsAllocations() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var leaver = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(leaver, proj, true); // open-ended, started 3 months ago
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().minusDays(2));
        associateRepository.save(leaver);

        associateService.processExits();

        var refreshed = associateRepository.findById(leaver.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(EntityStatus.INACTIVE);
        var allocations = allocationRepository.findByAssociateId(leaver.getId());
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getEndDate()).isEqualTo(LocalDate.now().minusDays(2));
        // idempotent: second run changes nothing and does not throw
        associateService.processExits();
    }

    @Test
    void processExits_deletesAllocationsThatNeverStarted() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var leaver = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var future = allocation(leaver, proj, true);
        future.setStartDate(LocalDate.now().plusDays(30)); // seat they will never take
        allocationRepository.save(future);
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().minusDays(1));
        associateRepository.save(leaver);

        associateService.processExits();

        assertThat(allocationRepository.findByAssociateId(leaver.getId())).isEmpty();
    }
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=ExitProcessingTest` — expect FAIL: `processExits()` not defined.

- [ ] **Step 3: Implement.** In `AssociateService` (inject `AllocationRepository allocationRepository` via constructor — it may already be there; add if not):

```java
    /**
     * Applies exit cleanup for every ACTIVE associate whose last working day has
     * passed: status -> INACTIVE, open/later-ending allocations end on the last
     * working day, allocations that never started are removed. Idempotent — runs
     * daily from ExitScheduler and inline when an already-past exit is recorded.
     */
    @Transactional
    public void processExits() {
        LocalDate today = LocalDate.now();
        associateRepository.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .filter(a -> a.getLastWorkingDay() != null && a.getLastWorkingDay().isBefore(today))
                .forEach(this::applyExit);
    }

    private void applyExit(Associate associate) {
        LocalDate lastDay = associate.getLastWorkingDay();
        int ended = 0;
        for (Allocation allocation : allocationRepository.findByAssociateId(associate.getId())) {
            if (allocation.getStartDate().isAfter(lastDay)) {
                allocationRepository.delete(allocation);
                ended++;
            } else if (allocation.getEndDate() == null || allocation.getEndDate().isAfter(lastDay)) {
                allocation.setEndDate(lastDay);
                ended++;
            }
        }
        associate.setStatus(EntityStatus.INACTIVE);
        auditService.record("EXITED", "Associate", associate.getId(),
                associate.getName() + " left on " + lastDay + " (" + associate.getExitReason() + "); "
                + ended + " allocation(s) closed");
    }
```

At the end of `create(...)` and `update(...)` (after save/apply), add the inline trigger:
```java
        if (associate.getLastWorkingDay() != null
                && associate.getLastWorkingDay().isBefore(LocalDate.now())
                && associate.getStatus() == EntityStatus.ACTIVE) {
            applyExit(associate);
        }
```

`ExitScheduler.java`:
```java
package com.softility.omivertex.config;

import com.softility.omivertex.service.AssociateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs exit cleanup shortly after midnight so future-dated exits take effect. */
@Component
public class ExitScheduler {

    private final AssociateService associateService;

    public ExitScheduler(AssociateService associateService) {
        this.associateService = associateService;
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void nightlyExitSweep() {
        associateService.processExits();
    }
}
```

Add `@EnableScheduling` to `OmivertexApplication` (import `org.springframework.scheduling.annotation.EnableScheduling`).

- [ ] **Step 4: Run** `./mvnw test -Dtest=ExitProcessingTest` then `./mvnw test` — expect PASS / all green.
- [ ] **Step 5: Commit** `feat: exit auto-cleanup — status flip + allocation end on last working day`

### Task 3: "Exits (last 12 mo)" dashboard KPI

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java`
- Test: `src/test/java/com/softility/omivertex/api/DashboardApiTest.java`

- [ ] **Step 1: Failing test** (append to `DashboardApiTest`)

```java
@Test
void summary_countsExitsInTrailingYear() throws Exception {
    var recent = associate("Gone Recently", "gone1@softility.com", WorkMode.ONSHORE);
    recent.setExitReason(com.softility.omivertex.domain.ExitReason.RESIGNED);
    recent.setLastWorkingDay(java.time.LocalDate.now().minusDays(30));
    recent.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
    associateRepository.save(recent);

    var old = associate("Gone Long Ago", "gone2@softility.com", WorkMode.ONSHORE);
    old.setExitReason(com.softility.omivertex.domain.ExitReason.RESIGNED);
    old.setLastWorkingDay(java.time.LocalDate.now().minusDays(400));
    old.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
    associateRepository.save(old);

    mockMvc.perform(get("/api/v1/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitsLast12Months").value(1));
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=DashboardApiTest` — expect FAIL (`No value at JSON path`).

- [ ] **Step 3: Implement.** In `DashboardService`, add constant `static final int EXIT_WINDOW_DAYS = 365;`. In `summary()`, before building the response (note: use the **unfiltered** repository list, since leavers are INACTIVE):

```java
        LocalDate exitWindowStart = LocalDate.now().minusDays(EXIT_WINDOW_DAYS);
        long exitsLast12Months = associateRepository.findAll().stream()
                .filter(a -> a.getLastWorkingDay() != null
                        && !a.getLastWorkingDay().isAfter(LocalDate.now())
                        && !a.getLastWorkingDay().isBefore(exitWindowStart))
                .count();
```

Append `long exitsLast12Months` to the `DashboardSummaryResponse` record (at the end) and pass it in the constructor call.

- [ ] **Step 4: Run** `./mvnw test` — all green.
- [ ] **Step 5: Commit** `feat: dashboard KPI — exits in trailing 12 months`

### Task 4: Exit frontend (form section, profile banner, KPI tile)

**Files:**
- Modify: `frontend/src/pages/Associates.jsx`
- Modify: `frontend/src/pages/Profile.jsx`
- Modify: `frontend/src/pages/Dashboard.jsx`

- [ ] **Step 1: Associates.jsx** — extend `EMPTY` with `resignationDate: '', lastWorkingDay: '', exitReason: ''`; map them in `openEdit` (`row.resignationDate || ''` etc.); in `save()` payload send `|| null` for all three. In the modal after the Status field add:

```jsx
            <Field label="Exit reason" error={errors.exitReason}>
              <select
                value={editing.form.exitReason}
                onChange={(e) => set('exitReason', e.target.value)}
              >
                <option value="">— still employed —</option>
                <option value="RESIGNED">Resigned</option>
                <option value="TERMINATED">Terminated</option>
                <option value="CONTRACT_ENDED">Contract ended</option>
                <option value="RETIRED">Retired</option>
                <option value="OTHER">Other</option>
              </select>
            </Field>
            <Field label="Resignation date" error={errors.resignationDate}>
              <input type="date" value={editing.form.resignationDate}
                onChange={(e) => set('resignationDate', e.target.value)} />
            </Field>
            <Field label="Last working day" error={errors.lastWorkingDay}>
              <input type="date" value={editing.form.lastWorkingDay}
                onChange={(e) => set('lastWorkingDay', e.target.value)} />
            </Field>
```

- [ ] **Step 2: Profile.jsx** — where the associate header renders, show a banner when exited (match existing badge/banner styling in that file):

```jsx
      {data.exitReason && (
        <div className="form-alert">
          Exited — last working day {data.lastWorkingDay} ({data.exitReason.replaceAll('_', ' ').toLowerCase()})
        </div>
      )}
```

- [ ] **Step 3: Dashboard.jsx** — add a KPI tile following the existing tile markup, labeled `Exits (12 mo)` bound to `summary.exitsLast12Months`.

- [ ] **Step 4: Verify** `cd frontend && npm run build` — green. Backend still green.
- [ ] **Step 5: Commit** `feat: exit UI — form section, profile banner, exits KPI tile`

---

## Feature 2 — Multi-skill positions & matching

### Task 5: PositionSkill entity + position CRUD with skills list

**Files:**
- Create: `src/main/java/com/softility/omivertex/domain/PositionSkill.java`
- Create: `src/main/java/com/softility/omivertex/repository/PositionSkillRepository.java`
- Create: `src/main/resources/db/migration/V7__position_skills.sql`
- Modify: `src/main/java/com/softility/omivertex/domain/OpenPosition.java` (remove `requiredSkillRef`, `minProficiency`; add `workMode`)
- Modify: `src/main/java/com/softility/omivertex/web/dto/PositionRequest.java`, `PositionResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/PositionService.java`
- Test: `src/test/java/com/softility/omivertex/api/PositionApiTest.java`

- [ ] **Step 1: Failing tests**

```java
@Test
void createPosition_withSkillRequirements() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var java = skill("Backend", "Java");
    var aws = skill("Cloud", "AWS");

    mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title":"Senior Java Developer","projectId":%d,"workMode":"ONSHORE",
                             "skills":[{"skillId":%d,"minProficiency":"INTERMEDIATE","required":true},
                                       {"skillId":%d,"required":false}]}"""
                            .formatted(proj.getId(), java.getId(), aws.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.workMode").value("ONSHORE"))
            .andExpect(jsonPath("$.skills", hasSize(2)))
            .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
            .andExpect(jsonPath("$.skills[0].required").value(true))
            .andExpect(jsonPath("$.skills[1].skillName").value("AWS"))
            .andExpect(jsonPath("$.skills[1].required").value(false));
}

@Test
void createPosition_duplicateSkill_returns400() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var java = skill("Backend", "Java");
    mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title":"Dev","projectId":%d,
                             "skills":[{"skillId":%d,"required":true},{"skillId":%d,"required":false}]}"""
                            .formatted(proj.getId(), java.getId(), java.getId())))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=PositionApiTest` — new tests FAIL (unknown properties).

- [ ] **Step 3: Implement.**

`PositionSkill.java` (mirror `AssociateSkill`'s structure):
```java
package com.softility.omivertex.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "position_skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"position_id", "skill_id"}))
public class PositionSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private OpenPosition position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    private Proficiency minProficiency;

    @Column(nullable = false)
    private boolean required = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OpenPosition getPosition() { return position; }
    public void setPosition(OpenPosition position) { this.position = position; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Proficiency getMinProficiency() { return minProficiency; }
    public void setMinProficiency(Proficiency minProficiency) { this.minProficiency = minProficiency; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
}
```

`PositionSkillRepository.java`:
```java
package com.softility.omivertex.repository;

import com.softility.omivertex.domain.PositionSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PositionSkillRepository extends JpaRepository<PositionSkill, Long> {

    List<PositionSkill> findByPositionId(Long positionId);

    void deleteByPositionId(Long positionId);

    @Query("select ps from PositionSkill ps join fetch ps.skill s join fetch s.category join fetch ps.position")
    List<PositionSkill> findAllWithDetails();
}
```

`V7__position_skills.sql`:
```sql
-- A position now demands a LIST of skills (must-have / nice-to-have with a minimum
-- proficiency) plus an optional work-mode constraint. The old single structured
-- skill columns migrate into the new table as one must-have row.
CREATE TABLE position_skills (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    position_id bigint NOT NULL REFERENCES open_positions(id),
    skill_id bigint NOT NULL REFERENCES skills(id),
    min_proficiency varchar(255),
    required boolean NOT NULL DEFAULT true,
    CONSTRAINT position_skills_unique UNIQUE (position_id, skill_id),
    CONSTRAINT position_skills_min_proficiency_check CHECK (min_proficiency IN
        ('NOVICE','FOUNDATIONAL','INTERMEDIATE','FUNCTIONAL_USER','ADVANCE','MASTERY'))
);

INSERT INTO position_skills (position_id, skill_id, min_proficiency, required)
SELECT id, required_skill_id, min_proficiency, true
FROM open_positions WHERE required_skill_id IS NOT NULL;

ALTER TABLE open_positions DROP COLUMN required_skill_id;
ALTER TABLE open_positions DROP COLUMN min_proficiency;
ALTER TABLE open_positions ADD COLUMN work_mode varchar(255);
ALTER TABLE open_positions ADD CONSTRAINT open_positions_work_mode_check
    CHECK (work_mode IN ('ONSHORE','OFFSHORE'));
```

`OpenPosition.java`: delete the `requiredSkillRef` + `minProficiency` fields/accessors; add:
```java
    @Enumerated(EnumType.STRING)
    private WorkMode workMode; // null = any

    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
```

`PositionRequest`: remove `requiredSkillId` + `minProficiency`; add:
```java
        WorkMode workMode,
        @Valid List<SkillReq> skills,
        ...
    public record SkillReq(@NotNull Long skillId, Proficiency minProficiency, Boolean required) {}
```
(keep `requiredSkill` legacy text field; `required` null → true)

`PositionResponse`: remove `requiredSkillId/requiredSkillName/minProficiency`; add `WorkMode workMode` and `List<SkillLine> skills` with
```java
    public record SkillLine(Long skillId, String skillName, String category,
                            Proficiency minProficiency, boolean required) {}
```
`from(OpenPosition position, List<PositionSkill> skills)` maps them (sort: required first, then skill name). Update all callers to pass the list.

`PositionService`: inject `PositionSkillRepository positionSkills`; in `apply(...)` replace the old requiredSkillRef block with:
```java
        position.setWorkMode(request.workMode());
```
and after `positions.save(position)` in `create`/inside `update`, replace the skill rows:
```java
    private void replaceSkills(OpenPosition position, List<PositionRequest.SkillReq> reqs) {
        positionSkills.deleteByPositionId(position.getId());
        if (reqs == null) return;
        Set<Long> seen = new HashSet<>();
        for (PositionRequest.SkillReq req : reqs) {
            if (!seen.add(req.skillId())) {
                throw new BadRequestException("Duplicate skill in requirements");
            }
            Skill skill = skillRepository.findById(req.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", req.skillId()));
            PositionSkill ps = new PositionSkill();
            ps.setPosition(position);
            ps.setSkill(skill);
            ps.setMinProficiency(req.minProficiency());
            ps.setRequired(req.required() == null || req.required());
            positionSkills.save(ps);
        }
    }
```
`list()`/`get()` load `findAllWithDetails()` grouped by position id (or `findByPositionId` for single) to build responses. `delete()` must `deleteByPositionId(id)` first (protective ordering).

**Existing tests to update in the same commit:** any `PositionApiTest`/frontend-era test using `requiredSkillId`/`minProficiency` in JSON or asserting `matchedProficiency` — switch them to the `skills` array shape. The legacy `requiredSkill` text tests stay untouched.

- [ ] **Step 4: Run** `./mvnw test` — all green.
- [ ] **Step 5: Commit** `feat: positions carry multi-skill requirements + work-mode constraint (V7)`

### Task 6: Matching algorithm (full vs partial, missing labels)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/PositionService.java` (`matches`)
- Modify: `src/main/java/com/softility/omivertex/web/dto/MatchCandidateResponse.java`
- Test: `src/test/java/com/softility/omivertex/api/PositionApiTest.java`

- [ ] **Step 1: Failing test**

```java
@Test
void matches_ranksFullMatchesAboveParzialsWithMissingLabels() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var java = skill("Backend", "Java");
    var aws = skill("Cloud", "AWS");
    var k8s = skill("Cloud", "Kubernetes");

    var full = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    rateSkill(full, java, Proficiency.ADVANCE);
    rateSkill(full, aws, Proficiency.INTERMEDIATE);
    rateSkill(full, k8s, Proficiency.FOUNDATIONAL);

    var partial = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
    rateSkill(partial, java, Proficiency.ADVANCE); // missing AWS

    var offshore = associate("Anita Rao", "anita@softility.com", WorkMode.OFFSHORE);
    rateSkill(offshore, java, Proficiency.ADVANCE);
    rateSkill(offshore, aws, Proficiency.ADVANCE); // skills ok, wrong shore

    var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title":"Senior Java Developer","projectId":%d,"workMode":"ONSHORE",
                             "skills":[{"skillId":%d,"minProficiency":"INTERMEDIATE","required":true},
                                       {"skillId":%d,"required":true},
                                       {"skillId":%d,"required":false}]}"""
                            .formatted(proj.getId(), java.getId(), aws.getId(), k8s.getId())))
            .andExpect(status().isCreated()).andReturn();
    long id = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/api/v1/positions/" + id + "/matches"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Priya Sharma"))
            .andExpect(jsonPath("$[0].fullMatch").value(true))
            .andExpect(jsonPath("$[0].missingRequirements", hasSize(0)))
            .andExpect(jsonPath("$[1].fullMatch").value(false))
            .andExpect(jsonPath("$[2].fullMatch").value(false));
}
```
Also update the legacy test `matches_ranksBenchWithMatchingSkillFirst`: replace `$[0].skillMatch` / `$[1].skillMatch` assertions with `$[0].fullMatch` / `$[1].fullMatch`.

- [ ] **Step 2: Run** — FAIL (no `fullMatch` in response).

- [ ] **Step 3: Implement.** New `MatchCandidateResponse`:
```java
public record MatchCandidateResponse(
        Long associateId, String name, String designation,
        Long benchDays, int availablePercent,
        boolean fullMatch,
        List<String> matchedSkills,
        List<String> missingRequirements) {}
```

Rewrite `PositionService.matches(Long id)`:
```java
    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> matches(Long id) {
        OpenPosition position = find(id);
        List<PositionSkill> requirements = positionSkills.findByPositionId(id);
        List<Allocation> all = allocations.findAllWithDetails();
        Map<Long, List<Allocation>> byAssociate = all.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkillRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));

        record Scored(MatchCandidateResponse dto, int mustMet, int niceMet, Long benchDays) {}

        List<Scored> scored = associates.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .map(a -> {
                    List<Allocation> history = byAssociate.getOrDefault(a.getId(), List.of());
                    int allocated = history.stream().filter(Allocation::isCurrent)
                            .mapToInt(Allocation::getAllocationPercent).sum();
                    int available = Math.max(0, 100 - allocated);
                    if (available < position.getAllocationPercent()) return null;
                    Long benchDays = AssociateResponse.benchDays(a, history);

                    Map<Long, Proficiency> held = skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                            .collect(Collectors.toMap(s -> s.getSkill().getId(), AssociateSkill::getProficiency));

                    List<String> matched = new ArrayList<>();
                    List<String> missing = new ArrayList<>();
                    int mustTotal = 0, mustMet = 0, niceMet = 0;
                    for (PositionSkill req : requirements) {
                        Proficiency min = req.getMinProficiency() == null ? Proficiency.NOVICE : req.getMinProficiency();
                        Proficiency has = held.get(req.getSkill().getId());
                        boolean ok = has != null && has.ordinal() >= min.ordinal();
                        if (req.isRequired()) {
                            mustTotal++;
                            if (ok) { mustMet++; matched.add(req.getSkill().getName()); }
                            else missing.add(req.getSkill().getName() + " (min " + min + ")");
                        } else if (ok) { niceMet++; matched.add(req.getSkill().getName()); }
                    }
                    boolean workModeOk = position.getWorkMode() == null || position.getWorkMode() == a.getWorkMode();
                    if (!workModeOk) missing.add(position.getWorkMode().name().toLowerCase() + " required");

                    boolean full;
                    if (requirements.isEmpty()) {
                        // legacy fallback: free-text headline match
                        full = matchesSkill(a, position.getRequiredSkill()) && workModeOk;
                        if (!full && position.getRequiredSkill() != null && !position.getRequiredSkill().isBlank()
                                && !matchesSkill(a, position.getRequiredSkill())) {
                            missing.add(position.getRequiredSkill());
                        }
                    } else {
                        full = mustMet == mustTotal && workModeOk;
                    }
                    return new Scored(new MatchCandidateResponse(a.getId(), a.getName(), a.getDesignation(),
                            benchDays, available, full, matched, missing), mustMet, niceMet, benchDays);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((Scored s) -> s.dto().fullMatch(), Comparator.reverseOrder())
                        .thenComparing(Scored::mustMet, Comparator.reverseOrder())
                        .thenComparing(Scored::niceMet, Comparator.reverseOrder())
                        .thenComparing(s -> s.benchDays() == null ? -1L : s.benchDays(), Comparator.reverseOrder())
                        .thenComparing(s -> s.dto().name()))
                .toList();

        return scored.stream().map(Scored::dto).limit(MAX_MATCH_CANDIDATES).toList();
    }
```
with `static final int MAX_MATCH_CANDIDATES = 10;` (replacing the inline `10`).

- [ ] **Step 4: Run** `./mvnw test` — all green (fix any drifted assertions).
- [ ] **Step 5: Commit** `feat: multi-skill matching — full matches first, partials labeled with what's missing`

### Task 7: Positions frontend (requirements editor + match labels)

**Files:**
- Modify: `frontend/src/pages/Positions.jsx`

- [ ] **Step 1:** Replace `requiredSkillId`/`minProficiency` in `EMPTY`, `openEdit`, `save` payload with `workMode: ''` and `skills: []` (each row `{skillId, minProficiency, required}`); payload sends `workMode || null` and the rows with `skillId: Number(...)`. Replace the two old form fields with a requirements editor:

```jsx
            <Field label="Skill requirements" full>
              {editing.form.skills.map((row, i) => (
                <div key={i} className="skill-req-row" style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <SearchSelect
                    options={(taxonomy || []).flatMap((cat) =>
                      (cat.skills || []).map((s) => ({ value: s.id, label: `${cat.name} · ${s.name}` })))}
                    value={row.skillId}
                    onChange={(v) => setSkillRow(i, { ...row, skillId: v })}
                    placeholder="Skill…"
                  />
                  <select value={row.minProficiency || ''}
                    onChange={(e) => setSkillRow(i, { ...row, minProficiency: e.target.value || null })}>
                    <option value="">Any level</option>
                    {PROFICIENCIES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
                  </select>
                  <select value={row.required ? 'must' : 'nice'}
                    onChange={(e) => setSkillRow(i, { ...row, required: e.target.value === 'must' })}>
                    <option value="must">Must-have</option>
                    <option value="nice">Nice-to-have</option>
                  </select>
                  <button className="btn btn-ghost btn-sm" onClick={() => removeSkillRow(i)}>✕</button>
                </div>
              ))}
              <button className="btn btn-ghost btn-sm"
                onClick={() => set('skills', [...editing.form.skills, { skillId: '', minProficiency: null, required: true }])}>
                + Add requirement
              </button>
            </Field>
            <Field label="Work mode">
              <select value={editing.form.workMode} onChange={(e) => set('workMode', e.target.value)}>
                <option value="">Any</option>
                <option value="ONSHORE">Onshore</option>
                <option value="OFFSHORE">Offshore</option>
              </select>
            </Field>
```
with helpers `setSkillRow(i, row)` / `removeSkillRow(i)` updating `editing.form.skills`. Filter out rows with empty `skillId` in `save()`.

- [ ] **Step 2:** Positions table: show `skills` summary (`r.skills.map(s => s.skillName).join(', ')`) in place of the old required-skill column. Match modal: per candidate render `fullMatch` as a green "Full match" `Badge`, else an amber badge plus `missingRequirements.join(' · ')` as muted text.

- [ ] **Step 3: Verify** `cd frontend && npm run build`; click-through via `npm run dev` if desired. Backend `./mvnw test` still green.
- [ ] **Step 4: Commit** `feat: position form requirements editor + match modal missing-skill labels`

---

## Feature 3 — Dashboard: skill gaps + utilization forecast

### Task 8: `skillGaps` on /dashboard/summary

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java`
- Test: `src/test/java/com/softility/omivertex/api/DashboardApiTest.java`

- [ ] **Step 1: Failing test**

```java
@Test
void summary_reportsSkillGaps() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var java = skill("Backend", "Java");

    // demand: one open position must-have Java >= INTERMEDIATE
    var position = new com.softility.omivertex.domain.OpenPosition();
    position.setTitle("Java Dev");
    position.setProject(proj);
    openPositionRepository.save(position);
    var req = new com.softility.omivertex.domain.PositionSkill();
    req.setPosition(position);
    req.setSkill(java);
    req.setMinProficiency(com.softility.omivertex.domain.Proficiency.INTERMEDIATE);
    req.setRequired(true);
    positionSkillRepository.save(req);

    // supply: one on bench with Java ADVANCE, one allocated with Java MASTERY, one bench NOVICE (below min)
    var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    rateSkill(bench, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
    var busy = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
    rateSkill(busy, java, com.softility.omivertex.domain.Proficiency.MASTERY);
    allocation(busy, proj, true);
    var novice = associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);
    rateSkill(novice, java, com.softility.omivertex.domain.Proficiency.NOVICE);

    mockMvc.perform(get("/api/v1/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillGaps", hasSize(1)))
            .andExpect(jsonPath("$.skillGaps[0].skillName").value("Java"))
            .andExpect(jsonPath("$.skillGaps[0].demand").value(1))
            .andExpect(jsonPath("$.skillGaps[0].benchSupply").value(1))
            .andExpect(jsonPath("$.skillGaps[0].totalSupply").value(2))
            .andExpect(jsonPath("$.skillGaps[0].gap").value(0));
}
```
(add `@Autowired protected PositionSkillRepository positionSkillRepository;` to `ApiTestBase` and its `deleteAll()` before `openPositionRepository.deleteAll()`)

- [ ] **Step 2: Run** — FAIL (`skillGaps` missing).

- [ ] **Step 3: Implement.** In `DashboardService` (inject `PositionSkillRepository`), add record to `DashboardSummaryResponse`:
```java
    public record SkillGap(Long skillId, String skillName, String category,
                           long demand, long benchSupply, long totalSupply, long gap) {}
```
and field `List<SkillGap> skillGaps` at the end. Computation in `summary()` (after bench sets exist — reuse `activeIds`, `allocatedIds`):
```java
        static final int MAX_SKILL_GAP_ROWS = 20; // class-level constant

        Map<Long, List<PositionSkill>> demandBySkill = positionSkillRepository.findAllWithDetails().stream()
                .filter(PositionSkill::isRequired)
                .filter(ps -> ps.getPosition().getStatus() == PositionStatus.OPEN)
                .collect(Collectors.groupingBy(ps -> ps.getSkill().getId()));
        List<AssociateSkill> ratedSkills = associateSkillRepository.findAllWithDetails();
        List<DashboardSummaryResponse.SkillGap> skillGaps = demandBySkill.values().stream()
                .map(reqs -> {
                    Skill skill = reqs.get(0).getSkill();
                    Proficiency threshold = reqs.stream()
                            .map(r -> r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency())
                            .min(Comparator.comparingInt(Enum::ordinal)).orElse(Proficiency.NOVICE);
                    Set<Long> holders = ratedSkills.stream()
                            .filter(s -> s.getSkill().getId().equals(skill.getId()))
                            .filter(s -> s.getProficiency().ordinal() >= threshold.ordinal())
                            .map(s -> s.getAssociate().getId())
                            .filter(activeIds::contains)
                            .collect(Collectors.toSet());
                    long benchSupply = holders.stream().filter(idd -> !allocatedIds.contains(idd)).count();
                    return new DashboardSummaryResponse.SkillGap(skill.getId(), skill.getName(),
                            skill.getCategory().getName(), reqs.size(), benchSupply, holders.size(),
                            reqs.size() - benchSupply);
                })
                .sorted(Comparator.comparingLong(DashboardSummaryResponse.SkillGap::gap).reversed()
                        .thenComparing(DashboardSummaryResponse.SkillGap::skillName))
                .limit(MAX_SKILL_GAP_ROWS)
                .toList();
```
(needs `AssociateSkillRepository` injected too; imports for `PositionSkill`, `Skill`, `Proficiency`, `AssociateSkill`)

- [ ] **Step 4: Run** `./mvnw test` — green.
- [ ] **Step 5: Commit** `feat: skill gap analysis on dashboard summary (demand vs bench/total supply)`

### Task 9: `utilizationForecast` on /dashboard/summary

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java`, `DashboardSummaryResponse.java`
- Test: `src/test/java/com/softility/omivertex/api/DashboardApiTest.java`

- [ ] **Step 1: Failing test**

```java
@Test
void summary_forecastsUtilizationFromKnownEndDates() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var stays = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    allocation(stays, proj, true); // open-ended billable
    var rollsOff = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
    var ending = allocation(rollsOff, proj, true);
    ending.setEndDate(java.time.LocalDate.now().plusDays(45));
    allocationRepository.save(ending);

    mockMvc.perform(get("/api/v1/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.utilizationForecast", hasSize(4)))
            .andExpect(jsonPath("$.utilizationForecast[0].label").value("Today"))
            .andExpect(jsonPath("$.utilizationForecast[0].percent").value(100))
            .andExpect(jsonPath("$.utilizationForecast[1].percent").value(100))  // +30d
            .andExpect(jsonPath("$.utilizationForecast[2].percent").value(50))   // +60d
            .andExpect(jsonPath("$.utilizationForecast[3].percent").value(50));  // +90d
}
```

- [ ] **Step 2: Run** — FAIL.

- [ ] **Step 3: Implement.** Response record addition:
```java
    public record ForecastPoint(String label, long percent) {}
```
field `List<ForecastPoint> utilizationForecast`. In `DashboardService`:
```java
    static final int[] FORECAST_OFFSET_DAYS = {0, 30, 60, 90};

    private List<DashboardSummaryResponse.ForecastPoint> utilizationForecast(
            List<Associate> activeAssociates, List<Allocation> all) {
        List<DashboardSummaryResponse.ForecastPoint> points = new ArrayList<>();
        for (int offset : FORECAST_OFFSET_DAYS) {
            LocalDate at = LocalDate.now().plusDays(offset);
            List<Associate> present = activeAssociates.stream()
                    .filter(a -> a.getLastWorkingDay() == null || !a.getLastWorkingDay().isBefore(at))
                    .toList();
            Set<Long> presentIds = present.stream().map(Associate::getId).collect(Collectors.toSet());
            Map<Long, Integer> billablePct = all.stream()
                    .filter(Allocation::isBillable)
                    .filter(a -> !a.getStartDate().isAfter(at)
                            && (a.getEndDate() == null || !a.getEndDate().isBefore(at)))
                    .filter(a -> presentIds.contains(a.getAssociate().getId()))
                    .collect(Collectors.groupingBy(a -> a.getAssociate().getId(),
                            Collectors.summingInt(Allocation::getAllocationPercent)));
            double fte = billablePct.values().stream().mapToDouble(p -> Math.min(p, 100) / 100.0).sum();
            long pct = present.isEmpty() ? 0 : Math.round(fte / present.size() * 100);
            points.add(new DashboardSummaryResponse.ForecastPoint(offset == 0 ? "Today" : "+" + offset + "d", pct));
        }
        return points;
    }
```
Call it in `summary()` with the ACTIVE associate list and the full allocation list; pass into the response.

- [ ] **Step 4: Run** `./mvnw test` — green.
- [ ] **Step 5: Commit** `feat: deterministic 30/60/90-day utilization forecast on dashboard`

### Task 10: Dashboard frontend (heatmap table + forecast chart)

**Files:**
- Modify: `frontend/src/pages/Dashboard.jsx` (and `frontend/src/components/charts.jsx` only if a line-chart primitive is missing)

- [ ] **Step 1: Skill gap heatmap** — a card titled "Skill gaps (open demand vs supply)"; if `summary.skillGaps` empty show "No open skill demand." Otherwise a compact table: Skill (with category as muted subtext), Open seats, On bench, Total, Gap. Gap cell uses `Badge`-style tones: `--color-danger` when `gap > 0`, warning when `gap === 0`, success when `< 0`. Follow the existing card/table markup in `Dashboard.jsx`.

- [ ] **Step 2: Forecast chart** — card "Utilization forecast" with caption *assumes no new assignments*. Reuse the existing SVG trend/line chart from `charts.jsx` fed with `summary.utilizationForecast` (`label` on x, `percent` 0–100 on y). If only a bar/trend component exists, render points with it rather than writing a new chart primitive.

- [ ] **Step 3: Verify** `cd frontend && npm run build` green; `./mvnw test` green.
- [ ] **Step 4: Commit** `feat: dashboard skill-gap heatmap + utilization forecast chart`

---

## Feature 4 — Associate self-service

### Task 11: ASSOCIATE role + roster-linked approval

**Files:**
- Modify: `src/main/java/com/softility/omivertex/domain/Role.java` (add `ASSOCIATE`)
- Modify: `src/main/java/com/softility/omivertex/domain/AppUser.java` (add `associateId`)
- Create: `src/main/resources/db/migration/V8__self_service.sql` (first half)
- Modify: `src/main/java/com/softility/omivertex/web/AdminUserController.java`
- Test: `src/test/java/com/softility/omivertex/api/AdminAccessRequestApiTest.java`

- [ ] **Step 1: Failing tests** (append; follow the file's existing request/approve helpers)

```java
@Test
void approveAsAssociate_linksRosterRecord() throws Exception {
    var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    Long requestId = createPendingRequest("priya@softility.com"); // reuse/extract the file's existing setup helper

    mockMvc.perform(post("/api/v1/admin/access-requests/" + requestId + "/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"role":"ASSOCIATE"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ASSOCIATE"));

    var user = appUserRepository.findByEmailIgnoreCase("priya@softility.com").orElseThrow();
    org.assertj.core.api.Assertions.assertThat(user.getAssociateId()).isEqualTo(dev.getId());
}

@Test
void approveAsAssociate_withoutRosterMatch_returns400() throws Exception {
    Long requestId = createPendingRequest("stranger@softility.com");
    mockMvc.perform(post("/api/v1/admin/access-requests/" + requestId + "/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"role":"ASSOCIATE"}"""))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run** — FAIL (no ASSOCIATE constant).

- [ ] **Step 3: Implement.** `Role`: `ADMIN, VIEWER, ASSOCIATE`. `AppUser`: `private Long associateId;` + accessors (`@Column(name = "associate_id")`).

`V8__self_service.sql` (start the file; Task 13 appends the table):
```sql
-- Self-service: an ASSOCIATE-role login is linked to their roster record.
ALTER TABLE app_users ADD COLUMN associate_id bigint REFERENCES associates(id);
```

`AdminUserController.approveRequest` — inject `AssociateRepository`; after resolving the granted role:
```java
        Role granted = body != null && body.role() != null ? body.role() : Role.VIEWER;
        if (granted == Role.ASSOCIATE) {
            Associate match = associateRepository.findByEmailIgnoreCase(user.getEmail())
                    .orElseThrow(() -> new BadRequestException(
                            "No associate on the roster with email " + user.getEmail()
                            + " — add them to the roster first"));
            user.setAssociateId(match.getId());
        }
        user.setRole(granted);
```

- [ ] **Step 4: Run** `./mvnw test` — green.
- [ ] **Step 5: Commit** `feat: ASSOCIATE role — access-request approval links the roster record (V8)`

### Task 12: /me security boundary + own profile

**Files:**
- Modify: `src/main/java/com/softility/omivertex/config/SecurityConfig.java`
- Create: `src/main/java/com/softility/omivertex/web/MeController.java`
- Test: `src/test/java/com/softility/omivertex/api/SelfServiceApiTest.java` (new)

- [ ] **Step 1: Failing tests**

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SelfServiceApiTest extends ApiTestBase {

    private Associate linkedAssociate() {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var user = new AppUser();
        user.setEmail("priya@softility.com");
        user.setName("Priya Sharma");
        user.setRole(Role.ASSOCIATE);
        user.setStatus(AccessStatus.APPROVED);
        user.setAssociateId(dev.getId());
        appUserRepository.save(user);
        return dev;
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_seesOwnProfile() throws Exception {
        linkedAssociate();
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"));
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_cannotBrowseRoster() throws Exception {
        linkedAssociate();
        mockMvc.perform(get("/api/v1/associates")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run** — FAIL (404/403 mix).

- [ ] **Step 3: Implement.** `SecurityConfig` — insert **before** the blanket GET rule:
```java
                        .requestMatchers("/api/v1/me/**").hasRole("ASSOCIATE")
```

`MeController.java`:
```java
package com.softility.omivertex.web;

import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.service.AssociateService;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AppUserRepository appUsers;
    private final AssociateService associateService;

    public MeController(AppUserRepository appUsers, AssociateService associateService) {
        this.appUsers = appUsers;
        this.associateService = associateService;
    }

    @GetMapping("/profile")
    public AssociateResponse myProfile(Authentication auth) {
        return associateService.get(linkedAssociateId(auth));
    }

    Long linkedAssociateId(Authentication auth) {
        AppUser user = appUsers.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new NotFoundException("User", 0L));
        if (user.getAssociateId() == null) {
            throw new NotFoundException("Associate profile for " + auth.getName(), 0L);
        }
        return user.getAssociateId();
    }
}
```
(check `AssociateService.get(Long)` returns `AssociateResponse` — it does, it backs `GET /associates/{id}`; check `NotFoundException` constructor signature and adapt the two calls to it)

- [ ] **Step 4: Run** `./mvnw test` — green (also confirms ADMIN/VIEWER routes unaffected).
- [ ] **Step 5: Commit** `feat: /api/v1/me — ASSOCIATE-only security boundary + own profile endpoint`

### Task 13: Profile change requests (submit side)

**Files:**
- Create: `src/main/java/com/softility/omivertex/domain/ProfileChangeRequest.java`, `ProfileChangeType.java`, `ProfileChangeStatus.java`
- Create: `src/main/java/com/softility/omivertex/repository/ProfileChangeRequestRepository.java`
- Create: `src/main/java/com/softility/omivertex/service/ProfileChangeService.java`
- Create: `src/main/java/com/softility/omivertex/web/dto/ProfileChangeResponse.java`
- Modify: `src/main/java/com/softility/omivertex/web/MeController.java`
- Modify: `src/main/resources/db/migration/V8__self_service.sql` (append table)
- Test: `src/test/java/com/softility/omivertex/api/SelfServiceApiTest.java`

- [ ] **Step 1: Failing tests** (append; imports: `MockMvcRequestBuilders.post/multipart`, `MediaType`)

```java
    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_submitsSkillChange_pendingAndDuplicateBlocked() throws Exception {
        var dev = linkedAssociate();
        var java = skill("Backend", "Java");

        mockMvc.perform(post("/api/v1/me/profile-changes/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"ADVANCE","primary":true}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SKILLS"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // live profile untouched until approval
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(jsonPath("$.skillGroups", org.hamcrest.Matchers.hasSize(0)));

        // second pending SKILLS request -> 409
        mockMvc.perform(post("/api/v1/me/profile-changes/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"NOVICE","primary":false}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_submitsResumeChange() throws Exception {
        linkedAssociate();
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDFDATA".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/me/profile-changes/resume").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESUME"))
                .andExpect(jsonPath("$.resumeFilename").value("resume.pdf"));

        mockMvc.perform(get("/api/v1/me/profile-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }
```

- [ ] **Step 2: Run** — FAIL (404s).

- [ ] **Step 3: Implement.**

Enums:
```java
public enum ProfileChangeType { SKILLS, RESUME }
public enum ProfileChangeStatus { PENDING, APPROVED, REJECTED }
```

`ProfileChangeRequest.java`:
```java
package com.softility.omivertex.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "profile_change_requests")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "associate_id", nullable = false)
    private Associate associate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeStatus status = ProfileChangeStatus.PENDING;

    /** JSON payload of SkillAssignmentRequest, SKILLS requests only. */
    @Column(columnDefinition = "text")
    private String skillsPayload;

    // RESUME requests only
    private String resumeFilename;
    private String resumeContentType;
    private Long resumeByteSize;
    @Lob
    private byte[] resumeContent;

    private String note;
    private String decidedBy;
    private Instant decidedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // getters/setters for every field, same style as the other entities
}
```

Repository:
```java
public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    boolean existsByAssociateIdAndTypeAndStatus(Long associateId, ProfileChangeType type, ProfileChangeStatus status);
    List<ProfileChangeRequest> findByAssociateIdOrderByCreatedAtDesc(Long associateId);
    @Query("select r from ProfileChangeRequest r join fetch r.associate where (:status is null or r.status = :status) order by r.createdAt asc")
    List<ProfileChangeRequest> findAllByStatus(@Param("status") ProfileChangeStatus status);
}
```

`ProfileChangeResponse.java`:
```java
public record ProfileChangeResponse(
        Long id, Long associateId, String associateName,
        ProfileChangeType type, ProfileChangeStatus status,
        List<ProposedSkill> proposedSkills, String resumeFilename, Long resumeByteSize,
        String note, Instant createdAt, Instant decidedAt, String decidedBy) {

    public record ProposedSkill(String skillName, Proficiency proficiency, boolean primary) {}
}
```

`ProfileChangeService` — constructor-inject `ProfileChangeRequestRepository`, `AssociateRepository`, `SkillRepository`, `AssociateService`, `ResumeService`, `AuditService`, `com.fasterxml.jackson.databind.ObjectMapper`. Submit methods:
```java
    @Transactional
    public ProfileChangeResponse submitSkills(Long associateId, SkillAssignmentRequest request) {
        assertNoPending(associateId, ProfileChangeType.SKILLS);
        for (var entry : request.skills()) {
            skillRepository.findById(entry.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", entry.skillId()));
        }
        ProfileChangeRequest change = base(associateId, ProfileChangeType.SKILLS);
        try {
            change.setSkillsPayload(objectMapper.writeValueAsString(request));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException("Could not read the proposed skills");
        }
        return toResponse(repository.save(change));
    }

    @Transactional
    public ProfileChangeResponse submitResume(Long associateId, MultipartFile file) {
        assertNoPending(associateId, ProfileChangeType.RESUME);
        ProfileChangeRequest change = base(associateId, ProfileChangeType.RESUME);
        try {
            change.setResumeFilename(file.getOriginalFilename());
            change.setResumeContentType(file.getContentType());
            change.setResumeByteSize(file.getSize());
            change.setResumeContent(file.getBytes());
        } catch (java.io.IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
        return toResponse(repository.save(change));
    }

    private void assertNoPending(Long associateId, ProfileChangeType type) {
        if (repository.existsByAssociateIdAndTypeAndStatus(associateId, type, ProfileChangeStatus.PENDING)) {
            throw new ConflictException("A " + type.name().toLowerCase()
                    + " change is already awaiting approval — wait for the admin decision");
        }
    }
```
`base(...)` resolves the associate (404 if missing) and sets type. `toResponse(...)` parses `skillsPayload` back with the ObjectMapper and resolves skill names for `proposedSkills`.

Append to `V8__self_service.sql`:
```sql
-- An associate's proposed profile edit; live data changes only on admin approval.
CREATE TABLE profile_change_requests (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    associate_id bigint NOT NULL REFERENCES associates(id),
    type varchar(255) NOT NULL CHECK (type IN ('SKILLS','RESUME')),
    status varchar(255) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    skills_payload text,
    resume_filename varchar(255),
    resume_content_type varchar(255),
    resume_byte_size bigint,
    resume_content bytea,
    note varchar(1000),
    decided_by varchar(255),
    decided_at timestamp,
    created_at timestamp NOT NULL DEFAULT now()
);
```

`MeController` additions:
```java
    @PostMapping("/profile-changes/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileChangeResponse proposeSkills(Authentication auth,
                                               @Valid @RequestBody SkillAssignmentRequest request) {
        return profileChangeService.submitSkills(linkedAssociateId(auth), request);
    }

    @PostMapping("/profile-changes/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileChangeResponse proposeResume(Authentication auth,
                                               @RequestParam("file") MultipartFile file) {
        return profileChangeService.submitResume(linkedAssociateId(auth), file);
    }

    @GetMapping("/profile-changes")
    public List<ProfileChangeResponse> myChanges(Authentication auth) {
        return profileChangeService.listForAssociate(linkedAssociateId(auth));
    }
```
Add `ApiTestBase` cleanup: `profileChangeRequestRepository.deleteAll();` first in `cleanDatabase()` + autowire.

- [ ] **Step 4: Run** `./mvnw test` — green.
- [ ] **Step 5: Commit** `feat: associates propose skill/resume changes — pending until approved`

### Task 14: Admin queue — approve / reject

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/ProfileChangeController.java`
- Modify: `src/main/java/com/softility/omivertex/service/ProfileChangeService.java`
- Modify: `src/main/java/com/softility/omivertex/service/ResumeService.java` (byte-array overload)
- Test: `src/test/java/com/softility/omivertex/api/SelfServiceApiTest.java`

- [ ] **Step 1: Failing tests** (these run as the class-default ADMIN mock user; create the pending request by calling the service directly or via a helper that posts as the associate using `with(user("priya@softility.com").roles("ASSOCIATE"))` from `SecurityMockMvcRequestPostProcessors`)

```java
    @Test
    void admin_approvesSkillChange_appliesToProfile() throws Exception {
        var dev = linkedAssociate();
        var java = skill("Backend", "Java");
        mockMvc.perform(post("/api/v1/me/profile-changes/skills")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("priya@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"ADVANCE","primary":true}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isCreated());
        long changeId = profileChangeRequestRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/profile-changes/" + changeId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/associates/" + dev.getId()))
                .andExpect(jsonPath("$.skillGroups[0].skills[0].name").value("Java"))
                .andExpect(jsonPath("$.primarySkill").value("Java"));

        // approving twice -> 409
        mockMvc.perform(post("/api/v1/profile-changes/" + changeId + "/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_rejectsWithNote() throws Exception {
        linkedAssociate();
        var java = skill("Backend", "Java");
        mockMvc.perform(post("/api/v1/me/profile-changes/skills")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("priya@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"ADVANCE","primary":true}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isCreated());
        long changeId = profileChangeRequestRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/profile-changes/" + changeId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Please add a certification for this level"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.note").value("Please add a certification for this level"));

        mockMvc.perform(get("/api/v1/profile-changes").param("status", "PENDING"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
```

- [ ] **Step 2: Run** — FAIL (404 on admin endpoints).

- [ ] **Step 3: Implement.** `ResumeService` — extract the body of `store(Long, MultipartFile)` into:
```java
    public ResumeMetaResponse store(Long associateId, String filename, String contentType, byte[] content) { ... }
```
and have the MultipartFile version delegate (same validations, one implementation).

`ProfileChangeService` decision methods:
```java
    @Transactional
    public ProfileChangeResponse approve(Long id, String decidedBy) {
        ProfileChangeRequest change = findPending(id);
        if (change.getType() == ProfileChangeType.SKILLS) {
            try {
                SkillAssignmentRequest request = objectMapper.readValue(
                        change.getSkillsPayload(), SkillAssignmentRequest.class);
                associateService.replaceSkills(change.getAssociate().getId(), request);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new ConflictException("Stored change payload is unreadable");
            }
        } else {
            resumeService.store(change.getAssociate().getId(), change.getResumeFilename(),
                    change.getResumeContentType(), change.getResumeContent());
        }
        change.setStatus(ProfileChangeStatus.APPROVED);
        change.setDecidedBy(decidedBy);
        change.setDecidedAt(Instant.now());
        auditService.record("APPROVED", "ProfileChange", change.getId(),
                "Approved " + change.getType() + " change for " + change.getAssociate().getName());
        return toResponse(change);
    }

    @Transactional
    public ProfileChangeResponse reject(Long id, String note, String decidedBy) {
        ProfileChangeRequest change = findPending(id);
        change.setStatus(ProfileChangeStatus.REJECTED);
        change.setNote(note);
        change.setDecidedBy(decidedBy);
        change.setDecidedAt(Instant.now());
        auditService.record("REJECTED", "ProfileChange", change.getId(),
                "Rejected " + change.getType() + " change for " + change.getAssociate().getName());
        return toResponse(change);
    }

    private ProfileChangeRequest findPending(Long id) {
        ProfileChangeRequest change = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Profile change", id));
        if (change.getStatus() != ProfileChangeStatus.PENDING) {
            throw new ConflictException("This change was already decided");
        }
        return change;
    }
```

`ProfileChangeController.java`:
```java
@RestController
@RequestMapping("/api/v1/profile-changes")
public class ProfileChangeController {

    private final ProfileChangeService service;

    public ProfileChangeController(ProfileChangeService service) { this.service = service; }

    @GetMapping
    public List<ProfileChangeResponse> list(@RequestParam(required = false) ProfileChangeStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    public ProfileChangeResponse approve(@PathVariable Long id, Authentication auth) {
        return service.approve(id, auth.getName());
    }

    @PostMapping("/{id}/reject")
    public ProfileChangeResponse reject(@PathVariable Long id, Authentication auth,
                                        @RequestBody(required = false) RejectBody body) {
        return service.reject(id, body == null ? null : body.note(), auth.getName());
    }

    public record RejectBody(String note) {}
}
```
(POSTs are ADMIN-only via the existing blanket rule; GET is ADMIN+VIEWER like every other read — consistent with the app.)

- [ ] **Step 4: Run** `./mvnw test` — green.
- [ ] **Step 5: Commit** `feat: admin approval queue for profile changes — approve applies via existing services`

### Task 15: Frontend — associate "My Profile" experience

**Files:**
- Create: `frontend/src/pages/MyProfile.jsx`
- Modify: `frontend/src/App.jsx` (or wherever role-based nav/routes live — follow the existing `canEdit`/role wiring)
- Modify: `frontend/src/api.js` (add `me` helpers)

- [ ] **Step 1:** `api.js` helpers: `getMyProfile()` → `GET /me/profile`; `getMyChanges()` → `GET /me/profile-changes`; `proposeSkills(payload)` → `POST /me/profile-changes/skills`; `proposeResume(file)` → multipart `POST /me/profile-changes/resume` (mirror the existing `uploadResume` implementation).

- [ ] **Step 2:** `MyProfile.jsx` — loads profile + change list. Renders: profile header (name, designation, company, work mode), current skills via the same grouped display used on `Profile.jsx`, resume line, certifications. Then: a status banner when the newest request is PENDING ("Your skills change is awaiting admin approval") or REJECTED (show `note`). "Propose skill changes" opens a `Modal` with the shared `SkillEditor` prefilled from `skillGroups`; submit calls `proposeSkills` and reloads. "Upload new resume" file input calls `proposeResume`. Handle 409 by showing the server message via the existing toast pattern.

- [ ] **Step 3:** Routing/nav: when the signed-in user's role is `ASSOCIATE`, the nav shows only "My Profile" (and logout); all other routes redirect there. Follow how the app currently branches on role from `/auth/me`.

- [ ] **Step 4: Verify** `cd frontend && npm run build` green.
- [ ] **Step 5: Commit** `feat: associate self-service My Profile page (propose skills/resume)`

### Task 16: Frontend — admin Profile Changes queue + role option

**Files:**
- Create: `frontend/src/pages/ProfileChanges.jsx`
- Modify: nav/router registration file, `frontend/src/pages/AccessRequests.jsx`

- [ ] **Step 1:** `AccessRequests.jsx` — the approve control's role choice gains `Associate` (value `ASSOCIATE`); surface the server's 400 message (no roster match) via the existing error toast.

- [ ] **Step 2:** `ProfileChanges.jsx` — `DataTable` of pending requests: requester, type badge, submitted date, preview (proposed skills as `name — proficiency` chips, or resume `filename (size)`), Approve / Reject buttons (Reject opens a small `Modal` with a note field). Status filter tabs Pending/Approved/Rejected mirroring the queue endpoint's `?status=`. Register nav item "Profile Changes" for admins.

- [ ] **Step 3: Verify** `npm run build` + `./mvnw test` green.
- [ ] **Step 4: Commit** `feat: admin profile-changes queue UI + Associate role option on access requests`

---

## Task 17: Docs, graph, final verification

**Files:**
- Modify: `docs/TECHNICAL.md`, `docs/TODO.md`

- [ ] **Step 1:** `TECHNICAL.md`: entity table rows (Associate exit fields; PositionSkill; ProfileChangeRequest; AppUser.associateId; OpenPosition workMode/skills); REST table (`/me/**`, `/profile-changes/**`); dashboard shape (`exitsLast12Months`, `skillGaps`, `utilizationForecast`); roles section (ASSOCIATE). `TODO.md` resolved decisions: exit auto-cleanup semantics, partials-ranked-lower matching, forecast is deterministic, pending-change approval model, ASSOCIATE sees own profile only; future: manager reporting lines, notifications, money layer.
- [ ] **Step 2:** Full verification: `./mvnw test` (expect ~175+ tests, 0 failures) and `cd frontend && npm run build`.
- [ ] **Step 3:** Refresh graph: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- [ ] **Step 4: Commit** `docs: technical + decisions for exit tracking, multi-skill matching, gaps/forecast, self-service`
