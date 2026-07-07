# Unify Associate Skills Entry — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Add/Edit Associate "Primary/Secondary skill" dropdowns with the structured skills editor plus a "primary" star, deriving the legacy headline fields automatically.

**Architecture:** Add a `primary` flag to `AssociateSkill`; carry skills (with the flag) in `AssociateRequest`; derive `Associate.primarySkill`/`secondarySkill` in the service on every skill change; extract one shared `SkillEditor` React component used by both the Profile and the Add/Edit modal.

**Tech Stack:** Spring Boot 3.5 / JPA / Postgres+Flyway (prod), H2 (test); React 18 + Vite. TDD via MockMvc HTTP tests.

**Spec:** `docs/superpowers/specs/2026-07-07-unify-associate-skills-entry-design.md`

---

## File Structure

- `domain/AssociateSkill.java` — add `boolean primary` (column `is_primary`).
- `db/migration/V2__add_primary_skill_flag.sql` — prod column add.
- `web/dto/SkillAssignmentRequest.java` — add `boolean primary` to `Entry`.
- `web/dto/AssociateRequest.java` — drop `primarySkill`/`secondarySkill`; add `List<SkillAssignmentRequest.Entry> skills`.
- `web/dto/AssociateResponse.java` — add `primary` to `RatedSkill`; pass through in `groupSkills`.
- `service/AssociateService.java` — single-primary validation, skill persistence in create/update, headline derivation; drop old `validateSkills`.
- `frontend/src/components/SkillEditor.jsx` — extracted shared editor (new).
- `frontend/src/pages/Profile.jsx` — use `SkillEditor`.
- `frontend/src/pages/Associates.jsx` — use `SkillEditor` in the modal; remove dropdowns; submit `skills`.
- Tests: `AssociateSkillDerivationTest.java` (new, service), `AssociateApiTest.java` (existing, update + add).

---

## Task 1: Add the `primary` flag to the skill model + Flyway migration

**Files:**
- Modify: `src/main/java/com/softility/omivertex/domain/AssociateSkill.java`
- Create: `src/main/resources/db/migration/V2__add_primary_skill_flag.sql`

- [ ] **Step 1: Add the field + accessors** to `AssociateSkill` after `proficiency`:

```java
    // Exactly one of an associate's skills may be primary (the roster headline);
    // enforced in AssociateService. Column is `is_primary` to dodge the SQL reserved word.
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;
```
and accessors:
```java
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
```

- [ ] **Step 2: Write the prod migration** `V2__add_primary_skill_flag.sql`:

```sql
-- Marks one of an associate's rated skills as their primary (headline) skill.
ALTER TABLE associate_skills ADD COLUMN is_primary boolean NOT NULL DEFAULT false;
```

- [ ] **Step 3: Compile** — `./mvnw -q test-compile`. Expected: success (dev/H2 add the column via auto-DDL; no test asserts behavior yet).

- [ ] **Step 4: Commit** — `git add -A && git commit` ("db: add is_primary flag to associate_skills (+V2 migration)").

---

## Task 2: Derive the headline fields when skills change (`replaceSkills`)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/SkillAssignmentRequest.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Create: `src/test/java/com/softility/omivertex/service/AssociateSkillDerivationTest.java`

- [ ] **Step 1: Add `primary` to the request entry** in `SkillAssignmentRequest.Entry`:

```java
    public record Entry(
            @NotNull(message = "Skill is required") Long skillId,
            @NotNull(message = "Proficiency is required") Proficiency proficiency,
            boolean primary) {
    }
```

- [ ] **Step 2: Write the failing test.** `AssociateSkillDerivationTest` (extends `ApiTestBase` for repositories + a seeded skill taxonomy; create an associate, call the service). Assert derivation:

```java
@Test
void starredSkill_becomesPrimary_andNextHighestIsSecondary() {
    Associate a = newAssociate("deriv@softility.com");
    Long react = skillIdByName("React"), node = skillIdByName("Node.js");
    // React starred but lower proficiency; Node higher proficiency, not starred
    associateService.replaceSkills(a.getId(), new SkillAssignmentRequest(List.of(
            new SkillAssignmentRequest.Entry(react, Proficiency.INTERMEDIATE, true),
            new SkillAssignmentRequest.Entry(node, Proficiency.ADVANCE, false))));
    Associate saved = associateRepository.findById(a.getId()).orElseThrow();
    assertEquals("React", saved.getPrimarySkill());       // star wins over proficiency
    assertEquals("Node.js", saved.getSecondarySkill());   // next skill
}

@Test
void noStar_primaryIsHighestProficiency() {
    Associate a = newAssociate("nostar@softility.com");
    associateService.replaceSkills(a.getId(), new SkillAssignmentRequest(List.of(
            new SkillAssignmentRequest.Entry(skillIdByName("React"), Proficiency.INTERMEDIATE, false),
            new SkillAssignmentRequest.Entry(skillIdByName("Node.js"), Proficiency.MASTERY, false))));
    Associate saved = associateRepository.findById(a.getId()).orElseThrow();
    assertEquals("Node.js", saved.getPrimarySkill());
    assertEquals("React", saved.getSecondarySkill());
}

@Test
void twoPrimaries_rejected() {
    Associate a = newAssociate("two@softility.com");
    var req = new SkillAssignmentRequest(List.of(
            new SkillAssignmentRequest.Entry(skillIdByName("React"), Proficiency.ADVANCE, true),
            new SkillAssignmentRequest.Entry(skillIdByName("Node.js"), Proficiency.ADVANCE, true)));
    assertThrows(BadRequestException.class, () -> associateService.replaceSkills(a.getId(), req));
}
```

(Helpers `newAssociate`, `skillIdByName` created in the test from `associateRepository`/`skillRepository`. Confirm seeded skill names exist via `SeedDataLoader`; use two that do, adjusting names if needed.)

- [ ] **Step 2b: Run it — verify it FAILS** (`./mvnw test -Dtest=AssociateSkillDerivationTest`). Expected: assertion failures / no derivation.

- [ ] **Step 3: Implement in `AssociateService`.** In `replaceSkills`, set the flag and derive after persisting; add helpers; add single-primary validation at the top:

```java
long primaries = request.skills().stream().filter(SkillAssignmentRequest.Entry::primary).count();
if (primaries > 1) {
    throw new BadRequestException("Only one skill can be marked primary.");
}
```
When building each `AssociateSkill`: `rated.setPrimary(entry.primary());`
After the loop, before `auditService.record`: `deriveHeadline(associate);`

Add:
```java
/** Recomputes the associate's headline strings from their rated skills. */
private void deriveHeadline(Associate associate) {
    List<AssociateSkill> skills = associateSkillRepository.findByAssociateId(associate.getId());
    AssociateSkill primary = skills.stream().filter(AssociateSkill::isPrimary).findFirst()
            .orElseGet(() -> skills.stream()
                    .max(Comparator.comparingInt(s -> s.getProficiency().ordinal())).orElse(null));
    AssociateSkill secondary = skills.stream()
            .filter(s -> primary == null || !s.getId().equals(primary.getId()))
            .max(Comparator.comparingInt(s -> s.getProficiency().ordinal())).orElse(null);
    associate.setPrimarySkill(primary == null ? null : primary.getSkill().getName());
    associate.setSecondarySkill(secondary == null ? null : secondary.getSkill().getName());
    associateRepository.save(associate);
}
```
Add `import java.util.Comparator;`.

- [ ] **Step 4: Run — verify PASS** (`./mvnw test -Dtest=AssociateSkillDerivationTest`).

- [ ] **Step 5: Commit** ("feat: derive primary/secondary headline from starred skills").

---

## Task 3: Carry skills in `AssociateRequest`; persist on create/update

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateRequest.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Modify: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java`

- [ ] **Step 1: Change the request record.** Drop `primarySkill`/`secondarySkill`; add optional skills:

```java
public record AssociateRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
        @NotBlank(message = "Company is required") String company,
        String location,
        @NotNull(message = "Work mode is required") WorkMode workMode,
        String designation,
        @jakarta.validation.Valid java.util.List<SkillAssignmentRequest.Entry> skills,
        EntityStatus status) {
}
```

- [ ] **Step 2: Write the failing API test** in `AssociateApiTest` (create with skills + a primary, then GET):

```java
@Test
void create_withSkillsAndPrimary_derivesHeadlineAndFlagsPrimary() throws Exception {
    Long react = skillIdByName("React");
    String body = """
        {"name":"Sk Person","email":"skp@softility.com","company":"Softility",
         "workMode":"ONSHORE",
         "skills":[{"skillId":%d,"proficiency":"ADVANCE","primary":true}]}""".formatted(react);
    String location = mockMvc.perform(post("/api/v1/associates").with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
    mockMvc.perform(get(location).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primarySkill").value("React"))
            .andExpect(jsonPath("$.skillGroups[0].skills[0].primary").value(true));
}
```
(Match the existing auth helper in `AssociateApiTest` — if it uses `@WithMockUser`/session login rather than `.with(admin())`, mirror that. Confirm the create endpoint returns `Location`; if it returns the body instead, assert on the returned JSON directly.)

- [ ] **Step 2b: Run — verify FAILS** (compile error on old fields / missing `skills`). Fix any *existing* `AssociateApiTest` cases that still send `primarySkill`/`secondarySkill` by moving them to the `skills` array.

- [ ] **Step 3: Implement create/update.** Remove `validateSkills(request)` calls and the method. In `apply`, delete the two `setPrimarySkill/SecondarySkill` lines. After saving the associate in `create` and `update`, persist skills + derive:

```java
if (request.skills() != null) {
    replaceSkills(associate.getId(),
            new SkillAssignmentRequest(request.skills()));
}
```
In `create`, this must run after `associateRepository.save(associate)` (needs the id) and before building the response; then return `get(associate.getId())` instead of the empty-lists `from(...)`.

- [ ] **Step 4: Run the full suite** (`./mvnw test`). Fix fallout; expected: all green.

- [ ] **Step 5: Commit** ("feat: accept rated skills on associate create/update").

---

## Task 4: Expose the `primary` flag in the response

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java`

- [ ] **Step 1: Add `primary` to `RatedSkill`** and pass it through `groupSkills`:

```java
public record RatedSkill(Long skillId, String name, Proficiency proficiency, boolean primary) {
}
```
In `groupSkills`, the `.add(new RatedSkill(...))` becomes:
```java
.add(new RatedSkill(s.getSkill().getId(), s.getSkill().getName(),
        s.getProficiency(), s.isPrimary()));
```

- [ ] **Step 2: Run the suite** (`./mvnw test`) — the Task 3 test's `skillGroups[0].skills[0].primary` now passes. Expected: all green.

- [ ] **Step 3: Commit** ("feat: expose primary flag on rated skills in response").

---

## Task 5: Extract a shared `SkillEditor` component

**Files:**
- Create: `frontend/src/components/SkillEditor.jsx`
- Modify: `frontend/src/pages/Profile.jsx`

- [ ] **Step 1: Read** `frontend/src/pages/Profile.jsx` (the "Manage Skills" modal body + `selectedSkills` state, ~lines 27–95 and its JSX ~200–300) to see the exact markup to extract.

- [ ] **Step 2: Create `SkillEditor.jsx`** — a controlled component. Props: `taxonomy`, `value` (`{ skillId: {proficiency, primary} }` or an array), `onChange`. It renders the category/skill/proficiency rows plus a **star** toggle per selected skill; starring one clears the others (single primary). Move the row markup out of Profile verbatim, adding the star column.

- [ ] **Step 3: Use it in Profile.** Replace the inline editor markup with `<SkillEditor taxonomy={taxonomy} value={selectedSkills} onChange={setSelectedSkills} />`; adapt `selectedSkills` shape to include `primary`; include `primary` in the `replaceSkills` payload built in `handleSaveSkills`.

- [ ] **Step 4: Build the frontend** (`cd frontend && npm run build`). Expected: 0 errors (pre-existing warnings OK). Manually confirm nothing references removed markup.

- [ ] **Step 5: Commit** ("refactor: extract shared SkillEditor from Profile").

---

## Task 6: Use `SkillEditor` in the Add/Edit modal; remove dropdowns

**Files:**
- Modify: `frontend/src/pages/Associates.jsx`

- [ ] **Step 1: Remove** the "Primary skill" and "Secondary skill" `<Field>`/`<select>` blocks (currently ~lines 361–386) and the `primarySkill`/`secondarySkill` entries in the form's initial/`row`-mapping state (~lines 19–20, 150–151).

- [ ] **Step 2: Add skills state + editor.** Add `skills` to the editing form state (shape `{ skillId: {proficiency, primary} }`, seeded from `row.skillGroups` on edit). Render `<SkillEditor taxonomy={taxonomy} value={editing.form.skills} onChange={(v) => set('skills', v)} />` inside the modal.

- [ ] **Step 3: Submit skills.** Where the form POSTs/PUTs the associate, include `skills: Object.entries(form.skills).map(([skillId, v]) => ({ skillId: Number(skillId), proficiency: v.proficiency, primary: !!v.primary }))`. Remove `primarySkill`/`secondarySkill` from the payload.

- [ ] **Step 4: Build the frontend** (`npm run build`). Expected: 0 errors.

- [ ] **Step 5: Commit** ("feat: unified skills editor in the associate add/edit form").

---

## Task 7: End-to-end verification

- [ ] **Step 1: Full backend suite** (`./mvnw test`) — all green.
- [ ] **Step 2: Rebuild frontend + restart the running app** (kill the process on :8080, `nohup ./mvnw -q spring-boot:run` with `OMIVERTEX_AUTH_GOOGLE_CLIENT_ID` exported so Google stays wired).
- [ ] **Step 3: Manual smoke:** add an associate with two skills, star one → roster shows the starred skill as the headline; reopen edit → the star and proficiencies persist.
- [ ] **Step 4: Rebuild the graph** (`_rebuild_code`).
- [ ] **Step 5: Update `docs/TODO.md`** — note primary/secondary are now derived-only (UI no longer edits them).
- [ ] **Step 6: Commit** ("docs: skills entry unified; rebuild graph").
