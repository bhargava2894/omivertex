# Résumé Profile Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Résumé upload in the New Associate flow also extracts name, phone, and structured employment history, prefills the form as a reviewable overview, and persists history on create — shown as a "Previous employment" card on the profile.

**Architecture:** One extended Gemini call (same `extractResume`) returns the new fields; the keyword fallback leaves them empty. A V10 migration adds `associate.phone` + an `employment_history` table (entity keyed by `associateId`, like `Resume`). `AssociateRequest/Response` carry phone + history (history applied on create only; list responses omit it). The New Associate modal prefills name/phone only-when-empty, renders editable history rows, and drops rows matching the form's Company (internal Softility history stays allocation-derived — never merged).

**Tech Stack:** Spring Boot 3.5 / Java 21, Flyway (Postgres dialect, H2 in tests), JUnit 5 + AssertJ + MockMvc + Mockito, React 18.

**Spec:** `docs/superpowers/specs/2026-07-18-resume-profile-extraction-design.md`

**Rules for every task** (AGENTS.md): TDD — failing test first, right reason. Full `./mvnw test` green before every commit (Spotless + ArchUnit; `./mvnw spotless:apply` to fix). Frontend: `npm run format && npm run build`. Commits end `Co-Authored-By: <your model> <noreply@anthropic.com>`.

**Verified shapes** (do not re-derive): `ResumeService.parse` tries `aiParse(text)` when `geminiClient.isConfigured()`, else keyword path returning `new ParsedResumeResponse(suggestions, textExtracted, null, SuggestionSource.KEYWORD)`; `GeminiHttpClient.parseExtraction(String json, List<SkillOption>)` is static package-private and unit-tested in `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java`; `AssociateService.create` → `apply(associate, request)` → save → audit → skills → `get(id)`; `AssociateResponse.from(associate, allocations, ratedSkills[, resumeFilename])` is called at TWO sites in `AssociateService` (list ~line 67, get ~line 95); `Resume` entity uses a plain `Long associateId` (no @ManyToOne) — precedent for `EmploymentHistory`.

---

### Task 1: Migration V10 + `EmploymentHistory` entity + repository

**Files:**
- Create: `src/main/resources/db/migration/V10__resume_profile_fields.sql`
- Create: `src/main/java/com/softility/omivertex/domain/EmploymentHistory.java`
- Create: `src/main/java/com/softility/omivertex/repository/EmploymentHistoryRepository.java`
- Modify: `src/main/java/com/softility/omivertex/domain/Associate.java` (phone field)
- Test: `src/test/java/com/softility/omivertex/api/EmploymentHistoryRepositoryTest.java`

- [ ] **Step 1: Failing test** — create `EmploymentHistoryRepositoryTest.java`:

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.EmploymentHistory;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.repository.EmploymentHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentHistoryRepositoryTest extends ApiTestBase {

    @Autowired EmploymentHistoryRepository employmentHistoryRepository;

    @Test
    void entriesRoundTrip_orderedBySortOrder_andPhonePersists() {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        priya.setPhone("+91 98765 43210");
        associateRepository.save(priya);

        var second = new EmploymentHistory();
        second.setAssociateId(priya.getId());
        second.setCompany("Initech");
        second.setTitle("Engineer");
        second.setSortOrder(1);
        employmentHistoryRepository.save(second);
        var first = new EmploymentHistory();
        first.setAssociateId(priya.getId());
        first.setCompany("Globex");
        first.setTitle("Senior Engineer");
        first.setStartDate(LocalDate.of(2021, 3, 1));
        first.setEndDate(null); // "Present" on the résumé
        first.setSortOrder(0);
        employmentHistoryRepository.save(first);

        List<EmploymentHistory> rows =
                employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(priya.getId());
        assertThat(rows).extracting(EmploymentHistory::getCompany)
                .containsExactly("Globex", "Initech");
        assertThat(associateRepository.findById(priya.getId()).orElseThrow().getPhone())
                .isEqualTo("+91 98765 43210");
    }
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=EmploymentHistoryRepositoryTest` — COMPILATION ERROR (types missing). Right failure.

- [ ] **Step 3: Migration** — create `V10__resume_profile_fields.sql`:

```sql
-- Résumé-extracted profile fields: contact phone + previous (external) employers.
-- Internal Softility engagement history stays allocation-derived — never merged here.
ALTER TABLE associates ADD COLUMN phone varchar(32);

CREATE TABLE employment_history (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    associate_id bigint NOT NULL REFERENCES associates(id) ON DELETE CASCADE,
    company varchar(120) NOT NULL,
    title varchar(120),
    start_date date,
    end_date date,
    sort_order integer NOT NULL DEFAULT 0
);
CREATE INDEX idx_employment_history_associate ON employment_history(associate_id);
```

- [ ] **Step 4: Entity** — create `EmploymentHistory.java` (plain `associateId` like `Resume`; domain stays pure per ArchUnit):

```java
package com.softility.omivertex.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One PREVIOUS (external) employer from an associate's résumé. Internal
 * Softility engagement history is allocation-derived and never stored here.
 */
@Entity
@Table(name = "employment_history")
public class EmploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "associate_id", nullable = false)
    private Long associateId;

    @Column(nullable = false, length = 120)
    private String company;

    @Column(length = 120)
    private String title;

    private LocalDate startDate;
    private LocalDate endDate;

    /** Résumé order, 0 = topmost (most recent) as extracted. */
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public Long getAssociateId() { return associateId; }
    public void setAssociateId(Long associateId) { this.associateId = associateId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
```

- [ ] **Step 5: Repository** — create `EmploymentHistoryRepository.java`:

```java
package com.softility.omivertex.repository;

import com.softility.omivertex.domain.EmploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmploymentHistoryRepository extends JpaRepository<EmploymentHistory, Long> {

    List<EmploymentHistory> findByAssociateIdOrderBySortOrderAsc(Long associateId);

    void deleteByAssociateId(Long associateId);
}
```

- [ ] **Step 6: Associate.phone** — in `Associate.java`, next to the other simple columns add field `private String phone;` (with `@Column(length = 32)`) and accessors `getPhone()/setPhone(String)` matching the file's accessor style.

- [ ] **Step 7:** Add `employmentHistoryRepository.deleteAll();` to `ApiTestBase`'s `@BeforeEach` cleanup (before `associateRepository.deleteAll()`) with an `@Autowired protected EmploymentHistoryRepository employmentHistoryRepository;` alongside the others.

- [ ] **Step 8: Run the test** → PASS. **Step 9: Full suite** `./mvnw test` → PASS. Commit:

```bash
git add -A src/main src/test
git commit -m "Add associate phone and the employment_history table for resume-extracted employers

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 2: Extended Gemini extraction contract

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiClient.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (prompt + `parseExtraction`)
- Test: `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java`

- [ ] **Step 1: Failing tests** — add to `GeminiHttpClientTest.java` (match its existing style of calling the static `GeminiHttpClient.parseExtraction`):

```java
@Test
void parseExtraction_readsProfileAndEmployment_lenientOnDates() {
    String json = """
            {"name":"Priya Sharma","phone":"+91 98765 43210",
             "employment":[
               {"company":"Globex","title":"Senior Engineer","startDate":"2021-03-01","endDate":null},
               {"company":"Initech","title":null,"startDate":"garbage","endDate":"2020-12-01"},
               {"company":"","title":"Dropped — no company","startDate":null,"endDate":null}],
             "skills":[],"experienceSummary":"8 years across two firms."}""";

    GeminiClient.ResumeExtraction extraction =
            GeminiHttpClient.parseExtraction(json, List.of());

    assertThat(extraction.name()).isEqualTo("Priya Sharma");
    assertThat(extraction.phone()).isEqualTo("+91 98765 43210");
    assertThat(extraction.employment()).hasSize(2); // companyless row dropped
    assertThat(extraction.employment().get(0).company()).isEqualTo("Globex");
    assertThat(extraction.employment().get(0).endDate()).isNull();
    assertThat(extraction.employment().get(1).startDate()).isNull(); // "garbage" degraded to null
    assertThat(extraction.employment().get(1).endDate()).isEqualTo(java.time.LocalDate.of(2020, 12, 1));
}

@Test
void parseExtraction_missingProfileFields_areNull() {
    String json = """
            {"skills":[],"experienceSummary":"s"}""";

    GeminiClient.ResumeExtraction extraction =
            GeminiHttpClient.parseExtraction(json, List.of());

    assertThat(extraction.name()).isNull();
    assertThat(extraction.phone()).isNull();
    assertThat(extraction.employment()).isEmpty();
}
```

- [ ] **Step 2: Run** the class — COMPILATION ERROR (`name()` etc. missing). Right failure.

- [ ] **Step 3: Contract** — in `GeminiClient.java` replace the extraction records:

```java
/** One previous employer extracted from the résumé; dates may be null ("Present"/unclear). */
record Employment(String company, String title, java.time.LocalDate startDate,
                  java.time.LocalDate endDate) {}

/** Full extraction result. Profile fields are null / empty when the résumé doesn't state them. */
record ResumeExtraction(List<ExtractedSkill> skills, String experienceSummary,
                        String name, String phone, List<Employment> employment) {}
```

Update the interface javadoc on `extractResume` to mention the profile fields.

- [ ] **Step 4: Prompt** — in `GeminiHttpClient.extractResume`, replace the strict-JSON block of the prompt with:

```java
String prompt = """
        You extract structured data from a resume.
        Match skills ONLY from this taxonomy (lines are "id: name"):
        %s

        Return STRICT JSON and nothing else:
        {"name":"<candidate's full name, or null>",
         "phone":"<phone number as written, or null>",
         "employment":[{"company":"<employer name>","title":"<job title or null>",
                        "startDate":"<first-of-month ISO date like 2021-03-01, or null>",
                        "endDate":"<first-of-month ISO date, or null if current/unclear>"}],
         "skills":[{"skillId":<id>,"proficiency":"NOVICE|FOUNDATIONAL|INTERMEDIATE|FUNCTIONAL_USER|ADVANCE|MASTERY","evidence":"<short quote or phrase from the resume>"}],
         "experienceSummary":"<1-2 sentences: total years of experience and recent roles>"}
        Employment: most recent first; employers only, exclude education entries.

        Resume text:
        %s
        """.formatted(skillList, resumeText);
```

- [ ] **Step 5: `parseExtraction`** — extend the method: after the skills loop, add

```java
String name = textOrNull(root.path("name"));
String phone = textOrNull(root.path("phone"));
List<Employment> employment = new ArrayList<>();
for (JsonNode e : root.path("employment")) {
    String company = textOrNull(e.path("company"));
    if (company == null || company.isBlank()) {
        continue; // an employer without a name is noise
    }
    employment.add(new Employment(company, textOrNull(e.path("title")),
            dateOrNull(e.path("startDate")), dateOrNull(e.path("endDate"))));
}
return new ResumeExtraction(skills, summary, name, phone, employment);
```

with two small private static helpers next to it:

```java
private static String textOrNull(JsonNode node) {
    return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
}

private static java.time.LocalDate dateOrNull(JsonNode node) {
    String text = textOrNull(node);
    if (text == null) {
        return null;
    }
    try {
        return java.time.LocalDate.parse(text);
    } catch (java.time.format.DateTimeParseException e) {
        return null; // résumé dates are messy — degrade rather than fail the extraction
    }
}
```

(Adjust the existing `return new ResumeExtraction(skills, summary)` — it no longer compiles; the summary variable name in the file may differ, keep its actual name. Any other constructor call sites of `ResumeExtraction` — e.g. in tests — gain `, null, null, List.of()`.)

- [ ] **Step 6: Run the class** → PASS. **Step 7: Full suite** → PASS (fix any `ResumeExtraction` construction sites the compiler finds, mechanically, with `, null, null, List.of()`). Commit:

```bash
git add -A src/main src/test
git commit -m "Extract name, phone, and employment history in the resume Gemini call

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 3: Parse endpoint carries the profile fields

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/dto/EmploymentEntry.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java`
- Modify: `src/main/java/com/softility/omivertex/service/ResumeService.java`
- Test: `src/test/java/com/softility/omivertex/api/ResumeApiTest.java` (or the existing resume API test class — find it with `grep -rl "resumes/parse" src/test` and add there)

- [ ] **Step 1: Failing test** — add to the resume API test class (it already mocks `GeminiClient` or add `@MockBean GeminiClient geminiClient` following `AssistantApiTest`'s pattern; a minimal one-page PDF byte fixture already exists in that class — reuse its helper):

```java
@Test
void parse_carriesProfileFieldsFromExtraction() throws Exception {
    when(geminiClient.isConfigured()).thenReturn(true);
    when(geminiClient.extractResume(anyString(), anyList()))
            .thenReturn(new GeminiClient.ResumeExtraction(List.of(), "8 years experience.",
                    "Priya Sharma", "+91 98765 43210",
                    List.of(new GeminiClient.Employment("Globex", "Senior Engineer",
                            java.time.LocalDate.of(2021, 3, 1), null))));

    mockMvc.perform(multipart("/api/v1/resumes/parse").file(pdfFixture()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Priya Sharma"))
            .andExpect(jsonPath("$.phone").value("+91 98765 43210"))
            .andExpect(jsonPath("$.employmentHistory[0].company").value("Globex"))
            .andExpect(jsonPath("$.employmentHistory[0].startDate").value("2021-03-01"))
            .andExpect(jsonPath("$.employmentHistory[0].endDate").doesNotExist());
}

@Test
void parse_keywordFallback_leavesProfileFieldsEmpty() throws Exception {
    when(geminiClient.isConfigured()).thenReturn(false);

    mockMvc.perform(multipart("/api/v1/resumes/parse").file(pdfFixture()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.employmentHistory").isEmpty());
}
```

(Follow the test class's actual helper names for the multipart fixture — if the endpoint is async there, use `asyncPerform` as the class already does. Adapt mechanically; if the class asserts differently today, mirror its idiom and note it.)

- [ ] **Step 2: Run** — COMPILATION ERROR / FAIL (fields missing). Right failure.

- [ ] **Step 3: Shared DTO** — create `web/dto/EmploymentEntry.java` (used by parse response now, associate DTOs in Task 4):

```java
package com.softility.omivertex.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One previous (external) employer; dates are nullable — résumés say "Present". */
public record EmploymentEntry(
        @NotBlank(message = "Company is required") @Size(max = 120) String company,
        @Size(max = 120) String title,
        LocalDate startDate,
        LocalDate endDate) {
}
```

- [ ] **Step 4: Response DTO** — in `ResumeDtos.java`:

```java
public record ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted,
                                   String experienceSummary, SuggestionSource source,
                                   String name, String phone,
                                   List<EmploymentEntry> employmentHistory) {
}
```

- [ ] **Step 5: `ResumeService`** — keyword path becomes
`new ParsedResumeResponse(suggestions, textExtracted, null, SuggestionSource.KEYWORD, null, null, List.of())`;
`aiParse` maps the extraction:

```java
List<EmploymentEntry> history = extraction.employment().stream()
        .map(e -> new EmploymentEntry(e.company(), e.title(), e.startDate(), e.endDate()))
        .toList();
return new ParsedResumeResponse(suggestions, true, extraction.experienceSummary(),
        SuggestionSource.AI, extraction.name(), extraction.phone(), history);
```

(add the `EmploymentEntry` import; `@JsonInclude` note: if `ParsedResumeResponse` isn't NON_NULL-annotated and `$.name` serializes as `null` rather than absent, adjust the fallback test to `jsonPath("$.name").value((String) null)` — mirror what the class actually produces and keep the intent: fields empty on the keyword path.)

- [ ] **Step 6: Run the class** → PASS. **Step 7: Full suite** → PASS. Commit:

```bash
git add -A src/main src/test
git commit -m "Carry extracted name, phone, and employment history through the resume parse endpoint

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 4: Create-with-history API + read model

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateRequest.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Test: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java` (the existing associate API test class — locate with `grep -rl "associates" src/test/java/com/softility/omivertex/api | grep -i associateapi`)

- [ ] **Step 1: Failing test** — add to the associate API test class:

```java
@Test
void create_withPhoneAndEmploymentHistory_persistsAndEchoes() throws Exception {
    String body = """
            {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
             "workMode":"OFFSHORE","phone":"+91 98765 43210",
             "employmentHistory":[
               {"company":"Globex","title":"Senior Engineer","startDate":"2021-03-01","endDate":null},
               {"company":"Initech","title":"Engineer","startDate":"2018-06-01","endDate":"2021-02-01"}]}""";

    mockMvc.perform(post("/api/v1/associates")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("+91 98765 43210"))
            .andExpect(jsonPath("$.employmentHistory[0].company").value("Globex"))
            .andExpect(jsonPath("$.employmentHistory[1].company").value("Initech"));

    var saved = associateRepository.findAll().get(0);
    assertThat(employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(saved.getId()))
            .extracting(com.softility.omivertex.domain.EmploymentHistory::getCompany)
            .containsExactly("Globex", "Initech"); // résumé order kept
}

@Test
void create_withoutHistory_unchanged() throws Exception {
    String body = """
            {"name":"Rahul Verma","email":"rahul@softility.com","company":"Softility",
             "workMode":"ONSHORE"}""";

    mockMvc.perform(post("/api/v1/associates")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    assertThat(employmentHistoryRepository.count()).isZero();
}
```

(Mirror the class's existing create-test idiom — status code may be `isCreated()` there; follow it.)

- [ ] **Step 2: Run** — FAIL (unknown fields ignored / missing in response). Right failure.

- [ ] **Step 3: Request DTO** — `AssociateRequest` gains, after `designation`:

```java
@Size(max = 32) String phone,
```

(add `jakarta.validation.constraints.Size` import) and after `skills`:

```java
// Previous EXTERNAL employers from the résumé — applied on create only; never
// merged with internal allocation-derived history.
@Valid List<EmploymentEntry> employmentHistory,
```

- [ ] **Step 4: Response DTO** — `AssociateResponse` gains `String phone` (after `email`) and `List<EmploymentEntry> employmentHistory` (before `skillGroups`). Update `from(...)`: the 4-arg overload becomes a delegator adding `null` history, and a new 5-arg
`from(associate, allocations, ratedSkills, resumeFilename, List<EmploymentEntry> employmentHistory)`
maps both new fields (`associate.getPhone()`, the passed list). The list call site in `AssociateService` (~line 67) keeps the old overload (history omitted from roster rows); the `get(...)` site passes the real list.

- [ ] **Step 5: Service** — `AssociateService`: inject `EmploymentHistoryRepository employmentHistoryRepository` (constructor, matching style). In `apply(...)` add `associate.setPhone(request.phone());`. In `create(...)`, after the skills block:

```java
if (request.employmentHistory() != null) {
    int order = 0;
    for (EmploymentEntry entry : request.employmentHistory()) {
        EmploymentHistory row = new EmploymentHistory();
        row.setAssociateId(associate.getId());
        row.setCompany(entry.company());
        row.setTitle(entry.title());
        row.setStartDate(entry.startDate());
        row.setEndDate(entry.endDate());
        row.setSortOrder(order++);
        employmentHistoryRepository.save(row);
    }
}
```

and extend the create audit summary: `"Created associate " + associate.getName()` becomes
`"Created associate " + associate.getName() + (request.employmentHistory() == null || request.employmentHistory().isEmpty() ? "" : " with " + request.employmentHistory().size() + " previous employers")`.
In `get(...)`, load `List<EmploymentEntry> history = employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(id).stream().map(h -> new EmploymentEntry(h.getCompany(), h.getTitle(), h.getStartDate(), h.getEndDate())).toList();` and pass it to the 5-arg `from`. `update(...)` deliberately ignores `employmentHistory` (spec: create-only).

- [ ] **Step 6: Run the class** → PASS. **Step 7: Full suite** → PASS. Commit:

```bash
git add -A src/main src/test
git commit -m "Persist resume-extracted phone and previous employers on associate create

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 5: New Associate form — prefill + editable overview

**Files:**
- Modify: `frontend/src/pages/Associates.jsx`

(No JS test infra — guardrails are Prettier/ESLint + the Task 7 live check.)

- [ ] **Step 1: Form state.** The form state object (the `useState` holding name/email/company/…) gains `phone: ''`; add `const [extractedHistory, setExtractedHistory] = useState([]);` (rows `{company, title, startDate, endDate}`) and `const [historyNote, setHistoryNote] = useState('');` near the other resume-parse state. Reset both wherever the form/parse state is reset (modal open/close, after create).

- [ ] **Step 2: Parse handler.** In the résumé-parse success path (where `data.suggestedSkills` is consumed, ~line 166), add:

```js
if (data.name || data.phone || (data.employmentHistory || []).length > 0) {
  setForm((f) => ({
    ...f,
    // prefill only when empty — never clobber what the admin already typed
    name: f.name || data.name || f.name,
    phone: f.phone || data.phone || f.phone,
  }));
  const company = (formRef => formRef.company || 'Softility')(form);
  const external = (data.employmentHistory || []).filter(
    (e) => !e.company || e.company.trim().toLowerCase() !== company.trim().toLowerCase()
  );
  if (external.length < (data.employmentHistory || []).length) {
    setHistoryNote(`${company} entry omitted — internal history comes from allocations.`);
  }
  setExtractedHistory(external);
}
```

(Use the actual `form` variable in scope rather than the IIFE if simpler — the intent: compare against the form's current Company value, defaulting to `'Softility'`.)

- [ ] **Step 3: Phone input.** Next to the Designation field add:

```jsx
<Field label="Phone" error={errors.phone}>
  <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
</Field>
```

- [ ] **Step 4: Overview rows.** Below the résumé field (after the parse notice), render when `extractedHistory.length > 0`:

```jsx
<Field label="Previous employment (from résumé — review before saving)" full>
  {historyNote && <p className="stat-hint">{historyNote}</p>}
  <div style={{ display: 'grid', gap: '6px' }}>
    {extractedHistory.map((e, i) => (
      <div key={i} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
        <input
          style={{ flex: 2 }}
          value={e.company}
          placeholder="Company"
          onChange={(ev) =>
            setExtractedHistory((h) =>
              h.map((row, j) => (j === i ? { ...row, company: ev.target.value } : row))
            )
          }
        />
        <input
          style={{ flex: 2 }}
          value={e.title || ''}
          placeholder="Title"
          onChange={(ev) =>
            setExtractedHistory((h) =>
              h.map((row, j) => (j === i ? { ...row, title: ev.target.value } : row))
            )
          }
        />
        <input
          type="date"
          value={e.startDate || ''}
          onChange={(ev) =>
            setExtractedHistory((h) =>
              h.map((row, j) => (j === i ? { ...row, startDate: ev.target.value } : row))
            )
          }
        />
        <input
          type="date"
          value={e.endDate || ''}
          onChange={(ev) =>
            setExtractedHistory((h) =>
              h.map((row, j) => (j === i ? { ...row, endDate: ev.target.value } : row))
            )
          }
        />
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          aria-label={`Remove ${e.company}`}
          onClick={() => setExtractedHistory((h) => h.filter((_, j) => j !== i))}
        >
          <Icon name="x" size={14} />
        </button>
      </div>
    ))}
  </div>
</Field>
```

- [ ] **Step 5: Submit payload.** Where the create request body is built (the `api.create('associates', …)` call, ~line 258), add:

```js
phone: form.phone || null,
employmentHistory: extractedHistory
  .filter((e) => e.company && e.company.trim())
  .map((e) => ({
    company: e.company.trim(),
    title: e.title || null,
    startDate: e.startDate || null,
    endDate: e.endDate || null,
  })),
```

Also include `phone: form.phone || null` in the EDIT/update payload if create and edit share the body builder (phone is updatable; history is create-only — never send `employmentHistory` on update).

- [ ] **Step 6:** `cd frontend && npm run format && npm run build` → clean. Commit:

```bash
git add frontend/src/pages/Associates.jsx
git commit -m "Prefill the New Associate form from the resume and show a reviewable employment overview

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 6: Profile + MyProfile display

**Files:**
- Modify: `frontend/src/pages/Profile.jsx`
- Modify: `frontend/src/pages/MyProfile.jsx`

- [ ] **Step 1: Profile header phone.** In the header detail line (`<strong>{associate.designation}</strong> · {associate.email} · …`), append `{associate.phone && <> · {associate.phone}</>}`.

- [ ] **Step 2: Previous-employment card.** In the right-hand column (next to the Certifications/Résumé cards — mirror their card markup), add:

```jsx
{(associate.employmentHistory || []).length > 0 && (
  <div className="card" style={{ padding: '24px' }}>
    <h3 style={{ marginTop: 0 }}>Previous Employment</h3>
    <p className="stat-hint" style={{ marginTop: 0 }}>
      From the résumé — Softility engagement history lives in Allocation &amp; Engagement History.
    </p>
    <div style={{ display: 'grid', gap: '8px' }}>
      {associate.employmentHistory.map((e, i) => (
        <div key={i} style={{ fontSize: '13.5px' }}>
          <strong>{e.company}</strong>
          {e.title && <> · {e.title}</>}
          <div className="cell-sub">
            {e.startDate || '?'} – {e.endDate || 'present'}
          </div>
        </div>
      ))}
    </div>
  </div>
)}
```

- [ ] **Step 3: MyProfile phone row.** In the "My Details" grid add `<DetailRow label="Phone" value={profile.phone} />` after the Designation row (the `/me` profile response is an `AssociateResponse`, so `phone` flows automatically; verify with the running app or the MeController return type — if MyProfile's data source is a different DTO, add `phone` there following Task 4's pattern and note it).

- [ ] **Step 4:** `cd frontend && npm run format && npm run build` → clean. Commit:

```bash
git add frontend/src/pages/Profile.jsx frontend/src/pages/MyProfile.jsx
git commit -m "Show phone and the resume-extracted previous employment on the profile pages

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 7: Docs, graph, live check, plan deletion

- [ ] **Step 1: `docs/TECHNICAL.md`** — entities list gains `EmploymentHistory` (one line: résumé-extracted previous employers, create-only, never merged with allocation history); the associates endpoint rows mention `phone` + `employmentHistory` (create-only); the resumes/parse row mentions the extracted profile fields and that the keyword path leaves them empty.

- [ ] **Step 2: `docs/TODO.md`** — top of Resolved decisions:

```
- **Résumé history is structured and external-only** (2026-07-18): résumé
  parsing extracts name/phone/employment into the New Associate overview;
  entries matching the form's company (Softility) are dropped — internal
  engagement history stays allocation-derived, the two are never merged.
  Email is deliberately NOT extracted (résumés carry personal addresses;
  company emails come from EmailNaming). Follow-ups: post-create history
  editing, Mirai get_associate_detail exposure, profile re-upload overview.
```

- [ ] **Step 3: Graph** — `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

- [ ] **Step 4: Verification** — `./mvnw test` green; `cd frontend && npm run format:check && npm run build` clean.

- [ ] **Step 5: Live check** — sync `frontend/dist` → `target/classes/static`, restart the app on :8080, login as admin, and via the browser (user) or curl: `POST /api/v1/resumes/parse` with a real PDF asserting the new fields appear; `POST /api/v1/associates` with phone + history and `GET` it back. Report what was and wasn't live-verified.

- [ ] **Step 6: Commit docs, delete plan**

```bash
git add docs/TECHNICAL.md docs/TODO.md
git commit -m "Document resume profile extraction and the external-only employment history decision

Co-Authored-By: <your model> <noreply@anthropic.com>"
git rm docs/superpowers/plans/2026-07-18-resume-profile-extraction.md
git commit -m "Remove the merged resume profile extraction plan

Co-Authored-By: <your model> <noreply@anthropic.com>"
```
