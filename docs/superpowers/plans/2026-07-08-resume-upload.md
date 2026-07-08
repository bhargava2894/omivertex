# Résumé Upload & Skill Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attach one résumé (PDF/DOCX) per associate — from the New Associate form and the profile — storing it as a Postgres blob and suggesting taxonomy skills read locally from the file.

**Architecture:** A new `resumes` table (Flyway V3), one row per associate, keeps the blob out of `associates`. A `ResumeTextExtractor` (PDFBox for PDF, POI for DOCX) feeds a `ResumeSkillMatcher` (whole-word, case-insensitive keyword match against the taxonomy). `ResumeService` orchestrates parse/store/download/delete with audit; `ResumeController` exposes the endpoints. The frontend adds a résumé field to the add form (store-after-save) and a résumé card to the profile (upload/replace/download/delete + re-suggest).

**Tech Stack:** Spring Boot 3.5 / Java 21, Spring Data JPA, Apache PDFBox 3.0.3, Apache POI (already present), Flyway; React 18 + Vite. Backend is strict TDD (H2 API tests via `ApiTestBase`); the frontend has no unit-test runner in this repo, so frontend tasks are verified by `npm run build` + manual smoke — a deliberate, stated exception to TDD.

**Spec:** `docs/superpowers/specs/2026-07-08-resume-upload-design.md`

---

## File Structure

**Backend — create:**
- `src/main/resources/db/migration/V3__add_resumes_table.sql` — table (prod/dev only).
- `src/main/java/com/softility/omivertex/domain/Resume.java` — JPA entity.
- `src/main/java/com/softility/omivertex/repository/ResumeRepository.java` — repo + `ResumeMeta` projection.
- `src/main/java/com/softility/omivertex/service/ResumeTextExtractor.java` — bytes → text.
- `src/main/java/com/softility/omivertex/service/ResumeSkillMatcher.java` — text → suggested skills.
- `src/main/java/com/softility/omivertex/service/ResumeService.java` — orchestration + audit.
- `src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java` — request/response records.
- `src/main/java/com/softility/omivertex/web/ResumeController.java` — endpoints.

**Backend — modify:**
- `pom.xml` — add PDFBox dependency.
- `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java` — add `resumeFilename`.
- `src/main/java/com/softility/omivertex/service/AssociateService.java` — populate `resumeFilename` in `get`.
- `src/test/java/com/softility/omivertex/api/ApiTestBase.java` — wire `ResumeRepository` + cleanup.

**Backend — tests:**
- `src/test/java/com/softility/omivertex/repository/ResumeRepositoryTest.java`
- `src/test/java/com/softility/omivertex/service/ResumeTextExtractorTest.java`
- `src/test/java/com/softility/omivertex/service/ResumeSkillMatcherTest.java`
- `src/test/java/com/softility/omivertex/api/ResumeApiTest.java`
- `src/test/java/com/softility/omivertex/api/AssociateApiTest.java` (extend)

**Frontend — modify:**
- `frontend/src/api.js` — résumé methods.
- `frontend/src/pages/Associates.jsx` — résumé field on the New Associate form.
- `frontend/src/pages/Profile.jsx` — résumé card.

**Docs:** `docs/TECHNICAL.md`, `docs/TODO.md`.

---

## Task 1: Storage — dependency, migration, entity, repository

**Files:**
- Modify: `pom.xml` (after the `poi-ooxml` dependency block, around line 54)
- Create: `src/main/resources/db/migration/V3__add_resumes_table.sql`
- Create: `src/main/java/com/softility/omivertex/domain/Resume.java`
- Create: `src/main/java/com/softility/omivertex/repository/ResumeRepository.java`
- Modify: `src/test/java/com/softility/omivertex/api/ApiTestBase.java`
- Test: `src/test/java/com/softility/omivertex/repository/ResumeRepositoryTest.java`

- [ ] **Step 1: Add the PDFBox dependency to `pom.xml`**

Insert directly after the `poi-ooxml` `</dependency>` (line 54):

```xml
		<!-- Extracts text from PDF résumés (POI above handles .docx) -->
		<dependency>
			<groupId>org.apache.pdfbox</groupId>
			<artifactId>pdfbox</artifactId>
			<version>3.0.3</version>
		</dependency>
```

- [ ] **Step 2: Create the Flyway migration** `src/main/resources/db/migration/V3__add_resumes_table.sql`

```sql
-- One résumé per associate. The blob lives here (not on `associates`) so roster
-- queries never load file bytes. Replaced on re-upload (see ResumeService).
-- ON DELETE CASCADE: deleting an associate removes their résumé automatically.
CREATE TABLE resumes (
    id            BIGSERIAL PRIMARY KEY,
    associate_id  BIGINT NOT NULL UNIQUE REFERENCES associates(id) ON DELETE CASCADE,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    byte_size     BIGINT NOT NULL,
    content       BYTEA NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
```

> Note: Flyway is disabled in tests (H2 uses `create-drop` from the entity). This
> migration is validated only under the prod/dev profiles. Tests exercise the entity
> mapping directly.

- [ ] **Step 3: Create the entity** `src/main/java/com/softility/omivertex/domain/Resume.java`

```java
package com.softility.omivertex.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * An associate's stored résumé. `associateId` is a plain column (not a JPA
 * relationship) with a unique constraint — one résumé per associate; the service
 * manages replace-on-upload by id. The blob is LAZY so metadata reads (via the
 * ResumeMeta projection) never pull the bytes.
 */
@Entity
@Table(name = "resumes", uniqueConstraints = @UniqueConstraint(columnNames = "associate_id"))
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "associate_id", nullable = false, unique = true)
    private Long associateId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociateId() { return associateId; }
    public void setAssociateId(Long associateId) { this.associateId = associateId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
```

- [ ] **Step 4: Create the repository** `src/main/java/com/softility/omivertex/repository/ResumeRepository.java`

```java
package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByAssociateId(Long associateId);

    boolean existsByAssociateId(Long associateId);

    void deleteByAssociateId(Long associateId);

    /** Metadata only — never selects the `content` blob. */
    @Query("select r.filename as filename, r.contentType as contentType, "
            + "r.byteSize as byteSize, r.uploadedAt as uploadedAt "
            + "from Resume r where r.associateId = :associateId")
    Optional<ResumeMeta> findMetaByAssociateId(Long associateId);

    interface ResumeMeta {
        String getFilename();
        String getContentType();
        long getByteSize();
        Instant getUploadedAt();
    }
}
```

- [ ] **Step 5: Wire `ResumeRepository` into `ApiTestBase`**

In `src/test/java/com/softility/omivertex/api/ApiTestBase.java`, add the autowired field after `appUserRepository` (line 29):

```java
    @Autowired protected com.softility.omivertex.repository.ResumeRepository resumeRepository;
```

And add the delete as the **first** line inside `cleanDatabase()` (before `appUserRepository.deleteAll();`) — résumés reference associates, so they must go first:

```java
        resumeRepository.deleteAll();
```

- [ ] **Step 6: Write the failing repository test** `src/test/java/com/softility/omivertex/repository/ResumeRepositoryTest.java`

```java
package com.softility.omivertex.repository;

import com.softility.omivertex.api.ApiTestBase;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ResumeRepositoryTest extends ApiTestBase {

    private Resume newResume(Long associateId, String filename, byte[] bytes) {
        Resume r = new Resume();
        r.setAssociateId(associateId);
        r.setFilename(filename);
        r.setContentType("application/pdf");
        r.setByteSize(bytes.length);
        r.setContent(bytes);
        r.setUploadedAt(Instant.now());
        return r;
    }

    @Test
    void savesAndFindsByAssociateId() {
        Associate a = associate("Meera Nair", "meera@softility.com", WorkMode.OFFSHORE);
        resumeRepository.save(newResume(a.getId(), "meera.pdf", "hello".getBytes()));

        var found = resumeRepository.findByAssociateId(a.getId());
        assertTrue(found.isPresent());
        assertEquals("meera.pdf", found.get().getFilename());
        assertArrayEquals("hello".getBytes(), found.get().getContent());
    }

    @Test
    void metaProjection_returnsMetadata() {
        Associate a = associate("Ravi K", "ravi@softility.com", WorkMode.ONSHORE);
        resumeRepository.save(newResume(a.getId(), "ravi.docx", "world".getBytes()));

        var meta = resumeRepository.findMetaByAssociateId(a.getId());
        assertTrue(meta.isPresent());
        assertEquals("ravi.docx", meta.get().getFilename());
        assertEquals(5, meta.get().getByteSize());
    }

    @Test
    void deleteByAssociateId_removesTheRow() {
        Associate a = associate("Sana P", "sana@softility.com", WorkMode.OFFSHORE);
        resumeRepository.save(newResume(a.getId(), "sana.pdf", "x".getBytes()));

        resumeRepository.deleteByAssociateId(a.getId());

        assertFalse(resumeRepository.existsByAssociateId(a.getId()));
    }
}
```

- [ ] **Step 7: Run the test to verify it passes** (entity + repo now exist)

Run: `./mvnw test -Dtest=ResumeRepositoryTest`
Expected: PASS (3 tests). If the whole app context fails to load because `ResumeService`/`ResumeController` don't exist yet — they don't need to; this test only needs the entity + repo. Context should load fine.

- [ ] **Step 8: Run Spotless and the full suite**

Run: `./mvnw spotless:apply && ./mvnw test`
Expected: all green (was 101; now 104).

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/resources/db/migration/V3__add_resumes_table.sql \
  src/main/java/com/softility/omivertex/domain/Resume.java \
  src/main/java/com/softility/omivertex/repository/ResumeRepository.java \
  src/test/java/com/softility/omivertex/api/ApiTestBase.java \
  src/test/java/com/softility/omivertex/repository/ResumeRepositoryTest.java
git commit -m "feat: resumes table, entity, and repository

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: ResumeTextExtractor (bytes → text)

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/ResumeTextExtractor.java`
- Test: `src/test/java/com/softility/omivertex/service/ResumeTextExtractorTest.java`

- [ ] **Step 1: Write the failing test** `src/test/java/com/softility/omivertex/service/ResumeTextExtractorTest.java`

```java
package com.softility.omivertex.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    private byte[] pdf(String text) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(text));
            doc.close();
            return out.toByteArray();
        }
    }

    private byte[] docx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        String text = extractor.extract(pdf("Experienced in Java and Kubernetes"), "application/pdf");
        assertTrue(text.contains("Java"));
        assertTrue(text.contains("Kubernetes"));
    }

    @Test
    void extractsTextFromDocx() throws Exception {
        String text = extractor.extract(docx("Skilled in Python and Docker"),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertTrue(text.contains("Python"));
        assertTrue(text.contains("Docker"));
    }

    @Test
    void returnsEmptyStringForCorruptFile() {
        assertEquals("", extractor.extract("not a real pdf".getBytes(), "application/pdf"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ResumeTextExtractorTest`
Expected: FAIL — `ResumeTextExtractor` does not exist (compilation error).

- [ ] **Step 3: Write the implementation** `src/main/java/com/softility/omivertex/service/ResumeTextExtractor.java`

```java
package com.softility.omivertex.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Turns résumé bytes into plain text. PDF via PDFBox, DOCX via POI. Never throws:
 * an unreadable/encrypted file yields "" so callers can fall back to manual entry.
 */
@Service
public class ResumeTextExtractor {

    public static final String PDF = "application/pdf";
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public String extract(byte[] bytes, String contentType) {
        try {
            if (isPdf(contentType)) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (isDocx(contentType)) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                        XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    return ex.getText();
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isSupported(String contentType) {
        return isPdf(contentType) || isDocx(contentType);
    }

    private static boolean isPdf(String ct) {
        return ct != null && ct.toLowerCase(Locale.ROOT).contains("pdf");
    }

    private static boolean isDocx(String ct) {
        return ct != null && ct.toLowerCase(Locale.ROOT).contains("wordprocessingml");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=ResumeTextExtractorTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add src/main/java/com/softility/omivertex/service/ResumeTextExtractor.java \
  src/test/java/com/softility/omivertex/service/ResumeTextExtractorTest.java
git commit -m "feat: ResumeTextExtractor (PDF via PDFBox, DOCX via POI)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: ResumeSkillMatcher (text → suggested skills)

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java` (the `SuggestedSkill` record the matcher returns)
- Create: `src/main/java/com/softility/omivertex/service/ResumeSkillMatcher.java`
- Test: `src/test/java/com/softility/omivertex/service/ResumeSkillMatcherTest.java`

- [ ] **Step 1: Create the DTO file** `src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java`

(Created now because the matcher returns `SuggestedSkill`; the other records are used in Task 4.)

```java
package com.softility.omivertex.web.dto;

import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record SuggestedSkill(Long skillId, String skillName, String categoryName) {
    }

    /** Result of parsing a résumé without storing it. */
    public record ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted) {
    }

    /** Metadata about a stored résumé (never includes the bytes). */
    public record ResumeMetaResponse(String filename, String contentType, long byteSize, Instant uploadedAt) {
    }
}
```

- [ ] **Step 2: Write the failing test** `src/test/java/com/softility/omivertex/service/ResumeSkillMatcherTest.java`

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.dto.ResumeDtos.SuggestedSkill;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryResponse;
import com.softility.omivertex.web.dto.TaxonomyDtos.SkillResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeSkillMatcherTest {

    private final ResumeSkillMatcher matcher = new ResumeSkillMatcher();

    private final List<CategoryResponse> taxonomy = List.of(
            new CategoryResponse(1L, "Languages", List.of(
                    new SkillResponse(10L, "Java"),
                    new SkillResponse(11L, "JavaScript"))),
            new CategoryResponse(2L, "Platforms", List.of(
                    new SkillResponse(20L, "Kubernetes"))));

    @Test
    void matchesWholeWordsCaseInsensitively() {
        var ids = matcher.match("Experienced in JAVA and kubernetes", taxonomy)
                .stream().map(SuggestedSkill::skillId).toList();
        assertTrue(ids.contains(10L));
        assertTrue(ids.contains(20L));
    }

    @Test
    void doesNotMatchJavaInsideJavaScript() {
        var ids = matcher.match("I only know JavaScript", taxonomy)
                .stream().map(SuggestedSkill::skillId).toList();
        assertTrue(ids.contains(11L));   // JavaScript matched
        assertFalse(ids.contains(10L));  // bare Java NOT matched
    }

    @Test
    void suggestsEachSkillAtMostOnce() {
        var result = matcher.match("Java Java Java everywhere", taxonomy);
        assertEquals(1, result.stream().filter(s -> s.skillId().equals(10L)).count());
    }

    @Test
    void emptyTextYieldsNoSuggestions() {
        assertTrue(matcher.match("", taxonomy).isEmpty());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ResumeSkillMatcherTest`
Expected: FAIL — `ResumeSkillMatcher` does not exist.

- [ ] **Step 4: Write the implementation** `src/main/java/com/softility/omivertex/service/ResumeSkillMatcher.java`

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.dto.ResumeDtos.SuggestedSkill;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Suggests taxonomy skills whose name appears in the résumé text. Case-insensitive,
 * whole-word match (so "Java" does not match inside "JavaScript"). Suggest-only: the
 * caller sets proficiency (always Intermediate) and a human confirms.
 *
 * Known limitation: symbol-heavy names like "C++"/"C#" may not match via word
 * boundaries; the admin adds those manually. Acceptable for v1 keyword matching.
 */
@Service
public class ResumeSkillMatcher {

    public List<SuggestedSkill> match(String text, List<CategoryResponse> taxonomy) {
        List<SuggestedSkill> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        for (CategoryResponse category : taxonomy) {
            for (var skill : category.skills()) {
                if (mentions(text, skill.name())) {
                    found.add(new SuggestedSkill(skill.id(), skill.name(), category.name()));
                }
            }
        }
        return found;
    }

    private static boolean mentions(String text, String skillName) {
        String regex = "\\b" + Pattern.quote(skillName) + "\\b";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=ResumeSkillMatcherTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
./mvnw spotless:apply
git add src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java \
  src/main/java/com/softility/omivertex/service/ResumeSkillMatcher.java \
  src/test/java/com/softility/omivertex/service/ResumeSkillMatcherTest.java
git commit -m "feat: ResumeSkillMatcher (whole-word taxonomy keyword match)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Parse endpoint (`POST /resumes/parse`)

**Files:**
- Create: `src/main/java/com/softility/omivertex/service/ResumeService.java` (parse only for now)
- Create: `src/main/java/com/softility/omivertex/web/ResumeController.java` (parse only for now)
- Test: `src/test/java/com/softility/omivertex/api/ResumeApiTest.java`

- [ ] **Step 1: Write the failing test** `src/test/java/com/softility/omivertex/api/ResumeApiTest.java`

```java
package com.softility.omivertex.api;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeApiTest extends ApiTestBase {

    private static final String PDF = "application/pdf";

    private byte[] pdf(String text) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(text));
            doc.close();
            return out.toByteArray();
        }
    }

    private MockMultipartFile resumeFile(String text) throws Exception {
        return new MockMultipartFile("file", "resume.pdf", PDF, pdf(text));
    }

    @Test
    void parse_suggestsSkillsFoundInText() throws Exception {
        skill("Languages", "Java");
        skill("Platforms", "Kubernetes");

        mockMvc.perform(multipart("/api/v1/resumes/parse").file(resumeFile("Strong in Java and Kubernetes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textExtracted").value(true))
                .andExpect(jsonPath("$.suggestedSkills[?(@.skillName=='Java')]").exists())
                .andExpect(jsonPath("$.suggestedSkills[?(@.skillName=='Kubernetes')]").exists());
    }

    @Test
    void parse_unsupportedType_returns400() throws Exception {
        var file = new MockMultipartFile("file", "resume.txt", "text/plain", "Java".getBytes());
        mockMvc.perform(multipart("/api/v1/resumes/parse").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: FAIL — no `/api/v1/resumes/parse` mapping (404), and `ResumeService`/`ResumeController` don't compile/exist.

- [ ] **Step 3: Create `ResumeService`** `src/main/java/com/softility/omivertex/service/ResumeService.java`

(Parse + shared helpers now; store/download/delete added in Task 5.)

```java
package com.softility.omivertex.service;

import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ResumeRepository;
import com.softility.omivertex.web.dto.ResumeDtos.ParsedResumeResponse;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestedSkill;
import com.softility.omivertex.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ResumeService {

    private final ResumeRepository resumes;
    private final AssociateRepository associates;
    private final ResumeTextExtractor extractor;
    private final ResumeSkillMatcher matcher;
    private final TaxonomyService taxonomyService;
    private final AuditService auditService;

    public ResumeService(ResumeRepository resumes, AssociateRepository associates,
            ResumeTextExtractor extractor, ResumeSkillMatcher matcher,
            TaxonomyService taxonomyService, AuditService auditService) {
        this.resumes = resumes;
        this.associates = associates;
        this.extractor = extractor;
        this.matcher = matcher;
        this.taxonomyService = taxonomyService;
        this.auditService = auditService;
    }

    /** Stateless: read the file, suggest skills. Stores nothing. */
    @Transactional(readOnly = true)
    public ParsedResumeResponse parse(MultipartFile file) {
        byte[] bytes = readBytes(file);
        String contentType = requireSupported(file);
        String text = extractor.extract(bytes, contentType);
        List<SuggestedSkill> suggestions = matcher.match(text, taxonomyService.list());
        return new ParsedResumeResponse(suggestions, !text.isBlank());
    }

    byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    /** Accepts PDF and DOCX only, by content type or filename extension. */
    String requireSupported(MultipartFile file) {
        String ct = file.getContentType();
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (ResumeTextExtractor.isSupported(ct)) {
            return ct;
        }
        if (name.endsWith(".pdf")) {
            return ResumeTextExtractor.PDF;
        }
        if (name.endsWith(".docx")) {
            return ResumeTextExtractor.DOCX;
        }
        throw new BadRequestException("Unsupported file type — upload a PDF or Word (.docx) résumé");
    }
}
```

- [ ] **Step 4: Create `ResumeController`** `src/main/java/com/softility/omivertex/web/ResumeController.java`

(Parse only now; other endpoints added in Task 5.)

```java
package com.softility.omivertex.web;

import com.softility.omivertex.service.ResumeService;
import com.softility.omivertex.web.dto.ResumeDtos.ParsedResumeResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/resumes/parse")
    public ParsedResumeResponse parse(@RequestParam("file") MultipartFile file) {
        return resumeService.parse(file);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
./mvnw spotless:apply
git add src/main/java/com/softility/omivertex/service/ResumeService.java \
  src/main/java/com/softility/omivertex/web/ResumeController.java \
  src/test/java/com/softility/omivertex/api/ResumeApiTest.java
git commit -m "feat: POST /resumes/parse — suggest skills from a résumé

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Store / download / delete résumé (with replace)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/ResumeService.java`
- Modify: `src/main/java/com/softility/omivertex/web/ResumeController.java`
- Test: `src/test/java/com/softility/omivertex/api/ResumeApiTest.java` (add cases)

- [ ] **Step 1: Add the failing tests** to `ResumeApiTest.java`

Add these imports at the top (alongside the existing ones):

```java
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.domain.WorkMode;
import org.springframework.security.test.context.support.WithMockUser;
import java.time.Instant;
```

Add these test methods to the class:

```java
    @Test
    void upload_thenDownload_returnsTheBytes() throws Exception {
        Associate a = associate("Meera Nair", "meera@softility.com", WorkMode.OFFSHORE);

        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId()).file(resumeFile("Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("resume.pdf"))
                .andExpect(jsonPath("$.contentType").value(PDF));

        mockMvc.perform(get("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("resume.pdf")))
                .andExpect(content().contentType(PDF));
    }

    @Test
    void upload_twice_replacesTheResume() throws Exception {
        Associate a = associate("Ravi K", "ravi@softility.com", WorkMode.ONSHORE);
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId()).file(resumeFile("first")))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId())
                        .file(new MockMultipartFile("file", "updated.pdf", PDF, pdf("second"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("updated.pdf"));

        org.junit.jupiter.api.Assertions.assertEquals(1, resumeRepository.count());
        mockMvc.perform(get("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(header().string("Content-Disposition", containsString("updated.pdf")));
    }

    @Test
    void delete_removesResume_thenDownload404() throws Exception {
        Associate a = associate("Sana P", "sana@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId()).file(resumeFile("Java")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_missingResume_returns404() throws Exception {
        Associate a = associate("No Resume", "nr@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(get("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_unknownAssociate_returns404() throws Exception {
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", 99999L).file(resumeFile("Java")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewer_canDownload_butCannotUpload() throws Exception {
        Associate a = associate("Priya", "priya@softility.com", WorkMode.OFFSHORE);
        Resume r = new Resume();
        r.setAssociateId(a.getId());
        r.setFilename("priya.pdf");
        r.setContentType(PDF);
        r.setByteSize(3);
        r.setContent("abc".getBytes());
        r.setUploadedAt(Instant.now());
        resumeRepository.save(r);

        mockMvc.perform(get("/api/v1/associates/{id}/resume", a.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId()).file(resumeFile("Java")))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: FAIL — no upload/download/delete mappings yet (404/405/403 mismatches, compile errors on new refs).

- [ ] **Step 3: Add store/download/delete to `ResumeService`**

Add these imports to `ResumeService.java`:

```java
import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.web.dto.ResumeDtos.ResumeMetaResponse;
import com.softility.omivertex.web.error.NotFoundException;
import java.time.Instant;
```

Add these methods to the class:

```java
    /** Stores (replacing any existing) the associate's résumé. */
    public ResumeMetaResponse store(Long associateId, MultipartFile file) {
        var associate = associates.findById(associateId)
                .orElseThrow(() -> new NotFoundException("Associate", associateId));
        byte[] bytes = readBytes(file);
        String contentType = requireSupported(file);
        boolean replacing = resumes.existsByAssociateId(associateId);
        resumes.deleteByAssociateId(associateId);
        resumes.flush(); // delete must hit the DB before the re-insert touches the unique constraint
        Resume resume = new Resume();
        resume.setAssociateId(associateId);
        resume.setFilename(sanitizeName(file.getOriginalFilename()));
        resume.setContentType(contentType);
        resume.setByteSize(bytes.length);
        resume.setContent(bytes);
        resume.setUploadedAt(Instant.now());
        resume = resumes.save(resume);
        auditService.record(replacing ? "UPDATED" : "CREATED", "Resume", resume.getId(),
                (replacing ? "Replaced résumé of " : "Uploaded résumé for ") + associate.getName());
        return new ResumeMetaResponse(resume.getFilename(), resume.getContentType(),
                resume.getByteSize(), resume.getUploadedAt());
    }

    @Transactional(readOnly = true)
    public Resume download(Long associateId) {
        return resumes.findByAssociateId(associateId)
                .orElseThrow(() -> new NotFoundException("Resume", associateId));
    }

    public void delete(Long associateId) {
        Resume resume = resumes.findByAssociateId(associateId)
                .orElseThrow(() -> new NotFoundException("Resume", associateId));
        auditService.record("DELETED", "Resume", resume.getId(),
                "Deleted résumé of associate " + associateId);
        resumes.delete(resume);
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "resume";
        }
        return name.replaceAll("[\\r\\n\"]", "_");
    }
```

- [ ] **Step 4: Add upload/download/delete to `ResumeController`**

Add these imports to `ResumeController.java`:

```java
import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.web.dto.ResumeDtos.ResumeMetaResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
```

Add these methods to the class:

```java
    @PostMapping("/associates/{id}/resume")
    public ResumeMetaResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return resumeService.store(id, file);
    }

    @GetMapping("/associates/{id}/resume")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Resume resume = resumeService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resume.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(resume.getContentType()))
                .body(resume.getContent());
    }

    @DeleteMapping("/associates/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        resumeService.delete(id);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: PASS (8 tests total).

- [ ] **Step 6: Commit**

```bash
./mvnw spotless:apply
git add src/main/java/com/softility/omivertex/service/ResumeService.java \
  src/main/java/com/softility/omivertex/web/ResumeController.java \
  src/test/java/com/softility/omivertex/api/ResumeApiTest.java
git commit -m "feat: upload/download/delete résumé with replace-on-upload

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Expose `resumeFilename` on the associate profile

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssociateService.java`
- Test: `src/test/java/com/softility/omivertex/api/AssociateApiTest.java` (add a case)

- [ ] **Step 1: Add the failing test** to `AssociateApiTest.java`

Ensure these imports exist (add any that are missing):

```java
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.hamcrest.Matchers.nullValue;
```

Add this test method:

```java
    @Test
    void get_exposesResumeFilename_nullUntilUploaded() throws Exception {
        var a = associate("Anita R", "anita@softility.com",
                com.softility.omivertex.domain.WorkMode.OFFSHORE);

        mockMvc.perform(get("/api/v1/associates/{id}", a.getId()))
                .andExpect(jsonPath("$.resumeFilename").value(nullValue()));

        // store bypasses extraction, so raw bytes with a .pdf content type are fine here
        var file = new MockMultipartFile("file", "anita.pdf", "application/pdf", "pdfdata".getBytes());
        mockMvc.perform(multipart("/api/v1/associates/{id}/resume", a.getId()).file(file))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/associates/{id}", a.getId()))
                .andExpect(jsonPath("$.resumeFilename").value("anita.pdf"));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=AssociateApiTest#get_exposesResumeFilename_nullUntilUploaded`
Expected: FAIL — `resumeFilename` field does not exist on the JSON.

- [ ] **Step 3: Add `resumeFilename` to `AssociateResponse`**

In `src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java`, add `String resumeFilename` as the **last** record component (after `List<SkillGroup> skillGroups`):

```java
        List<SkillGroup> skillGroups,
        String resumeFilename) {
```

Change the `from(...)` factory signature (line 43) to accept it and pass it through. Replace the existing `from` method with:

```java
    /** Builds the response from the associate plus their allocations, rated skills, and résumé filename (nullable). */
    public static AssociateResponse from(Associate associate, List<Allocation> allocations,
                                         List<AssociateSkill> ratedSkills, String resumeFilename) {
        List<Allocation> current = allocations.stream().filter(Allocation::isCurrent).toList();
        boolean billable = current.stream().anyMatch(Allocation::isBillable);
        Allocation primary = current.stream()
                .filter(Allocation::isBillable).findFirst()
                .orElse(current.isEmpty() ? null : current.get(0));
        return new AssociateResponse(associate.getId(), associate.getName(), associate.getEmail(),
                associate.getCompany(), associate.getLocation(), associate.getWorkMode(),
                associate.getDesignation(), associate.getPrimarySkill(), associate.getSecondarySkill(),
                associate.getStatus(), billable,
                primary == null ? null : primary.getProject().getId(),
                primary == null ? null : primary.getProject().getName(),
                primary == null ? null : primary.getProject().getClient().getName(),
                benchDays(associate, allocations),
                groupSkills(ratedSkills),
                resumeFilename);
    }
```

- [ ] **Step 4: Populate it in `AssociateService`**

In `src/main/java/com/softility/omivertex/service/AssociateService.java`:

Add the repository import:

```java
import com.softility.omivertex.repository.ResumeRepository;
```

Add the field and constructor param. Replace the field declarations + constructor (lines 33–48) with:

```java
    private final AssociateRepository associateRepository;
    private final AllocationRepository allocationRepository;

    private final AuditService auditService;
    private final AssociateSkillRepository associateSkillRepository;
    private final SkillRepository skillRepository;
    private final ResumeRepository resumeRepository;

    public AssociateService(AssociateRepository associateRepository, AllocationRepository allocationRepository,
                            AuditService auditService, AssociateSkillRepository associateSkillRepository,
                            SkillRepository skillRepository, ResumeRepository resumeRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.auditService = auditService;
        this.associateSkillRepository = associateSkillRepository;
        this.skillRepository = skillRepository;
        this.resumeRepository = resumeRepository;
    }
```

Update the `list(...)` mapping (line 58) to pass `null` for the filename (the roster does not need it):

```java
                .map(associate -> AssociateResponse.from(associate,
                        allocationsByAssociate.getOrDefault(associate.getId(), List.of()),
                        skillsByAssociate.getOrDefault(associate.getId(), List.of()),
                        null))
```

Replace `get(...)` (lines 79–84) with:

```java
    @Transactional(readOnly = true)
    public AssociateResponse get(Long id) {
        Associate associate = find(id);
        String resumeFilename = resumeRepository.findMetaByAssociateId(id)
                .map(ResumeRepository.ResumeMeta::getFilename).orElse(null);
        return AssociateResponse.from(associate, allocationRepository.findByAssociateId(id),
                associateSkillRepository.findByAssociateId(id), resumeFilename);
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=AssociateApiTest`
Expected: PASS (all AssociateApiTest cases, including the new one).

- [ ] **Step 6: Run the full backend suite**

Run: `./mvnw spotless:apply && ./mvnw test`
Expected: all green (101 baseline + ResumeRepositoryTest 3 + ResumeTextExtractorTest 3 + ResumeSkillMatcherTest 4 + ResumeApiTest 8 + AssociateApiTest +1 = ~120).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/softility/omivertex/web/dto/AssociateResponse.java \
  src/main/java/com/softility/omivertex/service/AssociateService.java \
  src/test/java/com/softility/omivertex/api/AssociateApiTest.java
git commit -m "feat: expose resumeFilename on the associate profile response

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Frontend API client methods

> **TDD exception:** the frontend has no unit-test runner in this repo (only Prettier +
> ESLint on build). Frontend tasks are verified by `npm run build` (which runs
> lint/format checks) and manual smoke. Stated per the TDD skill's exception clause.

**Files:**
- Modify: `frontend/src/api.js`

- [ ] **Step 1: Add résumé methods to the `api` object**

In `frontend/src/api.js`, add these entries to the `api` object (e.g. after `deleteCertification`, before the closing `};`):

```js
  parseResume: async (file) => {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(`${BASE}/resumes/parse`, { method: 'POST', body: form });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.message || 'Could not read the résumé');
    return body; // { suggestedSkills: [{skillId, skillName, categoryName}], textExtracted }
  },
  uploadResume: async (associateId, file) => {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(`${BASE}/associates/${associateId}/resume`, { method: 'POST', body: form });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.message || 'Résumé upload failed');
    return body; // { filename, contentType, byteSize, uploadedAt }
  },
  downloadResume: (associateId) => api.downloadUrl(`${BASE}/associates/${associateId}/resume`),
  deleteResume: (associateId) => request(`/associates/${associateId}/resume`, { method: 'DELETE' }),
```

- [ ] **Step 2: Verify it builds**

Run: `cd frontend && npm run build`
Expected: build succeeds (0 ESLint errors; format check passes). If Prettier complains, run `npm run format` and rebuild.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api.js
git commit -m "feat: frontend API methods for résumé parse/upload/download/delete

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: New Associate form — résumé field (store after save)

**Files:**
- Modify: `frontend/src/pages/Associates.jsx`

- [ ] **Step 1: Add résumé state**

In `Associates.jsx`, add these state hooks next to `const [saving, setSaving] = useState(false);` (around line 117):

```jsx
  const [resumeFile, setResumeFile] = useState(null);
  const [parsingResume, setParsingResume] = useState(false);
  const [resumeSuggestedCount, setResumeSuggestedCount] = useState(0);
```

- [ ] **Step 2: Reset résumé state when opening the create modal**

Replace `openCreate` (lines 129–132) with:

```jsx
  const openCreate = () => {
    setErrors({});
    setResumeFile(null);
    setResumeSuggestedCount(0);
    setEditing({ form: { ...EMPTY } });
  };
```

- [ ] **Step 3: Add the parse-and-merge handler**

Add this function inside the component, after `set` (around line 154):

```jsx
  // Reads a chosen résumé and merges detected skills into the form at Intermediate,
  // leaving any already-selected skill untouched. Suggestions only — the admin reviews.
  const parseResumeAndSuggest = async (file) => {
    if (!file) return;
    setResumeFile(file);
    setParsingResume(true);
    setResumeSuggestedCount(0);
    try {
      const { suggestedSkills = [] } = await api.parseResume(file);
      setEditing((e) => {
        const next = { ...(e.form.skills || {}) };
        let added = 0;
        suggestedSkills.forEach((s) => {
          if (!next[s.skillId]) {
            next[s.skillId] = { proficiency: 'INTERMEDIATE', primary: false };
            added += 1;
          }
        });
        setResumeSuggestedCount(added);
        return { ...e, form: { ...e.form, skills: next } };
      });
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setParsingResume(false);
    }
  };
```

- [ ] **Step 4: Store the résumé after the associate is saved**

Replace the `save` function body's `try` block (lines 170–176) so it uploads the held file after create/update:

```jsx
    try {
      let associateId = editing.id;
      if (editing.id) {
        await api.update('associates', editing.id, payload);
      } else {
        const created = await api.create('associates', payload);
        associateId = created.id;
      }
      if (resumeFile && associateId) {
        await api.uploadResume(associateId, resumeFile);
      }
      showToast(editing.id ? 'Associate updated' : 'Associate created');
      setResumeFile(null);
      setResumeSuggestedCount(0);
      setEditing(null);
      reload();
    } catch (err) {
      setErrors({
        ...err.fieldErrors,
        _general: Object.keys(err.fieldErrors).length ? null : err.message,
      });
    } finally {
      setSaving(false);
    }
```

- [ ] **Step 5: Render the résumé field (create only) above the Skills field**

In the modal's `form-grid`, immediately **before** the `<Field label="Skills ...">` block (line 391), insert:

```jsx
            {!editing.id && (
              <Field label="Résumé (PDF or Word)" full>
                <input
                  type="file"
                  accept=".pdf,.docx"
                  disabled={parsingResume}
                  onChange={(e) => parseResumeAndSuggest(e.target.files[0])}
                />
                {parsingResume && (
                  <div className="cell-sub" style={{ marginTop: '6px' }}>
                    Reading résumé…
                  </div>
                )}
                {resumeSuggestedCount > 0 && (
                  <div
                    style={{
                      marginTop: '8px',
                      padding: '8px 10px',
                      borderRadius: '8px',
                      background: 'var(--color-muted)',
                      fontSize: '13px',
                    }}
                  >
                    🛈 {resumeSuggestedCount} skill{resumeSuggestedCount === 1 ? '' : 's'} detected
                    from the résumé and added at <strong>Intermediate</strong> — please review and
                    adjust each below before saving.
                  </div>
                )}
              </Field>
            )}
```

- [ ] **Step 6: Verify it builds**

Run: `cd frontend && npm run build`
Expected: build succeeds. Run `npm run format` first if Prettier flags formatting.

- [ ] **Step 7: Manual smoke (with the app running)**

Build the frontend, start the app (`./mvnw spring-boot:run`), open Associates → New Associate, choose a PDF résumé that mentions known skills, confirm the notice appears and skills pre-fill at Intermediate, save, then open the new associate's profile and confirm the résumé is attached.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/Associates.jsx
git commit -m "feat: résumé upload on the New Associate form (store after save)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Profile — résumé card (upload / replace / download / delete + re-suggest)

**Files:**
- Modify: `frontend/src/pages/Profile.jsx`

- [ ] **Step 1: Add résumé state and handlers**

In `Profile.jsx`, add state next to the skills state (around line 30):

```jsx
  const [resumeBusy, setResumeBusy] = useState(false);
  const [resumeSuggestions, setResumeSuggestions] = useState(null); // {count} after an upload
```

Add these handlers after `handleSaveSkills` (around line 97):

```jsx
  const seedSkillsFromAssociate = () => {
    const map = {};
    (associate.skillGroups || []).forEach((group) => {
      (group.skills || []).forEach((skill) => {
        map[skill.skillId] = { proficiency: skill.proficiency, primary: !!skill.primary };
      });
    });
    return map;
  };

  const handleResumeUpload = async (file) => {
    if (!file) return;
    setResumeBusy(true);
    setResumeSuggestions(null);
    try {
      await api.uploadResume(id, file);
      showToast('Résumé uploaded');
      // suggest skills read from the résumé, merged (at Intermediate) over current skills
      const { suggestedSkills = [] } = await api.parseResume(file);
      const seeded = seedSkillsFromAssociate();
      let added = 0;
      suggestedSkills.forEach((s) => {
        if (!seeded[s.skillId]) {
          seeded[s.skillId] = { proficiency: 'INTERMEDIATE', primary: false };
          added += 1;
        }
      });
      reloadAssoc();
      if (added > 0) {
        setSelectedSkills(seeded);
        setResumeSuggestions({ count: added });
      }
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setResumeBusy(false);
    }
  };

  const handleResumeDelete = async () => {
    if (!window.confirm('Delete this résumé?')) return;
    try {
      await api.deleteResume(id);
      showToast('Résumé deleted');
      reloadAssoc();
    } catch (err) {
      showToast(err.message, true);
    }
  };
```

> Note: `setManagingSkills(true)` opens the existing Manage Skills modal. Its
> `useEffect` (line 44) re-seeds `selectedSkills` from `associate` on open, which would
> discard résumé suggestions. To preserve them, the "Review skills" button sets
> `selectedSkills` first, then opens the modal, and we guard the effect (next step).

- [ ] **Step 2: Guard the skills-seeding effect so it doesn't clobber suggestions**

Replace the `useEffect` that initializes selected skills (lines 44–54) with a version that only seeds when there are no pending résumé suggestions:

```jsx
  // Initialize selected skills when the modal opens — unless résumé suggestions are
  // already staged (handleResumeUpload set them), in which case keep those.
  useEffect(() => {
    if (managingSkills && associate && !resumeSuggestions) {
      const skillsMap = {};
      (associate.skillGroups || []).forEach((group) => {
        (group.skills || []).forEach((skill) => {
          skillsMap[skill.skillId] = { proficiency: skill.proficiency, primary: !!skill.primary };
        });
      });
      setSelectedSkills(skillsMap);
    }
  }, [managingSkills, associate, resumeSuggestions]);
```

Also clear the suggestions when the skills modal closes/saves so later opens seed normally. In `handleSaveSkills`, in its `try` after `setManagingSkills(false);` add `setResumeSuggestions(null);`. And in the Manage Skills modal's Cancel button `onClick`, change it to also clear:

```jsx
              <button
                className="btn btn-ghost"
                onClick={() => {
                  setManagingSkills(false);
                  setResumeSuggestions(null);
                }}
              >
                Cancel
              </button>
```

- [ ] **Step 3: Render the Résumé card**

Add a new card inside the two-column grid, after the Certifications card's closing `</div>` (line 326, before the grid's closing `</div>`):

```jsx
        {/* Résumé Card */}
        <div className="card" style={{ padding: '24px' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '16px',
            }}
          >
            <h3 style={{ margin: 0 }}>Résumé</h3>
          </div>
          {associate.resumeFilename ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Icon name="file" size={18} />
                <span className="cell-main">{associate.resumeFilename}</span>
              </div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                <button className="btn btn-ghost btn-sm" onClick={() => api.downloadResume(id)}>
                  <Icon name="download" size={14} /> Download
                </button>
                {canEdit && (
                  <>
                    <label className="btn btn-ghost btn-sm" style={{ cursor: 'pointer' }}>
                      <Icon name="upload" size={14} /> {resumeBusy ? 'Uploading…' : 'Replace'}
                      <input
                        type="file"
                        accept=".pdf,.docx"
                        disabled={resumeBusy}
                        style={{ display: 'none' }}
                        onChange={(e) => handleResumeUpload(e.target.files[0])}
                      />
                    </label>
                    <button className="btn btn-danger btn-sm" onClick={handleResumeDelete}>
                      <Icon name="trash" size={14} /> Delete
                    </button>
                  </>
                )}
              </div>
            </div>
          ) : (
            <div className="empty-state" style={{ padding: '20px 0' }}>
              <Icon name="inbox" size={30} />
              <p style={{ fontSize: '13.5px' }}>No résumé uploaded.</p>
              {canEdit && (
                <label className="btn btn-primary btn-sm" style={{ cursor: 'pointer' }}>
                  <Icon name="upload" size={14} /> {resumeBusy ? 'Uploading…' : 'Upload résumé'}
                  <input
                    type="file"
                    accept=".pdf,.docx"
                    disabled={resumeBusy}
                    style={{ display: 'none' }}
                    onChange={(e) => handleResumeUpload(e.target.files[0])}
                  />
                </label>
              )}
            </div>
          )}
          {resumeSuggestions && (
            <div
              style={{
                marginTop: '12px',
                padding: '10px 12px',
                borderRadius: '8px',
                background: 'var(--color-muted)',
                fontSize: '13px',
              }}
            >
              🛈 {resumeSuggestions.count} skill{resumeSuggestions.count === 1 ? '' : 's'} detected
              from the résumé (staged at <strong>Intermediate</strong>).{' '}
              <button className="link-btn" onClick={() => setManagingSkills(true)}>
                Review skills
              </button>
            </div>
          )}
        </div>
```

- [ ] **Step 4: Verify it builds**

Run: `cd frontend && npm run build`
Expected: build succeeds. Run `npm run format` first if Prettier flags formatting.

- [ ] **Step 5: Manual smoke (with the app running)**

On a profile: upload a résumé → confirm it appears with Download/Replace/Delete, the suggestion banner appears, "Review skills" opens Manage Skills with suggested skills at Intermediate preserved; Save persists them. Replace with another file → old is gone, new downloads. Delete → card returns to the Upload state. As a viewer, only Download shows.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Profile.jsx
git commit -m "feat: résumé card on the associate profile (upload/replace/download/delete)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: Docs + graph refresh + final verification

**Files:**
- Modify: `docs/TECHNICAL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Document the endpoints in `docs/TECHNICAL.md`**

Find the API contract / endpoints section and add a "Résumés" subsection describing:
- `POST /api/v1/resumes/parse` (multipart `file`) → `{ suggestedSkills: [{skillId, skillName, categoryName}], textExtracted }`. Admin. Stateless.
- `POST /api/v1/associates/{id}/resume` (multipart `file`) → `{ filename, contentType, byteSize, uploadedAt }`. Admin. Replaces any existing résumé.
- `GET /api/v1/associates/{id}/resume` → the file (attachment). Admin + Viewer. 404 if none.
- `DELETE /api/v1/associates/{id}/resume` → 204. Admin.
- Note: `AssociateResponse.resumeFilename` is populated on `GET /associates/{id}` (null in the list). Storage is a Postgres blob in `resumes` (one per associate); extraction is local (PDFBox/POI) keyword matching, suggest-only at Intermediate.

- [ ] **Step 2: Record the decision in `docs/TODO.md`**

Add an entry under the appropriate "done"/"resolved decisions" area noting the résumé feature shipped: DB-blob storage (chosen over object storage to fit the on-prem deployment and ride the existing `pg_dump` backup), local keyword extraction (no LLM/PII egress), one résumé per associate (replace-on-upload).

- [ ] **Step 3: Run the full backend suite and a frontend build**

Run: `./mvnw spotless:apply && ./mvnw test`
Expected: all green (~120 tests).

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 4: Refresh the graphify knowledge graph** (per AGENTS.md)

Run:
```bash
python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```
(If a graphify interpreter file exists at `graphify-out/.graphify_python`, use `$(cat graphify-out/.graphify_python)` instead of `python3`.)

- [ ] **Step 5: Commit**

```bash
git add docs/TECHNICAL.md docs/TODO.md graphify-out/
git commit -m "docs: document résumé endpoints; refresh knowledge graph

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review (checked against the spec)

**Spec coverage:**
- Separate `resumes` table, blob out of `associates`, one-per-associate, replace-on-upload → Tasks 1 & 5. ✅
- Flyway V3 → Task 1. ✅
- Local extraction (PDFBox + POI), suggest-only, Intermediate default → Tasks 2, 3, 8, 9. ✅
- Intermediate review notice (add form + profile) → Tasks 8 & 9. ✅
- Endpoints `parse` / `POST` / `GET` / `DELETE` with the documented security → Tasks 4 & 5 (security inherited, verified by the viewer test). ✅
- `AssociateResponse.resumeFilename` (populated on get, null in list) → Task 6. ✅
- Add-form store-after-save; profile upload/replace/download/delete + re-suggest → Tasks 8 & 9. ✅
- New dependency PDFBox → Task 1. ✅
- Non-goals (no proficiency inference, no LLM, no object storage, no history, no preview) → respected; nothing in the plan adds them. ✅
- Docs + graph refresh → Task 10. ✅

**Placeholder scan:** No TBD/TODO; every code step shows complete code and exact commands. ✅

**Type consistency:** `SuggestedSkill(skillId, skillName, categoryName)`, `ParsedResumeResponse(suggestedSkills, textExtracted)`, `ResumeMetaResponse(filename, contentType, byteSize, uploadedAt)`, `ResumeRepository.ResumeMeta`, `ResumeService.parse/store/download/delete`, and `AssociateResponse.from(..., resumeFilename)` are used consistently across backend tasks; frontend uses `suggestedSkills`/`skillId`/`skillName` and `resumeFilename` matching the DTOs. ✅
