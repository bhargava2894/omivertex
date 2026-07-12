# Motion polish — shared collapsible drill-down, Modal & Toast transitions

**Date:** 2026-07-12 · **Status:** Approved

## What & why

framer-motion is bundled but used only on Login and the dashboard charts. The
three interactions users touch most — drill-down expand/collapse (Staffing,
Projects, Allocations), modal open/close, toast dismiss — all snap instantly.
This work animates them and, in the same pass, extracts the triplicated
drill-down scaffolding into one shared component, resolving the
"third copy of the drill-down scaffolding" note in `docs/TODO.md`.

framer-motion is chosen over CSS here because these need what CSS can't do:
animating `height: auto`, exit animations on unmount (`AnimatePresence`), and
shared variant tokens.

## New shared modules

### `frontend/src/motion.js`

Single source of motion truth (AGENTS.md: one shared module per cross-cutting
concern):

- Duration tokens: `ENTER = 0.22`, `EXIT = 0.15` (seconds); one shared ease
  (`[0.4, 0, 0.2, 1]`, matching the CSS `--ease` feel).
- Named variant sets: `collapse` (height 0 ↔ 'auto' + opacity),
  `modalPop` (overlay fade; dialog scale 0.96→1 + fade, exit ~0.12s),
  `toastSlide` (slide-up + fade in, slide-down + fade out).
- A `useMotionVariants(variants)` hook that returns the variants with all
  durations forced to 0 when framer's `useReducedMotion()` reports true.
  **This is required for accessibility:** the global CSS kill-switch
  (`styles.css` `@media (prefers-reduced-motion)`) does not affect
  framer-motion, which animates via inline styles.

No page defines its own durations or easings; everything imports from here.

### `frontend/src/components/CollapsibleCard.jsx`

Controlled presentational component; expansion state stays in the pages.

- Props: `open` (bool), `onToggle` (fn), `header` (node), `children`.
- Renders: the `card` shell; a full-width header `<button type="button">`
  with `aria-expanded`, a chevron in a `motion.span` (rotates 0→90° when
  open), then the `header` slot content; body wrapped in `AnimatePresence`
  with a `motion.div` using the `collapse` variants
  (`overflow: hidden` while animating).
- One unified header style based on the current `.staffing-toggle` look.
  Page-specific header content (billable/non-billable badges, count pills,
  "No projects yet" subtext) passes in through the `header` slot.

## Page refactors (behavior unchanged)

- **Staffing.jsx / Allocations.jsx / Projects.jsx** replace their hand-rolled
  header + conditional body markup with `CollapsibleCard`. Expansion policies
  are untouched: Staffing/Allocations keep the "first client open" null
  sentinel (and Staffing's `?clientId=` deep-link seed; Allocations' search
  auto-expand), Projects keeps its all-open-by-default collapsed map and
  search auto-expand.
- CSS: consolidate the header styles — keep one set of classes for the shared
  header (derived from `.staffing-toggle*`), delete the then-unused
  `.client-header` / `.client-chevron` rules if nothing else uses them.
  Colors remain CSS tokens only.

## Modal

- `Modal.jsx` becomes motion-based: `motion.div` overlay (opacity fade) and
  dialog (scale 0.96→1 + fade in at ENTER; exit at ~0.12s — exits faster than
  enters). Variants come from `motion.js`; reduced motion renders instantly.
- Exit animations require `AnimatePresence` around the conditional render, so
  every call site changes mechanically from `{editing && <Modal…>}` to
  `<AnimatePresence>{editing && <Modal…>}</AnimatePresence>`. All call sites
  across `frontend/src/pages/` and components are updated in one pass.
- Remove the CSS `animation: pop-in` from `.modal` (double animation
  otherwise). `SearchSelect`'s `.menu` pop-in stays CSS.
- Modal's API (`title`, `onClose`, `children`, `footer`, `size`) is unchanged.

## Toast

- The single toast render in `App.jsx` wraps in `AnimatePresence`; the toast
  becomes a `motion.div` using `toastSlide`. Remove its CSS entrance
  animation if one applies. Timing/dismiss logic (3.5s) unchanged.

## Out of scope

Route/page transitions, DataTable row layout animations, dashboard tile
stagger/count-ups, AssistantChat message animations, SearchSelect menu
conversion. Each was deliberately excluded from this pass.

## Verification

- No backend change; `./mvnw test` must stay green (DoD).
- Frontend gate: `cd frontend && npm run format && npm run build`.
- Live smoke check (headless Chrome against the dev servers):
  1. Expand/collapse animates on Staffing, Projects, and Allocations; content
     ends fully visible (no clipped height).
  2. Search auto-expand still works on Projects and Allocations; Staffing
     deep-link still opens the right client.
  3. Modal opens/cancels with animation on at least one page; save flow still
     works; Esc and overlay-click still close.
  4. Toast animates in and out.
  5. Viewer role unaffected.
  6. With reduced motion emulated (`page.emulateMedia({ reducedMotion:
     'reduce' })`), interactions complete instantly.

## Docs

- `docs/TODO.md` → "Resolved decisions": drill-down scaffolding extracted to
  `CollapsibleCard` + `motion.js` tokens (supersedes the "if a fourth page
  adopts it" note); motion conventions recorded (all animation via motion.js
  tokens, reduced-motion via `useMotionVariants`).
- `docs/TECHNICAL.md`: update the frontend components list to include
  `CollapsibleCard` and `motion.js`.
