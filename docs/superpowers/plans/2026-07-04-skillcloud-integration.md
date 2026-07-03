# SkillCloud Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the abandoned "Softility Skill Cloud" app (skill taxonomy, per-associate proficiencies, certifications, faceted search, skill reports, cert-expiry visibility) natively into OmiVertex.

**Architecture:** New normalized skill model (`SkillCategory` → `Skill` → `AssociateSkill` with a 6-level proficiency enum) plus a `Certification` entity, layered onto the existing Spring Boot service/controller/DTO pattern. The existing free-text `primarySkill`/`secondarySkill` fields stay (deprecated, still returned) so nothing breaks; matching prefers the structured model. UI additions follow the existing React page pattern (DataTable + Modal + Field + Badge).

**Tech Stack:** Spring Boot 3.5 / Java 21, Spring Data JPA, PostgreSQL (H2 PG-mode in tests), Apache POI (multi-sheet import), React 18 + Vite + framer-motion, existing hand-rolled SVG charts.

---

## STATUS TRACKER (update after every task — the hand-off contract)

| Task | Description | Status |
|---|---|---|
| 1 | Skill model entities + repositories | ✅ DONE |
| 2 | Taxonomy API (categories/skills CRUD) | ✅ DONE |
| 3 | Associate skills API (grouped read + replace write) | ✅ DONE |
| 4 | Certifications API (per-associate + org-wide report) | ✅ DONE |
| 5 | Multi-sheet Excel import v2 | ✅ DONE |
| 6 | Associate profile page (UI) | ✅ DONE |
| 7 | Taxonomy admin page (UI) | ✅ DONE |
| 8 | Faceted skill search (API + Associates page filters) | ✅ DONE |
| 9 | Demand matching upgrade (skill FK + min proficiency) | ⬜ PENDING |
| 10 | Skill reports API + page (stacked proficiency bars) | ⬜ PENDING |
| 11 | Cert-expiry radar on dashboard | ⬜ PENDING |
| 12 | Docs, TODO refresh, graph rebuild | ⬜ PENDING |

**Read `docs/TODO.md` for the project-wide backlog beyond this epic** (P0 auth/deploy blockers remain untouched by this plan).

### Conventions the next agent MUST follow

- **Strict TDD.** Write the test in `src/test/java/com/softility/omivertex/api/`, run it, watch it FAIL for the right reason, implement, watch it PASS. Never write production code without a red test.
- All API tests extend `ApiTestBase` (gives MockMvc, repo helpers, clean DB per test, `@WithMockUser(roles="ADMIN")`). Add new repositories to its `cleanDatabase()` **in FK-safe order** (children before parents) and as helper factories if needed.
- Run tests: `./mvnw test` (full) or `./mvnw test -Dtest=ClassName`. Build UI: `cd frontend && npm run build` (outputs to `frontend/dist`; Maven copies it at `process-resources`). Run app: `./mvnw spring-boot:run` (PostgreSQL db `omivertex` must exist).
- Commit per task with a descriptive message ending in the Co-Authored-By line used in `git log`.
- After code changes, rebuild the knowledge graph: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`.
- Frontend: pages in `frontend/src/pages/`, shared components in `frontend/src/components/`, routes registered in `App.jsx` `ROUTES` (use `adminOnly: true` for admin pages). Never hardcode colors — use CSS tokens in `styles.css`. Respect `canEdit` prop for role gating.

---

### Task 1: Skill model entities + repositories

**Files:**
- Create: `src/main/java/com/softility/omivertex/domain/Proficiency.java`
- Create: `src/main/java/com/softility/omivertex/domain/SkillCategory.java`
- Create: `src/main/java/com/softility/omivertex/domain/Skill.java`
- Create: `src/main/java/com/softility/omivertex/domain/AssociateSkill.java`
- Create: `src/main/java/com/softility/omivertex/domain/Certification.java`
- Create: `src/main/java/com/softility/omivertex/repository/SkillCategoryRepository.java`, `SkillRepository.java`, `AssociateSkillRepository.java`, `CertificationRepository.java`
- Modify: `src/test/java/com/softility/omivertex/api/ApiTestBase.java` (clean new tables, order: associateSkill+certification before associate; skill before category)

- [ ] **Step 1:** Entities are pure structure (no behavior) — scaffold them so Task 2's failing tests compile. Follow the exact pattern of `domain/Client.java` (IDENTITY id, `@CreationTimestamp Instant createdAt`, explicit getters/setters).

```java
public enum Proficiency { NOVICE, FOUNDATIONAL, INTERMEDIATE, FUNCTIONAL_USER, ADVANCE, MASTERY }
```

- `SkillCategory`: `name` (unique, not null).
- `Skill`: `name` (not null), `@ManyToOne(optional=false) SkillCategory category`; unique constraint on `(name, category_id)`.
- `AssociateSkill`: `@ManyToOne(optional=false) Associate associate`, `@ManyToOne(optional=false) Skill skill`, `@Enumerated(STRING) Proficiency proficiency` (not null); unique `(associate_id, skill_id)`.
- `Certification`: `@ManyToOne(optional=false) Associate associate`, `name` (not null), `authority`, `credentialId`, `LocalDate issuedDate`, `LocalDate expiryDate`.

Repository methods needed by later tasks (add them now):

```java
// SkillCategoryRepository
Optional<SkillCategory> findByNameIgnoreCase(String name);
boolean existsByNameIgnoreCase(String name);
// SkillRepository
Optional<Skill> findByNameIgnoreCaseAndCategoryId(String name, Long categoryId);
Optional<Skill> findByNameIgnoreCase(String name);
List<Skill> findByCategoryId(Long categoryId);
boolean existsByCategoryId(Long categoryId);
// AssociateSkillRepository
@Query("select s from AssociateSkill s join fetch s.skill sk join fetch sk.category where s.associate.id = :associateId")
List<AssociateSkill> findByAssociateId(Long associateId);
@Query("select s from AssociateSkill s join fetch s.associate join fetch s.skill sk join fetch sk.category")
List<AssociateSkill> findAllWithDetails();
void deleteByAssociateId(Long associateId);
boolean existsBySkillId(Long skillId);
// CertificationRepository
List<Certification> findByAssociateId(Long associateId);
@Query("select c from Certification c join fetch c.associate order by c.expiryDate asc nulls last")
List<Certification> findAllWithAssociate();
void deleteByAssociateId(Long associateId);
```

- [ ] **Step 2:** Add to `ApiTestBase.cleanDatabase()` (top of method): `associateSkillRepository.deleteAll(); certificationRepository.deleteAll();` then after existing deletes but **before** `clientRepository.deleteAll()`: `skillRepository.deleteAll(); skillCategoryRepository.deleteAll();`. Add `@Autowired` fields for all four.
- [ ] **Step 3:** `./mvnw test` — everything still green (68+ tests). Commit: `feat: skill taxonomy + certification entities`.

### Task 2: Taxonomy API

**Files:**
- Create: `src/test/java/com/softility/omivertex/api/TaxonomyApiTest.java`
- Create: `src/main/java/com/softility/omivertex/service/TaxonomyService.java`
- Create: `src/main/java/com/softility/omivertex/web/TaxonomyController.java`
- Create: `src/main/java/com/softility/omivertex/web/dto/TaxonomyDtos.java` (records: `CategoryRequest(String name)`, `SkillRequest(String name, Long categoryId)`, `SkillResponse(Long id, String name)`, `CategoryResponse(Long id, String name, List<SkillResponse> skills)`)

- [ ] **Step 1: failing tests.** Endpoints: `GET /api/v1/taxonomy` (categories with nested skills, alphabetical), `POST /api/v1/taxonomy/categories` (201; blank name 400; duplicate 409), `POST /api/v1/taxonomy/skills` (201; unknown category 404; duplicate-in-category 409), `DELETE /api/v1/taxonomy/skills/{id}` (204; 409 if any AssociateSkill references it), `DELETE /api/v1/taxonomy/categories/{id}` (204; 409 if it still has skills). Audit entries recorded for creates/deletes (action CREATED/DELETED, entityType "Taxonomy").
- [ ] **Step 2:** Run — RED (404s).
- [ ] **Step 3:** Implement service+controller following `ClientService`/`ClientController` exactly (`@Valid`, `ConflictException`, `NotFoundException`, `AuditService`).
- [ ] **Step 4:** `./mvnw test` GREEN. Commit: `feat: skill taxonomy API (TDD)`.

### Task 3: Associate skills API

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java` (+ `List<SkillGroup> skillGroups`; record `SkillGroup(String category, List<RatedSkill> skills)`; `RatedSkill(Long skillId, String name, Proficiency proficiency)`)
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Create: `src/main/java/com/softility/omivertex/web/dto/SkillAssignmentRequest.java` (`record SkillAssignmentRequest(@NotNull List<Entry> skills)`, `record Entry(@NotNull Long skillId, @NotNull Proficiency proficiency)`)
- Modify: `src/main/java/com/softility/omivertex/web/AssociateController.java`

- [ ] **Step 1: failing tests.** `PUT /api/v1/associates/{id}/skills` with `{"skills":[{"skillId":X,"proficiency":"INTERMEDIATE"}]}` → 200; `GET /associates/{id}` returns `skillGroups` grouped by category name with proficiency; PUT replaces the whole set (idempotent upsert — put twice, second wins); unknown skillId → 404; audit entry UPDATED/Associate "skills".
- [ ] **Step 2:** RED. **Step 3:** Implement: `AssociateService.replaceSkills(id, request)` — `deleteByAssociateId` then insert; `get()`/`list()` populate `skillGroups` from `AssociateSkillRepository` (list: one `findAllWithDetails()` grouped by associate id, NOT per-row queries). **Step 4:** GREEN. Commit: `feat: per-associate rated skills (TDD)`.

### Task 4: Certifications API

**Files:**
- Create: `src/test/java/com/softility/omivertex/api/CertificationApiTest.java`
- Create: `src/main/java/com/softility/omivertex/web/dto/CertificationDtos.java` (`CertificationRequest(@NotBlank String name, String authority, String credentialId, LocalDate issuedDate, LocalDate expiryDate)`, `CertificationResponse(Long id, Long associateId, String associateName, String name, String authority, String credentialId, LocalDate issuedDate, LocalDate expiryDate)`)
- Create: `src/main/java/com/softility/omivertex/service/CertificationService.java`
- Create: `src/main/java/com/softility/omivertex/web/CertificationController.java`

- [ ] **Step 1: failing tests.** `POST /api/v1/associates/{id}/certifications` → 201; blank name 400; unknown associate 404; `GET /api/v1/associates/{id}/certifications` → list; `DELETE /api/v1/certifications/{id}` → 204; `GET /api/v1/certifications?q=aws` → org-wide, case-insensitive match on name/authority/associate name, sorted by expiry ascending; audit entries.
- [ ] **Step 2:** RED. **Step 3:** Implement (controller has two mappings: nested under associates + flat). **Step 4:** GREEN. Commit: `feat: certifications (TDD)`.

### Task 5: Multi-sheet Excel import v2

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/DataTransferApiTest.java`
- Modify: `src/main/java/com/softility/omivertex/service/ImportService.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/ImportSummaryResponse.java` (+ `int skillsImported`, `int certificationsImported`)
- Modify: `frontend/src/components/DataTransfer.jsx` (mention new sheets in the dropzone hint; show new counters)

Workbook contract (sheet names matched case-insensitively; all optional except at least one):
- `Employees`: same columns as today's roster import (reuse existing row logic).
- `EmployeeSkills`: `EMPLOYEE NAME | CATEGORY | SKILL | PROFICIENCY` — find associate by generated email from name (same `emailFor()` helper); find-or-create category and skill; upsert AssociateSkill. Unknown proficiency string → row error. `?ignoreNovice=true` skips NOVICE rows.
- `Certifications`: `EMPLOYEE NAME | CERTIFICATE NAME | AUTHORITY | CREDENTIAL ID | ISSUED | EXPIRES` (dates via POI `DataFormatter` then `LocalDate.parse` fallback patterns `yyyy-MM-dd` and `MMM dd, yyyy`; unparseable → row error, continue).

- [ ] **Step 1: failing tests** (build workbook with POI in test, 3 sheets): counts correct; re-import idempotent (skills upserted not duplicated); `ignoreNovice=true` skips; dry-run still writes nothing; single-sheet legacy roster file still works (regression).
- [ ] **Step 2:** RED. **Step 3:** Implement inside the existing `TransactionTemplate` block so dry-run keeps working; keep per-row error collection. **Step 4:** GREEN. Commit: `feat: multi-sheet import (employees/skills/certifications)`.

### Task 6: Associate profile page (UI)

**Files:**
- Modify: `frontend/src/App.jsx` — route support for `#/associates/{id}`: in `useHashRoute` keep raw hash; resolution: `if (route.startsWith('associates/'))` render `Profile` with `id={route.split('/')[1]}` (title "Associate Profile"). Not in sidebar.
- Create: `frontend/src/pages/Profile.jsx`
- Modify: `frontend/src/pages/Associates.jsx` — associate name cell becomes a link: `<a href={'#/associates/' + r.id}>`.

Profile content (mirrors SkillCloud screens 4–5, plus what SkillCloud never had):
- Header card: name, designation, email, company, location, work-mode + billability badges, bench days.
- **Skills by category**: for each `skillGroups` entry a heading + chip per skill `SkillName · Proficiency` (Badge tone: MASTERY/ADVANCE green, INTERMEDIATE/FUNCTIONAL_USER blue, FOUNDATIONAL amber, NOVICE gray). Admin: "Manage skills" button → modal listing all taxonomy skills grouped by category, each with proficiency `<select>` (empty = not held) → PUT `/associates/{id}/skills`.
- **Certifications** table (name, authority, credential id, issued, expires — expires < 90d away gets red badge). Admin: add (modal) + delete.
- **Engagement history**: `api.list('allocations', { associateId: id })` — project, client, billable, %, start, end, Current/Ended badge.

- [ ] Implement, `npm run build`, verify in browser (login as admin, click a roster name). Commit: `feat: associate profile page`.

### Task 7: Taxonomy admin page (UI)

**Files:**
- Create: `frontend/src/pages/Taxonomy.jsx`
- Modify: `frontend/src/App.jsx` — ROUTES entry `{ path: 'taxonomy', label: 'Skill Taxonomy', icon: 'sheet', component: Taxonomy, sub: 'Skill categories and tools', adminOnly: true }` (place before Access Requests).

Layout mirrors SkillCloud screen 7: left = category cards with skill chips (chip has × to delete, admin), right = "Add category" and "Add skill (category select + name)" forms. 409/400 errors surface via the standard form-alert pattern. Viewer never sees this page (adminOnly).

- [ ] Implement, build, verify. Commit: `feat: taxonomy admin page`.

### Task 8: Faceted skill search

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java` + `web/AssociateController.java`
- Modify: `frontend/src/pages/Associates.jsx`

API: `GET /api/v1/associates?categoryId=&skillId=&minProficiency=` — filter after building responses: associate matches if any AssociateSkill is in category / is the skill / has `proficiency.ordinal() >= minProficiency.ordinal()` (when combined with skillId, the proficiency check applies to that skill).

- [ ] **Step 1: failing tests** — three associates with different skills/levels; each filter combination asserted.
- [ ] **Step 2:** RED → implement → GREEN. UI: two selects (category → dependent skill list from `/taxonomy`) + min-proficiency select in the Associates toolbar. Commit: `feat: faceted skill search`.

### Task 9: Demand matching upgrade

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/PositionApiTest.java`
- Modify: `src/main/java/com/softility/omivertex/domain/OpenPosition.java` (+ `@ManyToOne Skill requiredSkillRef` nullable, `@Enumerated(STRING) Proficiency minProficiency` nullable; keep legacy `requiredSkill` text)
- Modify: `web/dto/PositionRequest.java` / `PositionResponse.java` (+ `Long requiredSkillId`, `Proficiency minProficiency`, response also `String requiredSkillName`)
- Modify: `src/main/java/com/softility/omivertex/service/PositionService.java`
- Modify: `frontend/src/pages/Positions.jsx` (skill dropdown from taxonomy + min-proficiency select in the position form; matches modal shows candidate proficiency)

Scoring in `matches()`: structured skill present → `skillMatch = candidate has AssociateSkill for requiredSkillRef with proficiency >= (minProficiency ?? NOVICE)`; score = `(skillMatch ? 2 : 0) + (benchDays != null ? 1 : 0) + (proficiency ordinal / 10.0 as tiebreak — keep int score, add secondary sort by proficiency desc before benchDays)`. Fallback: no `requiredSkillRef` → legacy text `contains` on primary/secondary (existing behavior, keep its tests green). `MatchCandidateResponse` + `Proficiency matchedProficiency` (nullable).

- [ ] **Step 1: failing tests** — candidate with MASTERY ranks above INTERMEDIATE; below-min-proficiency candidate has `skillMatch=false`; legacy text position still matches (regression).
- [ ] **Step 2:** RED → implement → GREEN. Commit: `feat: proficiency-aware demand matching`.

### Task 10: Skill reports

**Files:**
- Create: `src/test/java/com/softility/omivertex/api/SkillReportApiTest.java`
- Create: `src/main/java/com/softility/omivertex/web/SkillReportController.java` (service logic small enough to live here or in TaxonomyService — follow DashboardController pattern with a dedicated method in a service)
- Create: `frontend/src/pages/SkillReports.jsx`
- Modify: `frontend/src/App.jsx` (ROUTES: `{ path: 'skill-reports', label: 'Skill Reports', icon: 'activity', component: SkillReports, sub: 'Proficiency distribution by category' }` — visible to all roles)

API: `GET /api/v1/reports/skills` →
```json
[{"category":"CI/CD","skills":[{"skill":"GitHub","counts":{"NOVICE":0,"FOUNDATIONAL":2,"INTERMEDIATE":1,"FUNCTIONAL_USER":0,"ADVANCE":0,"MASTERY":1}}]}]
```
Computed from `AssociateSkillRepository.findAllWithDetails()` grouped in memory. UI: one panel per category, stacked columns per skill using the existing `VBarChart`-style rendering — simplest correct approach: reuse `StackedBar` per skill row (label = skill, segments = 6 proficiencies with counts > 0, colors `--chart-1..5` + gray for NOVICE). Legend once per panel.

- [ ] TDD as usual. Commit: `feat: skill reports`.

### Task 11: Cert-expiry radar on dashboard

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/DashboardApiTest.java`
- Modify: `web/dto/DashboardSummaryResponse.java` (+ `List<ExpiringCert> expiringCertifications`; record `ExpiringCert(Long certificationId, Long associateId, String associateName, String name, LocalDate expiryDate, long daysLeft)`)
- Modify: `service/DashboardService.java` (certs with expiry within 90 days, soonest first)
- Modify: `frontend/src/pages/Dashboard.jsx` (panel next to Roll-off Radar, same `radar-row` styling; red badge ≤30d, amber ≤60d, blue otherwise)

- [ ] **Step 1: failing test** — cert expiring in 20 days appears with `daysLeft: 20`; cert expiring in 200 days absent. RED → implement → GREEN. Commit: `feat: certification expiry radar`.

### Task 12: Docs, TODO, graph

- [ ] Update `docs/TECHNICAL.md` (data model table + API table: taxonomy, skills, certifications, reports endpoints; import v2 sheets) and `docs/FUNCTIONAL_OVERVIEW.md` (skills & certifications tour paragraph + FAQ line).
- [ ] Update `docs/TODO.md`: mark this epic done, note follow-ups (export columns for skills/certs, seed data for taxonomy).
- [ ] `./mvnw test` full suite green; rebuild graph; final commit.

## Self-review notes

- Spec coverage: all 10 SkillCloud screens map to tasks (home KPIs → existing dashboard + Task 10/11; profiles → T6; edit skills → T6 modal; search → T8; taxonomy → T2/T7; reports → T10; import → T5; cert central → T4 + T11). Legacy `primarySkill` fields intentionally retained — removal is a later cleanup, listed in TODO.
- Type consistency: `Proficiency` enum name `FUNCTIONAL_USER` (JSON `"FUNCTIONAL_USER"`) used everywhere; `skillGroups` naming shared between T3 (API) and T6 (UI).
- No placeholder steps: boilerplate CRUD points at exact exemplar files that exist in-repo (`ClientService`, `ClientController`), with fields and signatures specified here.
