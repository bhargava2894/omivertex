# MyProfile — read-only "My Details" block

**Date:** 2026-07-16 · **Status:** approved

## What & why

The ASSOCIATE self-service page (`MyProfile.jsx`) today only surfaces the two things
an associate can *change* (skills, resume) plus a thin header line. It never presents
their own basic identity/contact info as a first-class, clearly **read-only** block.
Associates should see who the system thinks they are — email, company, location, work
mode, designation, tenure — and understand those fields are admin-managed, not editable
by them.

Scope is deliberately tight: **contact & basic info only.** No project history, no
current-engagement panel, no certifications (considered and dropped for this pass).

## What we're building

A new **read-only "My Details" card panel** in `MyProfile.jsx`, placed immediately
after the profile header and *above* the editable "My Skills" panel, so the
non-editable identity block reads first and the "propose a change" panels follow.

Fields (a two-column label/value grid), all already present in the `/me/profile`
(`AssociateResponse`) payload — **no backend change, no new endpoint:**

- Email
- Company
- Location
- Work mode (Onshore / Offshore)
- Designation
- Joined date + tenure (e.g. "12 Mar 2023 · 3 yr 4 mo") — tenure computed client-side
  from `joinedDate`; omit the tenure suffix when `joinedDate` is absent
- Status (Active / Inactive)

**Read-only affordance:** the panel carries a `lock` icon and a muted caption —
"Managed by your admin — contact HR to update" — and renders **no inputs and no
buttons**, visually distinguishing it from the Skills/Resume panels.

**Header cleanup:** the header line currently repeats `designation · email · company`.
Slim it to name + work-mode/current-project badges only, letting the new Details card
own the granular fields (removes duplication).

## Decisions

- **All data from the existing payload.** `AssociateResponse` already carries every
  field, so this is a pure frontend addition. Tenure is derived on the client from
  `joinedDate`; nothing new crosses the wire.
- **Styling via existing tokens/classes.** Reuse `card panel`, `cell-sub`, `cell-main`,
  and CSS custom properties — no raw hex, dark mode automatic.
- **Read-only is enforced by absence.** The block has no controls at all; there is no
  new mutation surface to guard. The server already refuses associate writes to these
  fields.

## Testing / verification

Purely additive frontend; the `/me/profile` contract is already covered by the backend
`associate_seesOwnProfile` test. Verification is a live check: sign in as an associate,
confirm the "My Details" block renders the correct values, shows the read-only caption,
and offers no edit controls; toggle dark mode.

## Out of scope

Project history, current-engagement details, certifications view, and any ability for an
associate to edit these fields.
