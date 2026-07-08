# Résumé Upload & Skill Extraction — Design

**Date:** 2026-07-08
**Status:** Approved design (pre-plan)
**Author:** bhargava.sista28@gmail.com (with Claude)

## Goal

Let an admin attach a résumé (PDF or Word) to an associate — both while **creating**
the associate and later from the **associate profile** — and have the system **suggest
skills** read from the résumé so the admin doesn't have to hand-pick every one. One
résumé per associate; uploading a new one replaces the old.

## Why

Onboarding an associate today means manually ticking every skill in the taxonomy. A
résumé already lists those skills. Reading them out and pre-selecting them (for the
admin to confirm) removes the most tedious part of onboarding, while keeping a human
in the loop so the workforce graph stays accurate.

## Scope

### In scope
- Store one résumé per associate (PDF/DOCX), replace-on-upload.
- Upload from the **New Associate** form and from the **Associate profile**.
- Download and delete the stored résumé.
- Extract text locally and **suggest** matching taxonomy skills.
- Suggested skills default to **Intermediate** proficiency; the admin is shown a clear
  notice that they were auto-detected at Intermediate and must be reviewed.

### Non-goals (explicit)
- **No proficiency inference** — every suggested skill comes in at Intermediate; the
  résumé is never mined for "expert/beginner" signals.
- **No LLM / AI / external API** — extraction is fully local keyword matching. Nothing
  leaves the server. (This also keeps the feature working on the private on-prem server
  and avoids sending associate PII to any third party.)
- **No object storage / filesystem** — the résumé lives in Postgres as a blob, so it is
  covered by the existing `pg_dump` backup and needs no extra infrastructure on-prem.
- **No résumé history** — one résumé per associate; a new upload deletes the previous.
- **No preview/thumbnail rendering** and **no full-text search** of résumé content.

## Architecture

### Storage — separate `resumes` table, one-to-one with associate

A new table keeps the blob **out of** the `associates` table so roster/list queries
never drag megabytes of file data into memory.

```
resumes
  id            bigserial PK
  associate_id  bigint  NOT NULL  UNIQUE  FK -> associates(id)   -- one résumé per associate
  filename      varchar NOT NULL
  content_type  varchar NOT NULL
  byte_size     bigint  NOT NULL
  content       bytea   NOT NULL          -- the file bytes (LAZY-loaded)
  uploaded_at   timestamptz NOT NULL
```

- Delivered as Flyway migration **`V3__add_resumes_table.sql`** (V1, V2 already exist;
  prod/dev run `ddl-auto=validate`, so the migration is the source of truth).
- The `content` column maps to `@Lob @Basic(fetch = FetchType.LAZY)` so metadata reads
  don't load the bytes. Metadata is read via a projection query (below), never by
  loading the entity's blob.
- `content_type` stays a plain `String` (it is a free MIME value, not a fixed-value
  domain field — so it is *not* subject to the "fixed values must be enums" rule).

### Backend components (match existing Certification vertical-slice pattern)

| File | Responsibility |
|---|---|
| `domain/Resume.java` | JPA entity for the table above. |
| `repository/ResumeRepository.java` | `findByAssociateId`, `deleteByAssociateId`, and a `ResumeMeta` projection query `findMetaByAssociateId` (selects filename/contentType/byteSize/uploadedAt, **not** content). |
| `service/ResumeTextExtractor.java` | `String extract(byte[] bytes, String contentType)` — PDF via Apache **PDFBox**, DOCX via **POI `XWPFDocument`**. On a corrupt/encrypted file returns `""` (never throws to the caller). One place for "bytes → plain text". |
| `service/ResumeSkillMatcher.java` | `List<SuggestedSkill> match(String text, taxonomy)` — case-insensitive, **word-boundary** (`\b<escaped>\b`) match of each taxonomy skill name against the text; de-duplicated; suggest-only. One place for "text → skill ids". |
| `service/ResumeService.java` | Orchestration + audit + rules: `parse(file)` (stateless: extract → match → suggestions), `store(associateId, file)` (validate type, replace existing, audit), `download(associateId)`, `delete(associateId)`, `metaFor(associateId)`. `@Transactional`, constructor injection, calls `AuditService.record(...)` on every mutation. |
| `web/ResumeController.java` | Endpoints below; accepts `MultipartFile`, returns DTOs only. |
| `web/dto/ResumeDtos.java` | `ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted)`, `SuggestedSkill(Long skillId, String skillName, String categoryName)`, `ResumeMetaResponse(String filename, String contentType, long byteSize, Instant uploadedAt)`. |

### Endpoints

Security is inherited from the existing rules in `SecurityConfig` — no changes needed:
`GET /api/v1/**` = ADMIN or VIEWER; every other method under `/api/v1/**` = ADMIN.

| Method & path | Access | Purpose |
|---|---|---|
| `POST /api/v1/resumes/parse` | ADMIN | Stateless. Multipart file in → `ParsedResumeResponse`. Used by the **New Associate** form (associate doesn't exist yet) and by profile re-parse. Stores nothing. |
| `POST /api/v1/associates/{id}/resume` | ADMIN | Multipart file in → stores/replaces the blob, returns `ResumeMetaResponse`. Replacing deletes the prior row first (audited as "Replaced résumé"). |
| `GET /api/v1/associates/{id}/resume` | ADMIN + VIEWER | Downloads the stored file (`Content-Disposition: attachment`). 404 if none. |
| `DELETE /api/v1/associates/{id}/resume` | ADMIN | Removes the résumé. 204. |

`AssociateResponse` gains one field — **`resumeFilename` (nullable)** — populated from
the `ResumeMeta` projection (not the blob). This lets the profile know, in the single
associate fetch it already does, whether to show Download/Replace/Delete vs. Upload,
without a second round-trip and without loading bytes.

### Validation & limits
- Accept only PDF (`application/pdf`) and DOCX
  (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`), checked by
  content-type **and** file extension. Anything else → `BadRequestException` (400).
- File size: reuse the existing 10MB multipart cap (`application.properties`) — no new
  config.
- Missing associate → `NotFoundException` (404). Download/delete with no résumé → 404.
- Extraction failure (corrupt/encrypted) is **not** an upload failure: `store` still
  saves the bytes; `parse` returns `textExtracted=false` with an empty suggestion list
  so the UI can say "couldn't read skills from this file — add them manually".

## Data flow

### Creating an associate with a résumé ("store after Save")
1. Admin opens **New Associate**, fills the form.
2. Admin picks a résumé file in a new **Résumé** field (reuses the dropzone styling).
   On select, the browser calls `POST /resumes/parse`.
3. Suggestions merge into the existing in-form `SkillEditor` at **Intermediate** (only
   for skills not already chosen). A small inline notice appears:
   *"🛈 N skills detected from the résumé and added at Intermediate — please review and
   adjust each before saving."*
4. Admin reviews/edits skills, clicks **Save Associate**.
5. Frontend creates the associate (existing call, includes the reviewed skills), then —
   if a file is held — calls `POST /associates/{newId}/resume` to store it. One extra
   call after the associate exists; the file is held in component state until then.

### On the profile (résumé changes over time)
- **Résumé card** on the profile:
  - If `resumeFilename` present → show filename + **Download**, **Replace**, **Delete**.
  - Else → **Upload résumé**.
- **Upload/Replace** → `POST /associates/{id}/resume` (replaces, deleting the old file),
  then calls `parse`. If suggestions come back, a small panel shows
  *"Detected N skills (Intermediate — review)"* with **Review skills**, which opens the
  existing **Manage Skills** modal seeded with current + suggested skills. The admin
  confirms and saves through the existing `PUT /associates/{id}/skills` path — no new
  save route, and the graph only changes when the human clicks Save.

## Reused building blocks (no duplication)
- **`SkillEditor`** component — the suggestion merge target in both the add form and the
  profile Manage-Skills modal. No new skill-editing UI.
- **`PUT /associates/{id}/skills`** (`replaceSkills`) — the only skill-write path.
- **Certification vertical slice** — the structural template for the résumé slice
  (controller → service → repository → DTOs, audit in the service, DTOs at the boundary).
- **Existing 10MB multipart config** and **`SecurityConfig`** rules.
- **`ops/backup.sh`** (`pg_dump`) already backs up the new table's blobs.

## Testing (TDD — write the red test first)
- `ResumeTextExtractorTest` — build a tiny PDF (PDFBox `PDDocument`) and DOCX
  (`XWPFDocument`) **in the test**, assert known text is extracted; assert a corrupt
  byte array returns `""` rather than throwing.
- `ResumeSkillMatcherTest` — "Experienced in Java and Kubernetes" against a small
  taxonomy returns exactly those skill ids; case-insensitive; word-boundary (does **not**
  match `Java` inside `JavaScript`); no duplicates.
- `ResumeApiTest` — parse returns suggestions; upload stores + returns meta; a second
  upload **replaces** (exactly one row remains); download returns the bytes with the
  right `Content-Disposition`; delete → 204 then 404; unsupported type → 400; unknown
  associate → 404; VIEWER can GET the download but is 403 on POST/DELETE.
- Extend `AssociateApiTest` to assert `resumeFilename` is `null` with no résumé and set
  after upload.

## New dependency
- Apache **PDFBox** (`org.apache.pdfbox:pdfbox`) added to `pom.xml` for PDF text
  extraction. POI (`poi-ooxml`) is already present for DOCX.

## Ops / deployment note
This design is deployment-agnostic and fits the **private on-prem server**: DB-blob
storage rides on the existing Postgres backup, and local keyword extraction makes no
external calls. Nothing here depends on AWS/GCP.
