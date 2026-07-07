# Unify Associate Skills Entry — Design

**Date:** 2026-07-07
**Status:** Approved (brainstorming), pending implementation plan

## Problem

An associate's skills are captured in two disconnected places using two different
concepts:

1. **Add/Edit Associate modal** — single-select "Primary skill" and "Secondary
   skill" dropdowns, stored as free-text strings (`Associate.primarySkill` /
   `secondarySkill`).
2. **Profile page → "Manage Skills"** — the structured taxonomy list, each entry a
   `Skill` + `Proficiency` (`AssociateSkill`), saved via `replaceSkills`.

The structured list is already the authoritative data (search, reports, matching,
and CSV import all use it), and the Profile page already labels the free-text pair
as *"Legacy Skills."* Yet the Add form still presents primary/secondary as the
primary input. This dual-source-of-truth, entered on two screens, is confusing and
lets the two representations drift apart.

## Goal

One skills experience: enter each skill with its proficiency in **one list, in the
Add/Edit form**, and mark **one** as *primary* with a star. Remove the
primary/secondary dropdowns. Preserve every existing downstream behavior.

## Non-goals (scope control)

- **CSV import is unchanged.** It keeps populating both the structured skills and
  the legacy text fields in bulk. It does not set a "primary" flag; a later UI edit
  normalizes that. Keeps this a single, tight feature.
- No new per-skill fields (years, last-used). That was the rejected "Full HRMS"
  option and can come later.
- Certifications editor is untouched.

## Design

### UX

- The Add/Edit Associate modal drops the **Primary skill** and **Secondary skill**
  `<select>`s.
- In their place: the structured skills editor (skill + proficiency rows) with a
  **star** control per row. Tapping a star marks that row primary; starring another
  moves the star (at most one primary).
- Skills remain **optional** on create (you can add an associate and flesh out
  skills later), matching today's behavior where primary/secondary were optional.
- The roster and Profile continue to show a "primary skill" headline — now derived,
  not hand-typed.

### Component reuse (architecture)

Extract the existing "Manage Skills" editor from `Profile.jsx` into a shared
`SkillEditor` component (skillId → proficiency, plus a primary flag). Use it in
**both** the Profile "Manage Skills" modal and the Add/Edit Associate modal. This
removes the split-brain and honors the "one source of truth per concept" rule in
AGENTS.md.

### Data model

- New field on `AssociateSkill`: `boolean primary` (Java field `primary`, DB column
  **`is_primary`** to avoid the SQL reserved word), default `false`.
- Invariant: **at most one** `AssociateSkill` per associate has `primary = true`.
  Enforced in the service layer (not a DB constraint, to keep H2 tests simple).
- `Associate.primarySkill` / `secondarySkill` (String) are **kept** but become
  **derived**, never hand-entered — preserving the roster headline, CSV import, and
  the `PositionService` text-match fallback (per the 2026-07 resolved decision).

### Derivation rules (applied on associate create/update and on `replaceSkills`)

Given the associate's skill list:

- `primarySkill` = the name of the skill flagged `primary`; if none is flagged but
  skills exist, fall back to the **highest-proficiency** skill
  (`Proficiency.ordinal()`, tie-break: first encountered).
- `secondarySkill` = the highest-proficiency skill that is **not** the primary;
  blank if there is only one skill.
- No skills → both blank.

### API / DTO changes

- `AssociateRequest`: remove the writable `primarySkill` and `secondarySkill`
  string inputs. Add an optional list:
  `List<SkillRating> skills`, where `SkillRating = { Long skillId, Proficiency
  proficiency, boolean primary }`. Omitting the list (null) leaves skills unchanged
  on update / empty on create — backward compatible for callers that don't send it.
- On create/update, the service persists the associate, replaces its skills from
  the list (reusing existing `replaceSkills` logic), applies the single-primary
  invariant, then derives and stores the two headline strings.
- `AssociateResponse` still exposes `primarySkill` / `secondarySkill` (now derived)
  so the roster needs no change. It also exposes each skill's `primary` flag so the
  editor can render the star on edit.
- Reject a request with **more than one** primary skill: `400` with a clear message
  ("Only one skill can be marked primary").

### Migration (first schema change since adopting Flyway)

- Dev (`ddl-auto=update`) and tests (H2 `create-drop`) pick up the new column
  automatically.
- Prod: add `V2__add_primary_skill_flag.sql`:
  `ALTER TABLE associate_skills ADD COLUMN is_primary boolean NOT NULL DEFAULT false;`
  (Confirm the exact table name from the V1 baseline before writing.)

## Testing (TDD)

Service tests (`AssociateService` / skill derivation):

- Starring a skill sets `primarySkill` to that skill's name.
- With no star, `primarySkill` derives to the highest-proficiency skill.
- `secondarySkill` derives to the next-highest, non-primary skill; blank with a
  single skill; both blank with no skills.
- A request marking **two** skills primary returns `400` (not silently fixed), so
  the UI's single-star guarantee is also enforced server-side.

API tests (`AssociateApiTest`):

- Create an associate with a skills list including a `primary` in a single request →
  `201`, and a subsequent GET returns the derived `primarySkill` and the skill's
  `primary` flag.
- Existing associate CRUD tests updated to the new request shape; full suite green.

Frontend build (Prettier/ESLint gate) stays clean.

## Backward compatibility & risks

- Associates imported via CSV have legacy text but no starred structured skill;
  they display fine, and a UI edit normalizes them. Accepted.
- Existing associates edited through the new form get their headline re-derived
  from their structured skills the first time they're saved.
- The `AssociateRequest` shape change breaks any client still sending
  `primarySkill`/`secondarySkill`; the only client is our own SPA, updated in the
  same change.
