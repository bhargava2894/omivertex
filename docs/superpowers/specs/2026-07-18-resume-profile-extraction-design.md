# Résumé profile extraction with pre-save review

**Date:** 2026-07-18 · **Status:** Approved

## What & why

Uploading a résumé today extracts only skills. The New Associate flow should
also extract the candidate's **name**, **phone**, and **employment history**,
prefill the form, and let the admin review everything **before** saving —
the filled form is the reviewed overview; nothing persists until Create.

User decisions: employment history is **structured entries** (not a text
blob), and the flow applies to the **New Associate form only** — the profile
page's résumé re-upload keeps today's skills-only suggestions.

## Data model (migration V10)

- `associate.phone` — nullable varchar(32). Displayed, never required.
- New table `employment_history`: `id`, `associate_id` (FK, cascade delete),
  `company` (not null), `title` (nullable), `start_date` / `end_date`
  (nullable dates), `sort_order` int (résumé order, newest first as
  extracted). New entity `EmploymentHistory` + repository
  (`findByAssociateIdOrderBySortOrderAsc`).

## Extraction (one Gemini call, extended contract)

`GeminiClient.ResumeExtraction` grows from `(skills, experienceSummary)` to
also carry `name`, `phone`, and `employment` —
`Employment(company, title, startDate, endDate)` with `LocalDate` fields.
The prompt asks for strict JSON with first-of-month ISO dates or null
("Jan 2020 – Present" → `2020-01-01` / null); unparseable dates degrade to
null, entries without a company are dropped. The keyword fallback path
returns the new fields empty — no AI key means today's behavior exactly.

**Softility rows are never imported** (user decision): internal engagement
history is authoritative and system-derived from allocations, while résumé
history is self-reported external data — the two are shown as separate
profile cards and never merged. Extracted entries whose company matches the
form's Company value (case-insensitive; default "Softility") are dropped in
the overview with a one-line note ("Softility entry omitted — internal
history comes from allocations"); the admin can still add a row manually.

**Email is deliberately not extracted**: résumés carry personal addresses,
while this app derives company emails via `EmailNaming` — prefilling a
personal email would plant wrong data.

## API changes

- `ParsedResumeResponse` += `name`, `phone`,
  `List<EmploymentEntry> employmentHistory` (empty on the keyword path).
- `AssociateRequest` += optional `phone` and optional
  `List<EmploymentEntry> employmentHistory` — applied on **create** only
  (update ignores the history list; post-create editing is a follow-up).
  `AssociateService.create` persists entries in order and mentions the count
  in the audit summary.
- `AssociateResponse` += `phone` and `employmentHistory` (ordered).

## Frontend

- **New Associate form** (`Associates.jsx`): picking a résumé parses as
  today; the response now also drives an **"Extracted from résumé"**
  overview inside the modal — Name and Phone prefill their form fields
  **only when currently empty** (never clobbering typed values), and
  employment entries render as editable rows (company, title, start, end,
  remove button). The rows submit with Create. Skills review is unchanged.
- **Phone field** added to the New/Edit Associate form (plain optional
  input) and to the Profile header detail line; **MyProfile** "My Details"
  gains a Phone row.
- **Profile page**: a read-only **"Previous employment"** card (company ·
  title · dates per row) shown when entries exist.

## Error handling

Existing patterns: extraction failure or keyword path → skills-only notice
as today, no overview section; per-entry leniency (drop companyless rows,
null bad dates); server-side validation only on lengths (company/title ≤
120 chars via DTO validation); the create endpoint's existing error shape.

## Testing & verification

TDD per AGENTS.md.

- **Parse endpoint** (mocked `GeminiClient`): response carries name/phone/
  history; keyword fallback leaves them empty; entries with null dates
  survive.
- **Create**: associate with phone + 2 history entries persists both (order
  kept), echoes them in the response, audit written; create without them
  unchanged.
- **Profile/read**: `AssociateResponse` includes phone + ordered history.
- **Frontend**: `npm run format && npm run build`; live check — create an
  associate from a real résumé through the form, verify prefill + overview +
  saved profile card.
- Docs: `TECHNICAL.md` (entities, endpoints, extraction contract) and
  `TODO.md` decisions (structured history; email-not-extracted rationale;
  follow-ups: history editing, Mirai exposure, profile re-upload flow).
  Graph refreshed; full suite green throughout.

## Out of scope

Post-create employment-history editing, Mirai's `get_associate_detail`
including previous employers, email extraction, the profile-page re-upload
overview, importing history for existing associates.
