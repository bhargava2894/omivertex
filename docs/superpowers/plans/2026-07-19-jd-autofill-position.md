# JD-Autofill Open Position Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a resource manager upload a Job Description (PDF/DOCX) in the Open/Edit Position modal and have Mirai (Gemini) prefill the form — title, skills (matched to the taxonomy), JD text, work mode, allocation %, dates — and suggest a matching existing project. Skills not in the taxonomy are surfaced and preserved as text, never dropped.

**Architecture:** One new stateless endpoint `POST /api/v1/positions/parse-jd` mirrors the résumé-parse pipeline: `ResumeTextExtractor` → `GeminiClient.extractJobDescription(...)` (with keyword fallback) → `ParsedJobDescriptionResponse`. It persists nothing; the manager reviews the prefilled form and saves through the unchanged create/update path. Runs on the existing `AiExecutor` bulkhead.

**Tech Stack:** Spring Boot 3.5 / Java 21, JUnit + MockMvc + `@MockBean`, PDFBox (test PDFs), React 18 (Vite), `fetch` multipart.

**Spec:** `docs/superpowers/specs/2026-07-19-jd-autofill-position-design.md`

---

## File Structure

**Backend (create):**
- `src/main/java/com/softility/omivertex/service/UploadedDocuments.java` — shared PDF/DOCX file-type guard (one source of truth).
- `src/main/java/com/softility/omivertex/web/dto/PositionJdDtos.java` — `ParsedJobDescriptionResponse` + `JdSuggestedSkill` records.
- `src/test/java/com/softility/omivertex/service/UploadedDocumentsTest.java`
- `src/test/java/com/softility/omivertex/service/ProjectNameMatchTest.java`
- `src/test/java/com/softility/omivertex/service/GeminiJdParseTest.java`
- `src/test/java/com/softility/omivertex/api/PositionJdParseApiTest.java`

**Backend (modify):**
- `service/GeminiClient.java` — add `extractJobDescription` + records `ProjectOption`, `JobDescriptionExtraction`.
- `service/GeminiHttpClient.java` — implement `extractJobDescription` + static `parseJobDescription`.
- `service/ResumeService.java` — delegate its file-type check to `UploadedDocuments`.
- `service/PositionService.java` — add `parseJobDescription(...)`, deps, `matchProjectId(...)`.
- `web/PositionController.java` — inject `AiExecutor`, add `/parse-jd` endpoint.

**Frontend (modify):**
- `frontend/src/api.js` — add `parseJd(file)`.
- `frontend/src/pages/Positions.jsx` — upload button + prefill + unmatched-skills note.

**Docs (modify):**
- `docs/TECHNICAL.md` — document the new endpoint + contract.

---

## Task 1: Shared PDF/DOCX file-type guard

Extract the duplicated file-type validation into one place so `PositionService` and `ResumeService` share it (AGENTS.md: one implementation per cross-cutting rule).

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/UploadedDocuments.java`
- Create: `src/test/java/com/softility/omivertex/service/UploadedDocumentsTest.java`
- Modify: `src/main/java/com/softility/omivertex/service/ResumeService.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/service/UploadedDocumentsTest.java`:

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadedDocumentsTest {

    @Test
    void acceptsPdfAndDocx() {
        assertDoesNotThrow(() -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.pdf", "application/pdf", new byte[]{1})));
        assertDoesNotThrow(() -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[]{1})));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(BadRequestException.class, () -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.pdf", "application/pdf", new byte[]{})));
    }

    @Test
    void rejectsOtherTypes() {
        assertThrows(BadRequestException.class, () -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1})));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=UploadedDocumentsTest`
Expected: FAIL to compile — `UploadedDocuments` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/softility/omivertex/service/UploadedDocuments.java`:

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

/** One place for the "uploaded document must be a PDF or Word .docx" rule. */
public final class UploadedDocuments {

    private UploadedDocuments() {
    }

    public static void requirePdfOrDocx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        boolean isPdf = contentType.equals("application/pdf") || filename.endsWith(".pdf");
        boolean isDocx = contentType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || filename.endsWith(".docx");

        if (!isPdf && !isDocx) {
            throw new BadRequestException(
                    "Unsupported file type. Only PDF (.pdf) and Word (.docx) documents are allowed.");
        }
    }
}
```

- [ ] **Step 4: Point `ResumeService` at the shared guard**

In `src/main/java/com/softility/omivertex/service/ResumeService.java`, replace the body of the private `validateFileType(MultipartFile file)` method (currently lines ~172-194) with a single delegating call, and delete the now-unused local logic:

```java
    private void validateFileType(MultipartFile file) {
        UploadedDocuments.requirePdfOrDocx(file);
    }
```

(Leave the two call sites `validateFileType(file)` in `parse(...)` and `store(...)` unchanged.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=UploadedDocumentsTest,ResumeApiTest`
Expected: PASS — new guard tests green, all existing résumé tests still green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/UploadedDocuments.java \
        src/test/java/com/softility/omivertex/service/UploadedDocumentsTest.java \
        src/main/java/com/softility/omivertex/service/ResumeService.java
git commit -m "Extract shared PDF/DOCX upload guard (UploadedDocuments)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: GeminiClient contract for JD extraction

Add the interface method and records, then implement them in `GeminiHttpClient` mirroring `extractResume`. A static `parseJobDescription` maps the model JSON to the contract and is unit-tested directly.

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiClient.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`
- Create: `src/test/java/com/softility/omivertex/service/GeminiJdParseTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/service/GeminiJdParseTest.java`:

```java
package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiJdParseTest {

    private static final List<GeminiClient.SkillOption> TAXONOMY = List.of(
            new GeminiClient.SkillOption(10L, "Java"),
            new GeminiClient.SkillOption(20L, "AWS"));

    @Test
    void mapsFieldsAndKeepsUnmatchedSkills() {
        String json = """
                {"title":"Senior Java Developer",
                 "skills":[{"skillId":10,"proficiency":"ADVANCE"},{"skillId":999,"proficiency":"NOVICE"}],
                 "unmatchedSkills":["Rust","HTMX"],
                 "jobDescription":"Build backend services.",
                 "workMode":"ONSHORE","allocationPercent":80,
                 "startDate":"2026-03-01","endDate":null,
                 "projectName":"Acme Corp · Storefront Revamp"}""";

        GeminiClient.JobDescriptionExtraction ext =
                GeminiHttpClient.parseJobDescription(json, TAXONOMY);

        assertEquals("Senior Java Developer", ext.title());
        assertEquals(1, ext.skills().size());               // skillId 999 not in taxonomy -> dropped
        assertEquals(10L, ext.skills().get(0).skillId());
        assertEquals(Proficiency.ADVANCE, ext.skills().get(0).proficiency());
        assertEquals(List.of("Rust", "HTMX"), ext.unmatchedSkills());
        assertEquals("Build backend services.", ext.jobDescriptionText());
        assertEquals(WorkMode.ONSHORE, ext.workMode());
        assertEquals(80, ext.allocationPercent());
        assertEquals(LocalDate.of(2026, 3, 1), ext.startDate());
        assertNull(ext.endDate());
        assertEquals("Acme Corp · Storefront Revamp", ext.suggestedProjectName());
    }

    @Test
    void degradesBadEnumsAndOutOfRangeAllocation() {
        String json = """
                {"title":null,"skills":[],"unmatchedSkills":[],
                 "jobDescription":null,"workMode":"HYBRID","allocationPercent":250,
                 "startDate":null,"endDate":null,"projectName":null}""";

        GeminiClient.JobDescriptionExtraction ext =
                GeminiHttpClient.parseJobDescription(json, TAXONOMY);

        assertNull(ext.title());
        assertNull(ext.workMode());          // unknown enum -> null
        assertNull(ext.allocationPercent()); // 250 out of 1..100 -> null
        assertTrue(ext.skills().isEmpty());
        assertTrue(ext.unmatchedSkills().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=GeminiJdParseTest`
Expected: FAIL to compile — the method/records don't exist yet.

- [ ] **Step 3: Add the interface method and records**

In `src/main/java/com/softility/omivertex/service/GeminiClient.java`, add the import and members. Add near the other imports:

```java
import com.softility.omivertex.domain.WorkMode;
```

Add the method declaration after `extractResume(...)`:

```java
    /**
     * Structured extraction from a job description: a role title, skills matched
     * against the supplied taxonomy, skill names NOT in the taxonomy (raw, never
     * dropped), a cleaned description, work mode / allocation / dates when stated,
     * and the project/client name read from the JD. Throws on upstream/parse
     * failure (callers fall back).
     */
    JobDescriptionExtraction extractJobDescription(
            String jdText, List<SkillOption> taxonomy, List<ProjectOption> projects);
```

Add the records next to the existing ones (after `ResumeExtraction`):

```java
    /** One existing project offered to the model for name alignment. */
    record ProjectOption(Long id, String label) {}

    /** Full JD extraction. Any field is null when the JD does not state it. */
    record JobDescriptionExtraction(String title, List<ExtractedSkill> skills,
                                    List<String> unmatchedSkills, String jobDescriptionText,
                                    WorkMode workMode, Integer allocationPercent,
                                    java.time.LocalDate startDate, java.time.LocalDate endDate,
                                    String suggestedProjectName) {}
```

- [ ] **Step 4: Implement in `GeminiHttpClient`**

In `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`, add the import (after the existing `import com.softility.omivertex.domain.Proficiency;`):

```java
import com.softility.omivertex.domain.WorkMode;
```

Add the method (place it right after `extractResume(...)`, before the static `parseExtraction`):

```java
    @Override
    public JobDescriptionExtraction extractJobDescription(String jdText, List<SkillOption> taxonomy,
                                                          List<ProjectOption> projects) {
        if (apiKey.isEmpty()) {
            throw new BadRequestException("AI job-description parsing is not configured — "
                    + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
        }
        String skillList = taxonomy.stream()
                .map(o -> o.skillId() + ": " + o.name())
                .collect(Collectors.joining("\n"));
        String projectList = projects.stream()
                .map(ProjectOption::label)
                .collect(Collectors.joining("\n"));
        String prompt = """
                You extract structured data from a job description (JD).
                Match required skills ONLY from this taxonomy (lines are "id: name"):
                %s

                Known projects (one per line, "Client · Project"):
                %s

                Return STRICT JSON and nothing else:
                {"title":"<role/position title, or null>",
                 "skills":[{"skillId":<id>,"proficiency":"NOVICE|FOUNDATIONAL|INTERMEDIATE|FUNCTIONAL_USER|ADVANCE|MASTERY"}],
                 "unmatchedSkills":["<skill named in the JD that is NOT in the taxonomy above>"],
                 "jobDescription":"<cleaned 2-4 sentence summary of the responsibilities>",
                 "workMode":"ONSHORE|OFFSHORE|null",
                 "allocationPercent":<integer 1-100 or null>,
                 "startDate":"<ISO date like 2026-03-01, or null>",
                 "endDate":"<ISO date, or null>",
                 "projectName":"<the client/project this JD is for; copy from the known list if it matches, else as written, or null>"}
                Do not invent skills: anything named in the JD but not in the taxonomy goes in unmatchedSkills.

                Job description text:
                %s
                """.formatted(skillList, projectList, jdText);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = rest.post()
                    .uri(endpoint)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseJobDescription(extractText(response), taxonomy);
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Gemini JD extraction returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("AI job-description parsing is unavailable right now (upstream "
                    + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.warn("Gemini JD extraction call failed", e);
            throw new BadRequestException("AI job-description parsing is unavailable right now");
        }
    }

    /** Maps the model's JD JSON to the contract; unknown skill ids and bad enums degrade rather than fail. */
    static JobDescriptionExtraction parseJobDescription(String json, List<SkillOption> taxonomy) {
        Set<Long> validIds = taxonomy.stream().map(SkillOption::skillId).collect(Collectors.toSet());
        try {
            JsonNode root = MAPPER.readTree(json);
            List<ExtractedSkill> skills = new ArrayList<>();
            for (JsonNode s : root.path("skills")) {
                long id = s.path("skillId").asLong(-1);
                if (!validIds.contains(id)) {
                    continue;
                }
                Proficiency proficiency;
                try {
                    proficiency = Proficiency.valueOf(s.path("proficiency").asText(""));
                } catch (IllegalArgumentException e) {
                    proficiency = Proficiency.INTERMEDIATE;
                }
                skills.add(new ExtractedSkill(id, proficiency, ""));
            }
            List<String> unmatched = new ArrayList<>();
            for (JsonNode u : root.path("unmatchedSkills")) {
                String name = textOrNull(u);
                if (name != null) {
                    unmatched.add(name);
                }
            }
            WorkMode workMode;
            try {
                workMode = WorkMode.valueOf(root.path("workMode").asText(""));
            } catch (IllegalArgumentException e) {
                workMode = null;
            }
            Integer allocation = root.path("allocationPercent").isIntegralNumber()
                    ? root.path("allocationPercent").asInt() : null;
            if (allocation != null && (allocation < 1 || allocation > 100)) {
                allocation = null;
            }
            return new JobDescriptionExtraction(
                    textOrNull(root.path("title")), List.copyOf(skills), List.copyOf(unmatched),
                    textOrNull(root.path("jobDescription")), workMode, allocation,
                    dateOrNull(root.path("startDate")), dateOrNull(root.path("endDate")),
                    textOrNull(root.path("projectName")));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("AI job-description parsing returned an unexpected response");
        }
    }
```

(`extractText`, `textOrNull`, `dateOrNull`, `MAPPER`, `rest`, `endpoint`, `apiKey`, `log` already exist in this class.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=GeminiJdParseTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/GeminiClient.java \
        src/main/java/com/softility/omivertex/service/GeminiHttpClient.java \
        src/test/java/com/softility/omivertex/service/GeminiJdParseTest.java
git commit -m "Add GeminiClient.extractJobDescription + JSON mapping

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Fuzzy project-name matcher

A small, pure static method that maps a free-text project/client name to one of the known project options (or null). Lives on `PositionService`, unit-tested directly.

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/PositionService.java`
- Create: `src/test/java/com/softility/omivertex/service/ProjectNameMatchTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/service/ProjectNameMatchTest.java`:

```java
package com.softility.omivertex.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectNameMatchTest {

    private static final List<GeminiClient.ProjectOption> OPTIONS = List.of(
            new GeminiClient.ProjectOption(1L, "Acme Corp · Storefront Revamp"),
            new GeminiClient.ProjectOption(2L, "Acme Corp · Mobile App"),
            new GeminiClient.ProjectOption(3L, "Globex · Data Platform"));

    @Test
    void matchesOnSharedTokens() {
        assertEquals(2L, PositionService.matchProjectId("Acme mobile app", OPTIONS));
        assertEquals(3L, PositionService.matchProjectId("globex data platform", OPTIONS));
    }

    @Test
    void nullWhenNoOverlap() {
        assertNull(PositionService.matchProjectId("Initech payroll", OPTIONS));
        assertNull(PositionService.matchProjectId(null, OPTIONS));
        assertNull(PositionService.matchProjectId("   ", OPTIONS));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ProjectNameMatchTest`
Expected: FAIL to compile — `matchProjectId` does not exist.

- [ ] **Step 3: Add the matcher to `PositionService`**

In `src/main/java/com/softility/omivertex/service/PositionService.java`, add these two static methods (place them near the other private helpers, e.g. after `matchesSkill`). Also ensure `java.util.Set` and `java.util.HashSet` are imported (they already are):

```java
    /**
     * Best-effort map of a free-text project/client name to one of the given
     * options by shared word tokens; null when nothing overlaps. A suggestion the
     * user confirms — never authoritative.
     */
    static Long matchProjectId(String extractedName, List<GeminiClient.ProjectOption> options) {
        Set<String> wanted = tokenize(extractedName);
        if (wanted.isEmpty()) {
            return null;
        }
        Long bestId = null;
        int bestOverlap = 0;
        for (GeminiClient.ProjectOption o : options) {
            Set<String> have = tokenize(o.label());
            int overlap = 0;
            for (String t : wanted) {
                if (have.contains(t)) {
                    overlap++;
                }
            }
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestId = o.id();
            }
        }
        return bestOverlap > 0 ? bestId : null;
    }

    private static Set<String> tokenize(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) {
            return out;
        }
        for (String t : s.toLowerCase().split("[^a-z0-9]+")) {
            if (t.length() >= 2) { // drop 1-char noise and the "·" separator
                out.add(t);
            }
        }
        return out;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ProjectNameMatchTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/PositionService.java \
        src/test/java/com/softility/omivertex/service/ProjectNameMatchTest.java
git commit -m "Add fuzzy project-name matcher for JD autofill

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Response DTO

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/dto/PositionJdDtos.java`

- [ ] **Step 1: Create the DTO**

Create `src/main/java/com/softility/omivertex/web/dto/PositionJdDtos.java`:

```java
package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource;

import java.time.LocalDate;
import java.util.List;

public final class PositionJdDtos {

    private PositionJdDtos() {
    }

    /** A required-skill suggestion shaped for the position form's skill rows. */
    public record JdSuggestedSkill(Long skillId, String skillName, String categoryName,
                                   Proficiency minProficiency, boolean required) {
    }

    /**
     * Stateless prefill for the Open/Edit Position form. Any field is null/empty
     * when the JD did not state it. {@code unmatchedSkills} are skill names read
     * from the JD but not in the taxonomy — surfaced, never dropped.
     */
    public record ParsedJobDescriptionResponse(String title, List<JdSuggestedSkill> skills,
                                               List<String> unmatchedSkills, String jobDescription,
                                               WorkMode workMode, Integer allocationPercent,
                                               LocalDate startDate, LocalDate endDate,
                                               Long suggestedProjectId, String suggestedProjectName,
                                               boolean textExtracted, SuggestionSource source) {
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/softility/omivertex/web/dto/PositionJdDtos.java
git commit -m "Add ParsedJobDescriptionResponse DTO

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: PositionService.parseJobDescription

Wire the pieces: validate, extract text, call Mirai (or keyword fallback), map skills, resolve project id.

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/PositionService.java`
- Create: `src/test/java/com/softility/omivertex/service/GeminiJdParseTest.java` already exists (Task 2); this task adds a service test.
- Create: `src/test/java/com/softility/omivertex/api/PositionJdServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/api/PositionJdServiceTest.java` (extends `ApiTestBase` for the Spring context, seed helpers, and `@MockBean` pattern):

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import com.softility.omivertex.service.PositionService;
import com.softility.omivertex.web.dto.PositionJdDtos.ParsedJobDescriptionResponse;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PositionJdServiceTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;
    @Autowired PositionService positionService;

    /** A real one-page PDF so ResumeTextExtractor returns non-blank text end-to-end. */
    private MockMultipartFile pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.beginText();
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new MockMultipartFile("file", "jd.pdf", "application/pdf", out.toByteArray());
        }
    }

    @Test
    void aiExtraction_mapsSkillsUnmatchedAndProject() throws IOException {
        Client acme = client("Acme Corp");
        Project proj = project("ACM-200", "Mobile App", acme);
        Skill java = skill("Backend", "Java");

        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenReturn(new GeminiClient.JobDescriptionExtraction(
                        "Senior Java Developer",
                        List.of(new GeminiClient.ExtractedSkill(java.getId(),
                                com.softility.omivertex.domain.Proficiency.ADVANCE, "")),
                        List.of("Rust"),
                        "Build and run backend services.",
                        WorkMode.ONSHORE, 80, null, null,
                        "Acme Corp · Mobile App"));

        ParsedJobDescriptionResponse res =
                positionService.parseJobDescription(pdf("Java backend role at Acme Mobile App"));

        assertEquals("Senior Java Developer", res.title());
        assertEquals(SuggestionSource.AI, res.source());
        assertEquals(1, res.skills().size());
        assertEquals("Java", res.skills().get(0).skillName());
        assertTrue(res.skills().get(0).required());
        assertEquals(List.of("Rust"), res.unmatchedSkills());
        assertEquals(WorkMode.ONSHORE, res.workMode());
        assertEquals(80, res.allocationPercent());
        assertEquals(proj.getId(), res.suggestedProjectId());
        assertEquals("Acme Corp · Mobile App", res.suggestedProjectName());
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile png = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        assertThrows(com.softility.omivertex.web.error.BadRequestException.class,
                () -> positionService.parseJobDescription(png));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PositionJdServiceTest`
Expected: FAIL to compile — `parseJobDescription` does not exist.

- [ ] **Step 3: Add fields, constant, and method to `PositionService`**

In `src/main/java/com/softility/omivertex/service/PositionService.java`:

Add imports:

```java
import com.softility.omivertex.web.dto.PositionJdDtos;
import com.softility.omivertex.web.dto.PositionJdDtos.ParsedJobDescriptionResponse;
import com.softility.omivertex.web.dto.ResumeDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
```

Add a logger, the input cap, and three new dependency fields:

```java
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    /** AI extraction input cap — keeps prompts bounded for very long JDs. */
    static final int MAX_AI_JD_CHARS = 20_000;
```

Add these fields alongside the existing ones:

```java
    private final ResumeTextExtractor textExtractor;
    private final ResumeSkillMatcher skillMatcher;
    private final GeminiClient geminiClient;
```

Extend the constructor parameter list and assignments (append the three new params to the existing constructor):

```java
    public PositionService(OpenPositionRepository positions, ProjectRepository projects,
                           AssociateRepository associates, AllocationRepository allocations,
                           AllocationService allocationService, AuditService auditService,
                           SkillRepository skillRepository, AssociateSkillRepository associateSkillRepository,
                           PositionSkillRepository positionSkills,
                           ResumeTextExtractor textExtractor, ResumeSkillMatcher skillMatcher,
                           GeminiClient geminiClient) {
        this.positions = positions;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.allocationService = allocationService;
        this.auditService = auditService;
        this.skillRepository = skillRepository;
        this.associateSkillRepository = associateSkillRepository;
        this.positionSkills = positionSkills;
        this.textExtractor = textExtractor;
        this.skillMatcher = skillMatcher;
        this.geminiClient = geminiClient;
    }
```

Add the parse methods (place them after `get(...)` / before `create(...)`):

```java
    @Transactional(readOnly = true)
    public ParsedJobDescriptionResponse parseJobDescription(MultipartFile file) {
        UploadedDocuments.requirePdfOrDocx(file);
        try {
            byte[] bytes = file.getBytes();
            String text = textExtractor.extractText(bytes, file.getContentType(), file.getOriginalFilename());
            boolean textExtracted = text != null && !text.isBlank();

            List<GeminiClient.ProjectOption> projectOptions = projects.findAll().stream()
                    .map(p -> new GeminiClient.ProjectOption(p.getId(),
                            p.getClient().getName() + " · " + p.getName()))
                    .toList();

            if (textExtracted && geminiClient.isConfigured()) {
                try {
                    return aiParseJd(text, projectOptions);
                } catch (Exception e) {
                    log.warn("AI JD extraction failed — falling back to keyword matching", e);
                }
            }

            List<PositionJdDtos.JdSuggestedSkill> skills = skillMatcher.matchSkills(text).stream()
                    .map(s -> new PositionJdDtos.JdSuggestedSkill(s.getId(), s.getName(),
                            s.getCategory().getName(), null, true))
                    .toList();
            return new ParsedJobDescriptionResponse(null, skills, List.of(), null, null, null,
                    null, null, null, null, textExtracted, ResumeDtos.SuggestionSource.KEYWORD);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read upload file: " + e.getMessage());
        }
    }

    private ParsedJobDescriptionResponse aiParseJd(String text,
                                                   List<GeminiClient.ProjectOption> projectOptions) {
        Map<Long, Skill> byId = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        List<GeminiClient.SkillOption> taxonomy = byId.values().stream()
                .map(s -> new GeminiClient.SkillOption(s.getId(), s.getName()))
                .toList();
        String capped = text.length() > MAX_AI_JD_CHARS ? text.substring(0, MAX_AI_JD_CHARS) : text;
        GeminiClient.JobDescriptionExtraction ext =
                geminiClient.extractJobDescription(capped, taxonomy, projectOptions);

        List<PositionJdDtos.JdSuggestedSkill> skills = ext.skills().stream()
                .filter(s -> byId.containsKey(s.skillId()))
                .map(s -> {
                    Skill skill = byId.get(s.skillId());
                    return new PositionJdDtos.JdSuggestedSkill(skill.getId(), skill.getName(),
                            skill.getCategory().getName(), s.proficiency(), true);
                })
                .toList();
        List<String> unmatched = ext.unmatchedSkills() == null ? List.of() : ext.unmatchedSkills();
        Long suggestedProjectId = matchProjectId(ext.suggestedProjectName(), projectOptions);

        return new ParsedJobDescriptionResponse(ext.title(), skills, unmatched,
                ext.jobDescriptionText(), ext.workMode(), ext.allocationPercent(),
                ext.startDate(), ext.endDate(), suggestedProjectId, ext.suggestedProjectName(),
                true, ResumeDtos.SuggestionSource.AI);
    }
```

Note: `Skill`, `Map`, `Collectors`, `List`, `BadRequestException` are already imported (the class uses `com.softility.omivertex.domain.*` and the others). Keep the existing `MAX_MATCH_CANDIDATES` constant.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PositionJdServiceTest`
Expected: PASS (both cases).

- [ ] **Step 5: Run the wider suite to catch constructor-wiring regressions**

Run: `./mvnw test -Dtest=PositionApiTest,ResumeApiTest,PositionJdServiceTest`
Expected: PASS — the extended constructor is satisfied by Spring; existing position/résumé behavior unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/PositionService.java \
        src/test/java/com/softility/omivertex/api/PositionJdServiceTest.java
git commit -m "Add PositionService.parseJobDescription (Mirai + keyword fallback)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Endpoint POST /positions/parse-jd

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/PositionController.java`
- Create: `src/test/java/com/softility/omivertex/api/PositionJdParseApiTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/softility/omivertex/api/PositionJdParseApiTest.java`. It builds real PDFs with PDFBox (same helper as `ResumeApiTest`) so text extraction runs end-to-end.

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PositionJdParseApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    private byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.beginText();
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void parseJd_aiExtraction_prefillsFields() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-200", "Mobile App", acme);
        var java = skill("Backend", "Java");

        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenReturn(new GeminiClient.JobDescriptionExtraction(
                        "Senior Java Developer",
                        List.of(new GeminiClient.ExtractedSkill(java.getId(), Proficiency.ADVANCE, "")),
                        List.of("Rust"),
                        "Build backend services.",
                        WorkMode.ONSHORE, 80, null, null,
                        "Acme Corp · Mobile App"));

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("Java backend role at Acme Mobile App"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skills[0].required").value(true))
                .andExpect(jsonPath("$.unmatchedSkills[0]").value("Rust"))
                .andExpect(jsonPath("$.workMode").value("ONSHORE"))
                .andExpect(jsonPath("$.allocationPercent").value(80))
                .andExpect(jsonPath("$.suggestedProjectId").value(proj.getId()))
                .andExpect(jsonPath("$.suggestedProjectName").value("Acme Corp · Mobile App"));
    }

    @Test
    void parseJd_notConfigured_fallsBackToKeywordSkills() throws Exception {
        skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(false);

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("We need a Java developer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"))
                .andExpect(jsonPath("$.title").value((String) null))
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"));
    }

    @Test
    void parseJd_geminiThrows_fallsBackNo500() throws Exception {
        skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenThrow(new RuntimeException("upstream 500"));

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("We need a Java developer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"));
    }

    @Test
    void parseJd_invalidFileType_returns400() throws Exception {
        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PositionJdParseApiTest`
Expected: FAIL — endpoint returns 404 / does not exist.

- [ ] **Step 3: Add the endpoint**

In `src/main/java/com/softility/omivertex/web/PositionController.java`, add imports:

```java
import com.softility.omivertex.service.AiExecutor;
import com.softility.omivertex.web.dto.PositionJdDtos.ParsedJobDescriptionResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
```

Inject `AiExecutor` (extend the constructor):

```java
    private final PositionService positionService;
    private final AiExecutor aiExecutor;

    public PositionController(PositionService positionService, AiExecutor aiExecutor) {
        this.positionService = positionService;
        this.aiExecutor = aiExecutor;
    }
```

Add the endpoint method (e.g. after `matches`):

```java
    /** Async on the AI bulkhead: reads a JD file and returns a stateless form prefill. */
    @PostMapping("/parse-jd")
    public CompletableFuture<ParsedJobDescriptionResponse> parseJd(
            @RequestParam("file") MultipartFile file) {
        return aiExecutor.submit(() -> positionService.parseJobDescription(file));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PositionJdParseApiTest`
Expected: PASS (all four cases).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/softility/omivertex/web/PositionController.java \
        src/test/java/com/softility/omivertex/api/PositionJdParseApiTest.java
git commit -m "Add POST /positions/parse-jd endpoint

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Frontend API helper

**Files:**
- Modify: `frontend/src/api.js`

- [ ] **Step 1: Add `parseJd`**

In `frontend/src/api.js`, add this entry to the `api` object (next to `parseResume`, same pattern):

```javascript
  parseJd: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${BASE}/positions/parse-jd`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      const error = new Error(body.message || 'Parsing failed');
      error.status = res.status;
      throw error;
    }
    return res.json();
  },
```

- [ ] **Step 2: Verify formatting/lint**

Run: `cd frontend && npm run format && npm run lint`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api.js
git commit -m "Add api.parseJd frontend helper

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Positions modal — upload button, prefill, unmatched-skills note

**Files:**
- Modify: `frontend/src/pages/Positions.jsx`

- [ ] **Step 1: Add parse state and handler**

In `frontend/src/pages/Positions.jsx`, inside the `Positions` component (near the other `useState` hooks, after `const [saving, setSaving] = useState(false);`), add:

```javascript
  const [parsingJd, setParsingJd] = useState(false);
  const [unmatchedSkills, setUnmatchedSkills] = useState([]);
```

Add the upload handler (after the `set`, `setSkillRow`, `removeSkillRow` helpers):

```javascript
  const onUploadJd = async (e) => {
    const file = e.target.files && e.target.files[0];
    e.target.value = ''; // allow re-selecting the same file
    if (!file) return;
    setParsingJd(true);
    setUnmatchedSkills([]);
    try {
      const p = await api.parseJd(file);
      setEditing((cur) => {
        if (!cur) return cur;
        const legacy = (p.unmatchedSkills || []).join(', ');
        return {
          ...cur,
          form: {
            ...cur.form,
            title: p.title || cur.form.title,
            projectId: p.suggestedProjectId != null ? p.suggestedProjectId : cur.form.projectId,
            jobDescription: p.jobDescription || cur.form.jobDescription,
            workMode: p.workMode || cur.form.workMode,
            allocationPercent:
              p.allocationPercent != null ? p.allocationPercent : cur.form.allocationPercent,
            startDate: p.startDate || cur.form.startDate,
            endDate: p.endDate || cur.form.endDate,
            requiredSkill: legacy || cur.form.requiredSkill,
            skills: (p.skills || []).map((s) => ({
              skillId: s.skillId,
              minProficiency: s.minProficiency || '',
              required: s.required,
            })),
          },
        };
      });
      setUnmatchedSkills(p.unmatchedSkills || []);
      const skillCount = (p.skills || []).length;
      if (!p.title && skillCount === 0) {
        showToast('Couldn’t read much from that file — please fill the form manually', true);
      } else {
        showToast('Form pre-filled from the job description');
        if (p.suggestedProjectId == null && p.suggestedProjectName) {
          showToast(`Couldn’t match project “${p.suggestedProjectName}” — pick it manually`, true);
        }
      }
    } catch (err) {
      showToast(err.message || 'Failed to parse the job description', true);
    } finally {
      setParsingJd(false);
    }
  };
```

- [ ] **Step 2: Reset the unmatched note when opening the modal**

In `openCreate` and `openEdit`, add `setUnmatchedSkills([]);` alongside the existing `setErrors({});` so the note doesn't leak between opens.

- [ ] **Step 3: Add the upload control + note to the Job Description field**

Replace the existing Job Description `Field` block (the one with the `<textarea>` for `jobDescription`) with this version that adds an upload button and the unmatched-skills note:

```jsx
              <Field label="Job Description" error={errors.jobDescription} full>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <label className="btn btn-ghost btn-sm" style={{ cursor: 'pointer' }}>
                      <Icon name="sparkles" size={14} />{' '}
                      {parsingJd ? 'Reading JD…' : 'Upload JD to autofill'}
                      <input
                        type="file"
                        accept=".pdf,.docx"
                        hidden
                        disabled={parsingJd}
                        onChange={onUploadJd}
                      />
                    </label>
                  </div>
                  <textarea
                    value={editing.form.jobDescription}
                    onChange={(e) => set('jobDescription', e.target.value)}
                    placeholder="Describe the responsibilities, project context, and daily tasks..."
                    rows={4}
                    className={errors.jobDescription ? 'invalid' : ''}
                  />
                  {unmatchedSkills.length > 0 && (
                    <p className="stat-hint" style={{ margin: 0 }}>
                      Not in your taxonomy — kept as free text: {unmatchedSkills.join(', ')}
                    </p>
                  )}
                </div>
              </Field>
```

- [ ] **Step 4: Build the frontend to verify format + lint + compile**

Run: `cd frontend && npm run format && npm run build`
Expected: BUILD succeeds (Prettier + ESLint pass, Vite build output in `frontend/dist`).

- [ ] **Step 5: Manual smoke check**

Run the app locally (`./mvnw spring-boot:run` after a frontend build, or the project's usual run path), open the Demand page → **Open Position**, click **Upload JD to autofill**, choose a sample JD PDF, and confirm the title, skills, description, work mode, dates, and project preselect populate; any out-of-taxonomy skills show under the note and in the legacy Required Skill field.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Positions.jsx
git commit -m "Positions: upload JD to autofill the Open Position form

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Docs, graph refresh, full verification

**Files:**
- Modify: `docs/TECHNICAL.md`

- [ ] **Step 1: Document the endpoint**

In `docs/TECHNICAL.md`, in the positions/demand section (near the other `/api/v1/positions` routes), add an entry describing:

> `POST /api/v1/positions/parse-jd` (multipart `file`, PDF/DOCX) — stateless. Extracts, via Mirai (Gemini) with a keyword fallback, a suggested title, taxonomy-matched required skills, a cleaned job-description summary, work mode, allocation %, start/end dates, and a suggested existing project (fuzzy-matched from the JD's project/client name). Skills not in the taxonomy are returned in `unmatchedSkills` (never dropped). Persists nothing; the client reviews and saves through the normal create/update endpoints. Runs on the `AiExecutor` bulkhead (async).

Also note the shared `UploadedDocuments` PDF/DOCX guard now backs both résumé and JD uploads.

- [ ] **Step 2: Full backend suite (Spotless + ArchUnit included)**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all green. If Spotless complains: `./mvnw spotless:apply`, then rerun.

- [ ] **Step 3: Full frontend build**

Run: `cd frontend && npm run format && npm run build`
Expected: BUILD succeeds.

- [ ] **Step 4: Refresh the knowledge graph**

Run: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
Expected: completes without error; `graphify-out/` updated.

- [ ] **Step 5: Delete this plan (disposable scaffolding) and commit docs + graph**

Per AGENTS.md, the plan file is deleted once its feature is merged.

```bash
git rm docs/superpowers/plans/2026-07-19-jd-autofill-position.md
git add docs/TECHNICAL.md graphify-out
git commit -m "Docs + graph refresh for JD-autofill; remove merged plan

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Notes / deliberate simplifications

- **Extracted JD skills default to must-have (`required: true`).** `GeminiClient.ExtractedSkill` carries no must/nice flag, and adding one would ripple into résumé extraction. The manager toggles nice-to-have per row in the form. If must/nice detection is wanted later, add a `required` field to the extraction contract as a separate change.
- **Project is resolved server-side** from the model's free-text `suggestedProjectName` via `matchProjectId` (pure, unit-tested). The model is *shown* the real project labels to improve the name it returns, but never returns an id we trust blindly.
- **No file storage, no migration, no taxonomy writes** — matches the approved spec's scope boundaries. The permissioned "Add unmatched skill to taxonomy" affordance is a deferred follow-up.
