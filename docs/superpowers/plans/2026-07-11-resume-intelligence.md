# Resume Intelligence v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade resume parsing from word-boundary keyword matching to Gemini structured extraction — matched taxonomy skills with estimated proficiency and evidence quotes, plus an experience summary — falling back to the existing keyword matcher whenever the key is missing or the call fails, and exposing the parse to associates so it pre-fills the propose-skills flow.

**Architecture:** `GeminiClient` gains `isConfigured()` and `extractResume(text, taxonomy) -> ResumeExtraction`. `ResumeService.parse` tries the AI path first (text length-capped), falls back to `ResumeSkillMatcher` on any failure. Response DTO gains optional `proficiency`/`evidence` per suggestion plus `experienceSummary` and `source: AI|KEYWORD`. New `POST /api/v1/me/resumes/parse` (ASSOCIATE) reuses the same service. Frontend: Profile.jsx honors AI proficiencies; MyProfile.jsx parses after a resume proposal and pre-fills the skill editor.

**Tech Stack:** Spring Boot 3.5 / Java 21, Gemini `generateContent` REST (JSON mode), Mockito `@MockBean` API tests, React 18.

**Branch:** `feature/skill-gaps-and-ai`. Spec: `docs/superpowers/specs/2026-07-11-skill-gaps-resume-ai-assistant-actions-design.md`.

**Invariant (from spec):** the LLM only drafts — all persisted skill changes still flow through the existing replace/propose+approve paths.

---

### Task 1: Failing API tests for the AI parse path

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/ResumeApiTest.java` (add `@MockBean GeminiClient geminiClient;` field + three tests; existing tests keep passing because a Mockito boolean default is `false` → keyword path)

- [ ] **Step 1: Add the mock field and imports to `ResumeApiTest`**

```java
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.service.GeminiClient;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
```

and inside the class:

```java
@MockBean GeminiClient geminiClient;
```

(`AssistantApiTest` already uses the same `@MockBean GeminiClient` pattern.)

- [ ] **Step 2: Add the three tests** (reuse the class's existing PDF-bytes helper — `createPdf(...)` — for the upload payload; check its exact name/signature in the file before writing)

```java
@Test
void parseResume_usesAiExtraction_whenConfigured() throws Exception {
    var java = skill("Backend", "Java");
    when(geminiClient.isConfigured()).thenReturn(true);
    when(geminiClient.extractResume(anyString(), anyList()))
            .thenReturn(new GeminiClient.ResumeExtraction(
                    List.of(new GeminiClient.ExtractedSkill(java.getId(), Proficiency.ADVANCE,
                            "led a Java microservices team")),
                    "8 years of experience, most recently a senior backend engineer."));

    mockMvc.perform(multipart("/api/v1/resumes/parse")
                    .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                            createPdf("Java expert"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("AI"))
            .andExpect(jsonPath("$.experienceSummary").value(
                    "8 years of experience, most recently a senior backend engineer."))
            .andExpect(jsonPath("$.suggestedSkills[0].skillName").value("Java"))
            .andExpect(jsonPath("$.suggestedSkills[0].proficiency").value("ADVANCE"))
            .andExpect(jsonPath("$.suggestedSkills[0].evidence").value("led a Java microservices team"));
}

@Test
void parseResume_fallsBackToKeyword_whenGeminiFails() throws Exception {
    skill("Backend", "Java");
    when(geminiClient.isConfigured()).thenReturn(true);
    when(geminiClient.extractResume(anyString(), anyList()))
            .thenThrow(new RuntimeException("upstream 500"));

    mockMvc.perform(multipart("/api/v1/resumes/parse")
                    .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                            createPdf("Java expert"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("KEYWORD"))
            .andExpect(jsonPath("$.suggestedSkills[0].skillName").value("Java"));
}

@Test
void parseMyResume_associateAllowed_viaMeEndpoint() throws Exception {
    skill("Backend", "Java");
    // not configured -> keyword path; the point is the ASSOCIATE-role route works
    mockMvc.perform(multipart("/api/v1/me/resumes/parse")
                    .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                            createPdf("Java expert")))
                    .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("KEYWORD"));
}
```

(If the file's existing multipart tests use different helper names/imports — e.g. a `MockMultipartFile` factory or `SecurityMockMvcRequestPostProcessors` already imported — match them.)

- [ ] **Step 3: Verify red**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: COMPILE FAILURE — `isConfigured()`, `extractResume(..)`, `ResumeExtraction`, `ExtractedSkill` don't exist yet. That is the red state.

---

### Task 2: Contract + service + endpoint (make it pass)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/GeminiClient.java`
- Modify: `src/main/java/com/softility/omivertex/web/dto/ResumeDtos.java`
- Modify: `src/main/java/com/softility/omivertex/service/ResumeService.java`
- Modify: `src/main/java/com/softility/omivertex/web/MeController.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (stub the new methods so it still compiles; real impl in Task 3)

- [ ] **Step 1: Extend the `GeminiClient` interface**

```java
package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;

import java.util.List;

/**
 * Generates an assistant reply from a workforce context, prior turns, and the
 * user's question. Abstracted so the endpoint depends on the contract, not the
 * Gemini SDK/REST shape — and so tests supply a stub instead of calling Google.
 */
public interface GeminiClient {

    String reply(String workforceContext, List<Turn> history, String userMessage);

    /** True when an API key is present; callers use this to pick AI vs fallback paths. */
    boolean isConfigured();

    /**
     * Structured skill extraction from resume text. Matches only against the
     * supplied taxonomy; throws on any upstream/parse failure (callers fall back).
     */
    ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy);

    /** One prior chat turn; role is "user" or "model". */
    record Turn(String role, String content) {}

    /** One taxonomy entry offered to the model for matching. */
    record SkillOption(Long skillId, String name) {}

    /** One skill the model found, with its estimated level and supporting quote. */
    record ExtractedSkill(Long skillId, Proficiency proficiency, String evidence) {}

    /** Full extraction result. */
    record ResumeExtraction(List<ExtractedSkill> skills, String experienceSummary) {}
}
```

- [ ] **Step 2: Extend the DTOs** (`ResumeDtos.java`)

```java
package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;

import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    /** How the suggestions were produced — AI (Gemini) or the keyword matcher fallback. */
    public enum SuggestionSource { AI, KEYWORD }

    /** proficiency/evidence are AI-only; null on the keyword path. */
    public record SuggestedSkill(Long skillId, String skillName, String categoryName,
                                 Proficiency proficiency, String evidence) {
    }

    public record ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted,
                                       String experienceSummary, SuggestionSource source) {
    }

    public record ResumeMetaResponse(String filename, String contentType, long byteSize, Instant uploadedAt) {
    }
}
```

- [ ] **Step 3: Upgrade `ResumeService.parse`**

Add fields/constructor params `GeminiClient geminiClient` and `SkillRepository skillRepository` (import `com.softility.omivertex.repository.SkillRepository`, `com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource`, `org.slf4j.Logger/LoggerFactory`), plus:

```java
/** AI extraction input cap — keeps prompts bounded for very long resumes. */
static final int MAX_AI_RESUME_CHARS = 20_000;

private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
```

Replace the `parse` method:

```java
@Transactional(readOnly = true)
public ParsedResumeResponse parse(MultipartFile file) {
    validateFileType(file);
    try {
        byte[] bytes = file.getBytes();
        String text = textExtractor.extractText(bytes, file.getContentType(), file.getOriginalFilename());
        boolean textExtracted = text != null && !text.isBlank();

        if (textExtracted && geminiClient.isConfigured()) {
            try {
                return aiParse(text);
            } catch (Exception e) {
                log.warn("AI resume extraction failed — falling back to keyword matching", e);
            }
        }

        List<Skill> matched = skillMatcher.matchSkills(text);
        List<SuggestedSkill> suggestions = matched.stream()
                .map(s -> new SuggestedSkill(s.getId(), s.getName(), s.getCategory().getName(), null, null))
                .collect(Collectors.toList());
        return new ParsedResumeResponse(suggestions, textExtracted, null, SuggestionSource.KEYWORD);
    } catch (IOException e) {
        throw new BadRequestException("Failed to read upload file: " + e.getMessage());
    }
}

private ParsedResumeResponse aiParse(String text) {
    Map<Long, Skill> byId = skillRepository.findAll().stream()
            .collect(Collectors.toMap(Skill::getId, s -> s));
    List<GeminiClient.SkillOption> taxonomy = byId.values().stream()
            .map(s -> new GeminiClient.SkillOption(s.getId(), s.getName()))
            .toList();
    String capped = text.length() > MAX_AI_RESUME_CHARS ? text.substring(0, MAX_AI_RESUME_CHARS) : text;
    GeminiClient.ResumeExtraction extraction = geminiClient.extractResume(capped, taxonomy);
    List<SuggestedSkill> suggestions = extraction.skills().stream()
            .filter(s -> byId.containsKey(s.skillId()))
            .map(s -> {
                Skill skill = byId.get(s.skillId());
                return new SuggestedSkill(skill.getId(), skill.getName(),
                        skill.getCategory().getName(), s.proficiency(), s.evidence());
            })
            .toList();
    return new ParsedResumeResponse(suggestions, true, extraction.experienceSummary(), SuggestionSource.AI);
}
```

- [ ] **Step 4: ASSOCIATE parse endpoint** (`MeController` — add `ResumeService` constructor dependency and)

```java
/** Stateless AI/keyword skill suggestions from a resume — same parse the admin flow uses. */
@PostMapping("/resumes/parse")
public com.softility.omivertex.web.dto.ResumeDtos.ParsedResumeResponse parseResume(
        @org.springframework.web.bind.annotation.RequestParam("file")
        org.springframework.web.multipart.MultipartFile file) {
    return resumeService.parse(file);
}
```

(Use plain imports at the top of the file rather than fully-qualified names — match the file's style.)

- [ ] **Step 5: Keep `GeminiHttpClient` compiling** (temporary stubs; real impl is Task 3)

```java
@Override
public boolean isConfigured() {
    return !apiKey.isEmpty();
}

@Override
public ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy) {
    throw new UnsupportedOperationException("implemented in the next commit");
}
```

- [ ] **Step 6: Verify green**

Run: `./mvnw test -Dtest=ResumeApiTest`
Expected: PASS (all existing + 3 new). Then `./mvnw test` → full suite green (Spotless may require `./mvnw spotless:apply`).

- [ ] **Step 7: Commit**

```bash
git add -A src/main src/test
git commit -m "feat: AI resume extraction contract + keyword fallback + ASSOCIATE parse endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Real Gemini extraction (JSON mode) + unit tests

**Files:**
- Modify: `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java`
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`

- [ ] **Step 1: Failing unit tests for the JSON mapping**

```java
@Test
void parseExtraction_mapsSkillsAndSummary_droppingUnknownIdsAndBadProficiency() {
    List<GeminiClient.SkillOption> taxonomy = List.of(
            new GeminiClient.SkillOption(1L, "Java"),
            new GeminiClient.SkillOption(2L, "React"));
    String json = """
            {"skills":[
               {"skillId":1,"proficiency":"ADVANCE","evidence":"led a Java team"},
               {"skillId":2,"proficiency":"WIZARD","evidence":"built SPAs"},
               {"skillId":99,"proficiency":"MASTERY","evidence":"not in taxonomy"}],
             "experienceSummary":"8 years"}""";

    GeminiClient.ResumeExtraction out = GeminiHttpClient.parseExtraction(json, taxonomy);

    assertThat(out.experienceSummary()).isEqualTo("8 years");
    assertThat(out.skills()).hasSize(2);
    assertThat(out.skills().get(0).skillId()).isEqualTo(1L);
    assertThat(out.skills().get(0).proficiency()).isEqualTo(Proficiency.ADVANCE);
    assertThat(out.skills().get(0).evidence()).isEqualTo("led a Java team");
    // unknown proficiency degrades to INTERMEDIATE rather than dropping the skill
    assertThat(out.skills().get(1).proficiency()).isEqualTo(Proficiency.INTERMEDIATE);
}

@Test
void parseExtraction_malformedJson_throwsBadRequest() {
    assertThatThrownBy(() -> GeminiHttpClient.parseExtraction("not json", List.of()))
            .isInstanceOf(BadRequestException.class);
}
```

Add imports as needed (`Proficiency`, `BadRequestException`, `assertThatThrownBy`).

Run: `./mvnw test -Dtest=GeminiHttpClientTest` → COMPILE FAILURE (`parseExtraction` missing) = red.

- [ ] **Step 2: Implement in `GeminiHttpClient`**

Replace the Task-2 stub with (new imports: `com.fasterxml.jackson.databind.JsonNode`, `com.fasterxml.jackson.databind.ObjectMapper`, `com.softility.omivertex.domain.Proficiency`, `java.util.Set`, `java.util.stream.Collectors`):

```java
private static final ObjectMapper MAPPER = new ObjectMapper();

@Override
public ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy) {
    if (apiKey.isEmpty()) {
        throw new BadRequestException("AI resume parsing is not configured — "
                + "set OMIVERTEX_ASSISTANT_GEMINI_API_KEY and restart");
    }
    String skillList = taxonomy.stream()
            .map(o -> o.skillId() + ": " + o.name())
            .collect(Collectors.joining("\n"));
    String prompt = """
            You extract structured skill data from a resume.
            Match ONLY skills from this taxonomy (lines are "id: name"):
            %s

            Return STRICT JSON and nothing else:
            {"skills":[{"skillId":<id>,"proficiency":"NOVICE|FOUNDATIONAL|INTERMEDIATE|FUNCTIONAL_USER|ADVANCE|MASTERY","evidence":"<short quote or phrase from the resume>"}],
             "experienceSummary":"<1-2 sentences: total years of experience and recent roles>"}

            Resume text:
            %s
            """.formatted(skillList, resumeText);
    Map<String, Object> body = Map.of(
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of("responseMimeType", "application/json"));
    try {
        Map<String, Object> response = rest.post()
                .uri(ENDPOINT.formatted(model))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        return parseExtraction(extractText(response), taxonomy);
    } catch (BadRequestException e) {
        throw e;
    } catch (org.springframework.web.client.RestClientResponseException e) {
        log.warn("Gemini extraction returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        throw new BadRequestException("AI resume parsing is unavailable right now (upstream "
                + e.getStatusCode().value() + ")");
    } catch (Exception e) {
        log.warn("Gemini extraction call failed", e);
        throw new BadRequestException("AI resume parsing is unavailable right now");
    }
}

/** Maps the model's JSON to the contract; unknown skill ids are dropped, unknown proficiencies degrade to INTERMEDIATE. */
static ResumeExtraction parseExtraction(String json, List<SkillOption> taxonomy) {
    Set<Long> validIds = taxonomy.stream().map(SkillOption::skillId).collect(Collectors.toSet());
    try {
        JsonNode root = MAPPER.readTree(json);
        List<ExtractedSkill> skills = new java.util.ArrayList<>();
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
            skills.add(new ExtractedSkill(id, proficiency, s.path("evidence").asText("")));
        }
        return new ResumeExtraction(List.copyOf(skills), root.path("experienceSummary").asText(""));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new BadRequestException("AI resume parsing returned an unexpected response");
    }
}
```

(`isConfigured()` stays as written in Task 2.)

- [ ] **Step 3: Verify green + full suite**

Run: `./mvnw test -Dtest=GeminiHttpClientTest` → PASS. Then `./mvnw test` → green.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/GeminiHttpClient.java \
        src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java
git commit -m "feat: Gemini JSON-mode resume extraction with strict taxonomy mapping

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Frontend — honor AI suggestions in both flows

**Files:**
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/pages/Profile.jsx`
- Modify: `frontend/src/pages/MyProfile.jsx`

- [ ] **Step 1: `api.js`** — next to `parseResume`, add the associate variant (mirror `parseResume`'s multipart code exactly, changing only the path):

```js
parseMyResume: async (file) => {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${BASE}/me/resumes/parse`, {
    method: 'POST',
    body: form,
    credentials: 'include',
  });
  if (!res.ok) throw await toError(res);
  return res.json();
},
```

(Before writing, open `api.js:89-103` and copy `parseResume`'s exact fetch/error idiom — headers, credentials, and error helper naming must match.)

- [ ] **Step 2: `Profile.jsx`** — two changes:

1. In `handleReviewSkills` (line ~119), honor the AI proficiency:

```js
skillsMap[s.skillId] = { proficiency: s.proficiency || 'INTERMEDIATE', primary: false };
```

2. In the upload handler where `resumeNotice` is set after `api.parseResume`, use source-aware copy:

```js
const detected = parseData.suggestedSkills.length;
const viaAi = parseData.source === 'AI';
setResumeNotice(
  (viaAi
    ? `${detected} skills detected by AI with estimated proficiency` +
      (parseData.experienceSummary ? ` — ${parseData.experienceSummary}` : '')
    : `${detected} skills detected from the résumé (added at Intermediate)`) +
    ' — click "Review & Add Skills" to review and adjust before saving.'
);
```

- [ ] **Step 3: `MyProfile.jsx`** — after a resume proposal, parse and offer prefill:

1. New state next to the others: `const [aiSuggestions, setAiSuggestions] = useState(null);`
2. Extend `submitResume`:

```js
const submitResume = async (file) => {
  if (!file) return;
  try {
    await api.proposeResume(file);
    showToast('Resume submitted for approval');
    reloadChanges();
    try {
      const parsed = await api.parseMyResume(file);
      if (parsed.suggestedSkills?.length > 0) setAiSuggestions(parsed);
    } catch {
      // parsing is best-effort; the proposal itself already succeeded
    }
  } catch (err) {
    showToast(err.message, true);
  }
};
```

3. Prefill handler (below `openSkillEditor`, reusing its held-skills shape):

```js
const reviewSuggestedSkills = () => {
  const held = {};
  (profile.skillGroups || []).forEach((group) =>
    (group.skills || []).forEach((s) => {
      held[s.skillId] = { proficiency: s.proficiency, primary: !!s.primary };
    })
  );
  aiSuggestions.suggestedSkills.forEach((s) => {
    if (!held[s.skillId]) {
      held[s.skillId] = { proficiency: s.proficiency || 'INTERMEDIATE', primary: false };
    }
  });
  setEditingSkills(held);
  setAiSuggestions(null);
};
```

4. Notice banner in the JSX (place right after the `pendingResume` banner block; only offer the button when no skills change is already pending):

```jsx
{aiSuggestions && (
  <div className="form-alert">
    <div>
      {aiSuggestions.suggestedSkills.length} skills detected in your resume
      {aiSuggestions.experienceSummary ? ` — ${aiSuggestions.experienceSummary}` : ''}
    </div>
    {!pendingSkills && (
      <button className="btn btn-primary btn-sm" onClick={reviewSuggestedSkills}>
        Review &amp; propose skills
      </button>
    )}
  </div>
)}
```

(Match the file's actual banner classes/structure — read the `pendingResume` banner and mirror it.)

- [ ] **Step 4: Format + build**

Run: `cd frontend && npm run format && npm run build`
Expected: build green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api.js frontend/src/pages/Profile.jsx frontend/src/pages/MyProfile.jsx
git commit -m "feat: AI resume suggestions pre-fill skill editors (admin profile + self-service)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Docs, graph, wrap-up

**Files:**
- Modify: `docs/TECHNICAL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Contract updates**

`docs/TECHNICAL.md` endpoint table — update the `/resumes/parse` row and add the `/me` row:

```markdown
| `/resumes/parse` | POST multipart `file` (stateless suggestions; ADMIN; AI extraction with proficiency+evidence+`experienceSummary` when Gemini is configured, keyword fallback otherwise; `source: AI|KEYWORD`) | — |
| `/me/resumes/parse` | POST multipart `file` (same stateless parse for the self-service propose flow; ASSOCIATE) | — |
```

`docs/TODO.md` "Resolved decisions":

```markdown
- **AI resume parsing fails open to keyword matching** (2026-07-11): any Gemini
  failure (no key, upstream error, malformed JSON) silently degrades to the
  word-boundary matcher — parsing never breaks an environment. AI input is capped
  at 20k chars; unknown skill ids are dropped, unknown proficiencies degrade to
  INTERMEDIATE. The LLM only drafts: all writes still flow through
  replace-skills / propose+approve.
```

- [ ] **Step 2: Full suite + graph refresh + commit**

Run: `./mvnw test` → green.
Run: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

```bash
git add docs/TECHNICAL.md docs/TODO.md
git commit -m "docs: AI resume parsing contract + resolved decision

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
