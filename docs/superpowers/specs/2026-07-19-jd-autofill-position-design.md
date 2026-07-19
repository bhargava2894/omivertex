# Spec: Autofill Open Position from a Job Description (Mirai)

On the Demand page, when a resource manager opens or edits an Open Position, they can
upload a Job Description file (PDF or Word). Mirai (the Gemini-backed AI already used for
résumé parsing) reads it and prefills the form: position title, required skills (matched
to the existing skill taxonomy), the Job Description text, work mode, allocation %, and
start/end dates — and suggests which existing Project the JD refers to. Nothing is saved
until the manager reviews the prefilled form and clicks Save through the unchanged create/
update path.

This reuses the résumé-parsing pipeline (`ResumeTextExtractor`, the `AiExecutor` bulkhead,
taxonomy skill matching, suggest-then-confirm) rather than inventing a new one.

## User Review Required

No breaking changes. One new **stateless** endpoint (`POST /api/v1/positions/parse-jd`)
is added; it persists nothing — no new entity, no DB migration. The JD file is parsed and
discarded. The `GeminiClient` interface gains one method and two records. Existing
position create/update/read behaviour is untouched.

## Resolved Decisions

- **Parse-only, no file storage.** The uploaded JD is read for extraction and discarded;
  it is not attached to the position. (No new entity / migration / download endpoint.)
- **Project is suggested, never created.** Mirai fuzzy-matches the project/client name it
  reads to an existing `Project`; the manager confirms or changes it. No silent creation.
- **Extract targets:** title, required skills, Job Description text, work mode, allocation
  %, start/end dates.
- **Prefill overwrites** every field Mirai extracted (the common case is uploading into a
  fresh "Open Position" form); the manager edits afterward.
- **Entry point:** a button inside the Open/Edit Position modal, beside the Job
  Description field — one flow for both create and edit.
- **Skills not in the taxonomy are surfaced, never dropped.** Mirai returns them as raw
  strings; the modal shows them as a "not in your taxonomy" note and auto-fills them into
  the existing legacy **Required Skill (text fallback)** field, so no requirement is lost
  and the governed taxonomy is not silently mutated. Promoting an unmatched skill into the
  taxonomy (a permissioned "Add to taxonomy" affordance) is **deferred to a later
  iteration** — out of scope for this build.

## Architecture & Data Flow

```
[Positions modal] --upload PDF/DOCX--> POST /api/v1/positions/parse-jd
   -> PositionService.parseJobDescription(file)
       -> ResumeTextExtractor.extractText(bytes, contentType, filename)   (reused)
       -> if geminiClient.isConfigured():
             GeminiClient.extractJobDescription(text, taxonomy, projectOptions)
          else:
             ResumeSkillMatcher keyword fallback (skills only)
       -> resolve suggestedProjectId by fuzzy-matching extracted name (server-side)
   -> ParsedJobDescriptionResponse
[Modal] overwrites form fields from the response; preselects the suggested project
[User]  reviews / edits -> Save -> existing POST/PUT /positions (unchanged)
```

The endpoint runs on the existing `AiExecutor` bulkhead and returns
`CompletableFuture<ParsedJobDescriptionResponse>`, freeing the servlet thread while Mirai
responds — identical to `ResumeController.parseResume`.

## Proposed Changes

### GeminiClient (interface + real impl + test stub)

Modify [GeminiClient.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/GeminiClient.java):

- Add method:
  ```java
  JobDescriptionExtraction extractJobDescription(
      String jdText, List<SkillOption> taxonomy, List<ProjectOption> projects);
  ```
- Add records:
  ```java
  /** One existing project offered to the model for matching. */
  record ProjectOption(Long id, String label) {}

  /** Full JD extraction. Any field is null when the JD does not state it. */
  record JobDescriptionExtraction(
      String title,
      List<ExtractedSkill> skills,      // matched against the supplied taxonomy
      List<String> unmatchedSkills,     // skills read but NOT in the taxonomy, raw names
      String jobDescriptionText,        // cleaned JD prose
      WorkMode workMode,                // null = not stated
      Integer allocationPercent,        // null = not stated
      java.time.LocalDate startDate,    // null = not stated
      java.time.LocalDate endDate,      // null = not stated
      String suggestedProjectName) {}   // raw project/client name read from the JD

  // The server resolves suggestedProjectName -> a real projectId (fuzzy match,
  // testable Java); the model returns only the name it read.
  ```
- Implement `extractJobDescription` in the real Gemini implementation (structured-output
  prompt, same JSON-parse discipline as `extractResume`; throws on upstream/parse failure
  so the service falls back). The prompt instructs the model to return matched skills
  (with a taxonomy `skillId`) **and** a separate list of skill names it read but could not
  map to the supplied taxonomy (`unmatchedSkills`) — so genuine requirements outside the
  taxonomy are never lost. Stub it in the test double(s) that implement `GeminiClient`.

### Service Layer

Modify [PositionService.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/PositionService.java):

- Add `parseJobDescription(MultipartFile file)` returning `ParsedJobDescriptionResponse`,
  `@Transactional(readOnly = true)`. It:
  1. Validates the file type via the shared PDF/DOCX check (see below), rejecting empty /
     wrong-type files with `BadRequestException`.
  2. Extracts text with the reused `ResumeTextExtractor`.
  3. Caps input length with a named constant (mirror `ResumeService.MAX_AI_RESUME_CHARS`).
  4. If `geminiClient.isConfigured()` and text is non-blank, calls
     `extractJobDescription(...)`; on any exception, logs a warning and falls back.
  5. Fallback: `ResumeSkillMatcher` keyword skills only (title/project/dates left null).
  6. Maps returned skills against the taxonomy: skills present in the taxonomy become
     structured requirement rows; skills the model read but could not map are carried
     through as `unmatchedSkills` (raw strings) — never dropped. Resolves
     `suggestedProjectId` server-side by fuzzy-matching the extracted project/client name
     against existing projects (`ProjectRepository`).
- Inject `ResumeTextExtractor`, `ResumeSkillMatcher`, `GeminiClient`, `SkillRepository`,
  `ProjectRepository` via the constructor (constructor injection only).

**Shared file-type check (one source of truth).** The PDF/DOCX validation currently lives
inside `ResumeService.validateFileType`. Extract it into one shared place (a small
`UploadedDocuments` helper or a static method) and call it from both `ResumeService` and
`PositionService`, rather than copying the rule. (AGENTS.md: "one implementation per
cross-cutting rule.")

**Fuzzy project match** is its own small, testable unit: given the extracted name and the
list of `{id, "Client · Project"}` options, return the best-matching id or null.
Case-insensitive, tolerant of the client/project both appearing. Kept simple (normalized
substring / token overlap); no new dependency.

### Web / DTO Layer

Add `ParsedJobDescriptionResponse` (new record under
[web/dto/](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/)) —
the web-boundary shape returned to the modal. Skills are represented the same way the
position form consumes them (skillId, skillName, category, minProficiency, required), plus
`title`, `jobDescription`, `workMode`, `allocationPercent`, `startDate`, `endDate`,
`suggestedProjectId`, `suggestedProjectName`, `unmatchedSkills` (list of raw skill names
not in the taxonomy), and a boolean `textExtracted` / `SuggestionSource` flag so the UI can
explain a weak result. Never returns a JPA entity.

Modify [PositionController.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/PositionController.java):

- Inject `AiExecutor` (constructor).
- Add:
  ```java
  @PostMapping("/positions/parse-jd")
  public CompletableFuture<ParsedJobDescriptionResponse> parseJd(
      @RequestParam("file") MultipartFile file) {
    return aiExecutor.submit(() -> positionService.parseJobDescription(file));
  }
  ```

### Frontend Layer

Modify [Positions.jsx](file:///Users/bhargavasista/omivertex/frontend/src/pages/Positions.jsx):

- In the Open/Edit Position modal, beside the existing **Job Description** field, add an
  **"Upload JD to autofill"** button backed by a hidden file input (`accept=".pdf,.docx"`).
- On file select: POST the file to `positions/parse-jd` (add an `api` helper for a
  multipart POST if one is not already reusable), show an inline spinner + toast while
  Mirai parses.
- On success: overwrite the form state from the response — `title`, `skills` rows,
  `jobDescription`, `workMode`, `allocationPercent`, `startDate`, `endDate`, and
  `projectId` = `suggestedProjectId` (preselect in the SearchSelect). If
  `suggestedProjectId` is null but a name was read, show it as a hint so the manager can
  pick the project. If little was extracted, toast "Couldn't read much from that file."
- **Unmatched skills:** when `unmatchedSkills` is non-empty, append them into the legacy
  **Required Skill (text fallback)** field (comma-joined) and show a small "Not in your
  taxonomy — kept as free text" note listing them beside the skill rows, so the manager
  can see what Mirai found but couldn't structure. No requirement is silently lost.
- Respect `canEdit` (the button only shows when the manager can edit) and use CSS tokens
  only — no raw hex.

## Error Handling

All via existing typed exceptions and `GlobalExceptionHandler`:

- Empty file / unsupported type (`.txt`, image, etc.) → `BadRequestException` (400).
- Unreadable file → `BadRequestException` (400).
- Gemini throws → logged, falls back to keyword skills; endpoint still returns `200`.
- No AI key configured → same keyword fallback path (mirrors résumé parsing today).
- A JD that yields nothing useful → `200` with empty/partial suggestions and the
  `textExtracted` / source flag set, so the UI can explain it.

## Verification Plan

### Automated Tests (TDD — failing test first)

In [PositionApiTest.java](file:///Users/bhargavasista/omivertex/src/test/java/com/softility/omivertex/api/PositionApiTest.java)
plus a `PositionService` test with a stub `GeminiClient`:

- Upload a text-bearing PDF/DOCX → `200`, with extracted title, taxonomy-matched skills,
  and `suggestedProjectId` matching a seeded project.
- Fuzzy project match: JD naming "Acme mobile app" resolves to the seeded "Acme · Mobile
  App" project; an unmatchable name → `suggestedProjectId` null but `suggestedProjectName`
  present.
- Unmatched skills: a JD naming a skill absent from the taxonomy → that name appears in
  `unmatchedSkills` in the response (and lands in the legacy Required Skill field on the
  frontend) and is **not** silently dropped, while in-taxonomy skills still become
  structured rows.
- Gemini not configured → falls back to keyword skills, still `200`, title/project null.
- Gemini throws → falls back, no `500`.
- Empty file / `.txt` / image → `400` via `BadRequestException`.
- Shared file-type check: `ResumeService` still rejects the same types after extraction
  (its existing tests stay green).

### Manual Verification

- Run locally, open the Demand page → Open Position → upload a sample JD PDF, confirm the
  title, skills, description, work mode, dates, and preselected project populate; edit and
  Save; confirm the position persists normally.

## Scope Boundaries (YAGNI)

- No file storage, no new entity, no DB migration — parse-and-discard.
- No new project creation — Mirai only suggests an existing project.
- No taxonomy mutation — skills outside the taxonomy are preserved as free text, not
  created as `Skill` rows. A permissioned "Add to taxonomy" promotion is a **deferred**
  follow-up, not part of this build.
- The suggestion is a soft preselect; nothing persists until the manager clicks Save via
  the unchanged create/update path.
