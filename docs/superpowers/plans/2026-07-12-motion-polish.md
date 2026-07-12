# Motion Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Animate the three highest-traffic interactions (drill-down expand/collapse, modal open/close, toast dismiss) with framer-motion, extracting the triplicated drill-down scaffolding into one shared `CollapsibleCard` component.

**Architecture:** A new `frontend/src/motion.js` holds all duration/easing tokens and variant sets plus a `useMotionVariants` hook that zeroes durations under `prefers-reduced-motion` (framer-motion ignores the CSS kill-switch). A new controlled `CollapsibleCard` component (header slot + `AnimatePresence` height-auto body) replaces the hand-rolled card markup in Staffing, Allocations, and Projects; expansion state stays in the pages. `Modal.jsx` becomes motion-based with `AnimatePresence` wrapped around each of its 14 call sites; the single toast in `App.jsx` gets enter/exit animation.

**Tech Stack:** React 18 + Vite, framer-motion ^12.42.2 (already installed). Gate: Prettier + ESLint via `npm run build`; no frontend unit-test harness (AGENTS.md TDD applies to the backend, untouched here). Chevron rotation stays CSS-driven (`.staffing-toggle[aria-expanded='true']` rule) so the existing CSS reduced-motion kill-switch covers it.

**Spec:** `docs/superpowers/specs/2026-07-12-motion-polish-design.md`

All commits end with:

```
Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

---

### Task 1: motion.js tokens + CollapsibleCard component

**Files:**
- Create: `frontend/src/motion.js`
- Create: `frontend/src/components/CollapsibleCard.jsx`

- [ ] **Step 1: Create `frontend/src/motion.js`**

```js
import { useReducedMotion } from 'framer-motion';

// Single source of motion truth (AGENTS.md: one shared module per
// cross-cutting concern). No page defines its own durations or easings.
export const EASE = [0.4, 0, 0.2, 1]; // matches the CSS --ease feel
export const ENTER = 0.22;
export const EXIT = 0.15;

export const collapse = {
  initial: { height: 0, opacity: 0 },
  animate: { height: 'auto', opacity: 1, transition: { duration: ENTER, ease: EASE } },
  exit: { height: 0, opacity: 0, transition: { duration: EXIT, ease: EASE } },
};

export const modalPop = {
  overlay: {
    initial: { opacity: 0 },
    animate: { opacity: 1, transition: { duration: 0.18, ease: EASE } },
    exit: { opacity: 0, transition: { duration: 0.12, ease: EASE } },
  },
  dialog: {
    initial: { opacity: 0, scale: 0.96, y: 10 },
    animate: { opacity: 1, scale: 1, y: 0, transition: { duration: 0.18, ease: EASE } },
    exit: { opacity: 0, scale: 0.96, y: 0, transition: { duration: 0.12, ease: EASE } },
  },
};

export const toastSlide = {
  initial: { opacity: 0, y: 16 },
  animate: { opacity: 1, y: 0, transition: { duration: ENTER, ease: EASE } },
  exit: { opacity: 0, y: 16, transition: { duration: EXIT, ease: EASE } },
};

function withZeroDurations(node) {
  if (Array.isArray(node) || node === null || typeof node !== 'object') return node;
  const out = {};
  for (const [k, v] of Object.entries(node)) out[k] = withZeroDurations(v);
  if ('duration' in out) out.duration = 0;
  return out;
}

// The CSS reduced-motion kill-switch can't reach framer-motion (it animates
// inline styles), so every animated component gets its variants through here.
export function useMotionVariants(variants) {
  const reduce = useReducedMotion();
  return reduce ? withZeroDurations(variants) : variants;
}
```

- [ ] **Step 2: Create `frontend/src/components/CollapsibleCard.jsx`**

Controlled component; expansion state lives in the page. Chevron rotation is
CSS (`.staffing-toggle[aria-expanded='true'] .staffing-toggle-arrow`), already
in `styles.css`.

```jsx
import { AnimatePresence, motion } from 'framer-motion';
import { collapse, useMotionVariants } from '../motion.js';

export default function CollapsibleCard({ open, onToggle, header, children }) {
  const anim = useMotionVariants(collapse);
  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
      <button type="button" className="staffing-toggle" onClick={onToggle} aria-expanded={open}>
        <span aria-hidden="true" className="staffing-toggle-arrow">
          ▸
        </span>
        {header}
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={anim.initial}
            animate={anim.animate}
            exit={anim.exit}
            style={{ overflow: 'hidden' }}
          >
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
```

Note `<AnimatePresence initial={false}>`: sections that are open on first
render (e.g. the default-open first client) must not play an entrance
animation on page load.

- [ ] **Step 3: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds; ESLint has no errors. (The new files are not yet
imported anywhere — that's fine, ESLint only flags unused *imports*.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/motion.js frontend/src/components/CollapsibleCard.jsx
git commit -m "feat: shared motion tokens and animated CollapsibleCard"
```

---

### Task 2: Staffing.jsx onto CollapsibleCard

**Files:**
- Modify: `frontend/src/pages/Staffing.jsx` (render block, currently lines 50–133)

Behavior must not change: `isOpen`/`toggle`/deep-link logic stays exactly as
is; only the card markup is replaced.

- [ ] **Step 1: Add the import**

At the top of `Staffing.jsx`, add:

```jsx
import CollapsibleCard from '../components/CollapsibleCard.jsx';
```

- [ ] **Step 2: Replace the card markup**

Replace the entire `return (...)` block (the `<div style={{ display: 'grid', gap: '16px' }}>`
wrapper and everything inside it, currently lines 50–133) with:

```jsx
  return (
    <div style={{ display: 'grid', gap: '16px' }}>
      {clients.map((c) => (
        <CollapsibleCard
          key={c.clientId}
          open={isOpen(c.clientId)}
          onToggle={() => toggle(c.clientId)}
          header={
            <>
              <h3 className="staffing-toggle-title">{c.clientName}</h3>
              <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
              <Badge value="Non-billable" label={`${c.nonBillable} non-billable`} tone="amber" />
            </>
          }
        >
          <div style={{ padding: '0 20px 20px', display: 'grid', gap: '16px' }}>
            {c.projects.map((p) => (
              /* keep the existing project block (lines 70–127: the
                 project sub-header div and the .table-wrap table)
                 EXACTLY as it is today — unchanged, just re-indented */
            ))}
          </div>
        </CollapsibleCard>
      ))}
    </div>
  );
```

The `c.projects.map((p) => (...))` body — the project sub-header `<div className="cell-sub" …>`
and the `<div className="table-wrap" …><table…>…</table></div>` — is moved verbatim from the
current file (lines 70–127). Do not edit its contents.

- [ ] **Step 3: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Staffing.jsx
git commit -m "refactor: Staffing drill-down uses shared CollapsibleCard"
```

---

### Task 3: Allocations.jsx onto CollapsibleCard

**Files:**
- Modify: `frontend/src/pages/Allocations.jsx` (the sections render, currently lines ~247–331)

Behavior must not change: grouping, `isOpen`/`toggle` (null sentinel + search
auto-expand), all filters, and the CRUD/modal block stay exactly as they are.

- [ ] **Step 1: Add the import**

```jsx
import CollapsibleCard from '../components/CollapsibleCard.jsx';
```

- [ ] **Step 2: Replace the card markup**

In the sections render (inside `<div style={{ display: 'grid', gap: '16px' }}>`),
replace each section's card:

```jsx
          {sections.map((c) => (
            <CollapsibleCard
              key={c.key}
              open={isOpen(c.key)}
              onToggle={() => toggle(c.key)}
              header={
                <>
                  <h3 className="staffing-toggle-title">{c.name}</h3>
                  <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
                  <Badge value="Non-billable" label={`${c.nonBillable} non-billable`} tone="amber" />
                </>
              }
            >
              <div style={{ padding: '0 20px 20px', display: 'grid', gap: '16px' }}>
                {c.projects.map((p) => (
                  /* keep the existing project block (the "Name · CODE"
                     sub-header div and the .table-wrap table with the
                     Edit/Delete action cells) EXACTLY as it is today */
                ))}
              </div>
            </CollapsibleCard>
          ))}
```

This removes the old `<div className="card" …>` wrapper, the
`<button className="staffing-toggle" …>` header, and the `{isOpen(c.key) && (…)}`
conditional — `CollapsibleCard` now owns all three. The `c.projects.map((p) => (...))`
body (project sub-header + nested table, currently lines ~271–325) moves verbatim.

- [ ] **Step 3: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Allocations.jsx
git commit -m "refactor: Allocations drill-down uses shared CollapsibleCard"
```

---

### Task 4: Projects.jsx onto CollapsibleCard + CSS consolidation

**Files:**
- Modify: `frontend/src/pages/Projects.jsx` (sections render, currently lines 162–228)
- Modify: `frontend/src/styles.css` (delete `.client-section`, `.client-header`, `.client-header:hover`, `.client-chevron` — currently lines 351–374)

Projects keeps its own expansion policy (all open by default via the
`collapsed` map, search auto-expands) — only markup changes. The page adopts
the unified header shell; `.client-icon`, `.client-name`, `.client-count-pill`,
and `.client-projects` are header/body *content* classes and stay.

- [ ] **Step 1: Add the import**

```jsx
import CollapsibleCard from '../components/CollapsibleCard.jsx';
```

- [ ] **Step 2: Replace the section markup**

Replace the `sections.map` block (currently lines 163–227: the
`<div className="card client-section">` card, its `client-header` button, and
the `{!isCollapsed(...) && ...}` body) with:

```jsx
          {sections.map(({ client, rows, total, activeCount }) => (
            <CollapsibleCard
              key={client.id}
              open={!isCollapsed(client.id)}
              onToggle={() => toggle(client.id)}
              header={
                <>
                  <Icon name="briefcase" size={16} className="client-icon" />
                  <span className="client-name">{client.name}</span>
                  {total === 0 ? (
                    <span className="cell-sub">No projects yet</span>
                  ) : (
                    <>
                      <span className="client-count-pill">{total}</span>
                      <span className="cell-sub">{activeCount} active</span>
                    </>
                  )}
                </>
              }
            >
              {rows.length > 0 && (
                <div className="client-projects">
                  {rows.map((r) => (
                    /* keep the existing .radar-row block (name, code/date
                       cell-sub, Badge, Edit/Delete buttons) EXACTLY as it
                       is today (lines 193–223) */
                  ))}
                </div>
              )}
            </CollapsibleCard>
          ))}
```

The manual chevron `<span className="client-chevron" … transform: rotate(90deg)>`
is deleted — `CollapsibleCard`'s CSS-driven chevron replaces it.

- [ ] **Step 3: Delete the now-unused CSS**

In `frontend/src/styles.css`, delete these rules (currently lines 351–374):
`.client-section { … }`, `.client-header { … }`, `.client-header:hover { … }`,
`.client-chevron { … }`. Before deleting, verify nothing else references them:

Run: `cd frontend && grep -rn "client-section\|client-header\|client-chevron" src/`
Expected: no matches outside `styles.css` after the Projects.jsx edit.

- [ ] **Step 4: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/Projects.jsx frontend/src/styles.css
git commit -m "refactor: Projects drill-down uses shared CollapsibleCard"
```

---

### Task 5: Motion-based Modal + AnimatePresence at all call sites

**Files:**
- Modify: `frontend/src/components/Modal.jsx` (full replacement below)
- Modify: `frontend/src/styles.css` (remove `animation: pop-in 0.2s var(--ease);` from the `.modal` rule, currently line ~524)
- Modify (mechanical wrap, listed below): `frontend/src/components/DataTransfer.jsx:154`, `frontend/src/pages/Allocations.jsx`, `frontend/src/pages/Profile.jsx` (4 modals), `frontend/src/pages/ProfileChanges.jsx:206`, `frontend/src/pages/Clients.jsx:107`, `frontend/src/pages/Associates.jsx:430`, `frontend/src/pages/Projects.jsx:232`, `frontend/src/pages/SkillReports.jsx:323`, `frontend/src/pages/MyProfile.jsx:233`, `frontend/src/pages/Positions.jsx` (2 modals)

- [ ] **Step 1: Replace `frontend/src/components/Modal.jsx` with:**

```jsx
import { useEffect } from 'react';
import { motion } from 'framer-motion';
import { modalPop, useMotionVariants } from '../motion.js';
import Icon from './Icon.jsx';

export default function Modal({ title, onClose, children, footer, size }) {
  const anim = useMotionVariants(modalPop);
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <motion.div
      className="modal-overlay"
      initial={anim.overlay.initial}
      animate={anim.overlay.animate}
      exit={anim.overlay.exit}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        className={`modal ${size === 'lg' ? 'modal-lg' : ''}`}
        initial={anim.dialog.initial}
        animate={anim.dialog.animate}
        exit={anim.dialog.exit}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close dialog">
            <Icon name="x" size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </motion.div>
    </motion.div>
  );
}
```

- [ ] **Step 2: Remove the CSS entrance animation from `.modal`**

In `frontend/src/styles.css`, delete only the line
`animation: pop-in 0.2s var(--ease);` inside the `.modal { … }` rule.
Keep the `@keyframes pop-in` block and the `.menu` usage — `SearchSelect`'s
dropdown still uses it.

- [ ] **Step 3: Wrap every Modal call site in AnimatePresence**

Exit animations only run if `AnimatePresence` stays mounted around the
conditional. For **each** call site listed in **Files** above, apply this
transformation:

```jsx
// BEFORE (example: frontend/src/pages/Clients.jsx:107)
      {editing && (
        <Modal title={…} onClose={…} footer={…}>
          …
        </Modal>
      )}

// AFTER
      <AnimatePresence>
        {editing && (
          <Modal title={…} onClose={…} footer={…}>
            …
          </Modal>
        )}
      </AnimatePresence>
```

and add to that file's imports:

```jsx
import { AnimatePresence } from 'framer-motion';
```

Notes:
- `Profile.jsx` has 4 modals and `Positions.jsx` has 2 — wrap each
  conditional individually.
- The condition variable differs per site (`editing`, `confirming`, etc.) —
  wrap whatever conditional expression renders that `<Modal>`; do not change
  the condition itself.
- Find every site with: `grep -rn "<Modal" frontend/src | grep -v components/Modal.jsx`
  and confirm all 14 are wrapped.

- [ ] **Step 4: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors (an unwrapped site won't fail the build —
re-check with the grep in Step 3).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/Modal.jsx frontend/src/styles.css \
  frontend/src/components/DataTransfer.jsx frontend/src/pages
git commit -m "feat: animated modal open/close via AnimatePresence"
```

---

### Task 6: Animated toast

**Files:**
- Modify: `frontend/src/App.jsx` (toast render, currently lines 320–324, plus imports)
- Modify: `frontend/src/styles.css` (remove `animation: pop-in 0.2s var(--ease);` from the `.toast` rule, currently line ~735)

- [ ] **Step 1: Update App.jsx**

Add to imports:

```jsx
import { AnimatePresence, motion } from 'framer-motion';
import { toastSlide, useMotionVariants } from './motion.js';
```

Inside the `App` component body (it's a function component; any place before
the return works, next to the other hooks):

```jsx
  const toastAnim = useMotionVariants(toastSlide);
```

Replace the toast render:

```jsx
// BEFORE
      {toast && (
        <div className={`toast ${toast.isError ? 'error' : ''}`} role="status" aria-live="polite">
          {toast.message}
        </div>
      )}

// AFTER
      <AnimatePresence>
        {toast && (
          <motion.div
            className={`toast ${toast.isError ? 'error' : ''}`}
            role="status"
            aria-live="polite"
            initial={toastAnim.initial}
            animate={toastAnim.animate}
            exit={toastAnim.exit}
          >
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>
```

The 3.5s auto-dismiss logic in `showToast` is unchanged.

- [ ] **Step 2: Remove the CSS entrance animation from `.toast`**

In `frontend/src/styles.css`, delete only the line
`animation: pop-in 0.2s var(--ease);` inside the `.toast { … }` rule.

- [ ] **Step 3: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.jsx frontend/src/styles.css
git commit -m "feat: toast slides in and out"
```

---

### Task 7: Docs

**Files:**
- Modify: `docs/TODO.md` (`## Resolved decisions` section)
- Modify: `docs/TECHNICAL.md` (frontend components list, currently around line 71)

- [ ] **Step 1: Update the drill-down decision in TODO.md**

Append at the end of `## Resolved decisions`:

```markdown
- **Drill-down scaffolding extracted; motion via shared tokens** (2026-07-12):
  the client-card expand/collapse shell (header button, chevron, animated
  body) now lives in `CollapsibleCard.jsx`, used by Staffing, Projects, and
  Allocations — this supersedes the "if a fourth page adopts it, extract"
  note above. All framer-motion animation goes through `frontend/src/motion.js`
  tokens/variants and its `useMotionVariants` reduced-motion hook (the CSS
  `prefers-reduced-motion` kill-switch cannot reach framer-motion's inline
  styles). Route transitions, DataTable row animations, and dashboard tile
  staggers were deliberately excluded.
```

- [ ] **Step 2: Update the components list in TECHNICAL.md**

In the frontend tree (around line 71), extend the components line to include
the new modules, e.g.:

```
│     ├─ motion.js                shared animation tokens/variants + reduced-motion hook
│     ├─ components/              Icon, Modal, Badge, DataTable, Field, CollapsibleCard,
│     │                           DataTransfer (import/export), charts.jsx
```

(Match the file's existing box-drawing formatting.)

- [ ] **Step 3: Commit**

```bash
git add docs/TODO.md docs/TECHNICAL.md
git commit -m "docs: record CollapsibleCard extraction and motion conventions"
```

---

### Task 8: Definition of Done — verify, refresh graph, live smoke check

**Files:** none (verification only; `graphify-out/` is not git-tracked).

- [ ] **Step 1: Full backend suite**

Run: `./mvnw test` (repo root)
Expected: BUILD SUCCESS. Backend untouched — any failure is pre-existing;
stop and report rather than fixing unrelated code.

- [ ] **Step 2: Refresh the knowledge graph**

```bash
$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```

- [ ] **Step 3: Live smoke check (dev servers run on :8080 / :5173; login admin/Admin@123, viewer/Viewer@123)**

Drive `http://localhost:5173` (headless Chrome / Playwright with the system
Chrome executable works; see `scratchpad/smoke.mjs` pattern from the previous
feature if available):

1. Staffing, Projects, Allocations: clicking a client header animates the body
   open/closed (height + fade); content ends fully visible, no clipped rows.
2. Expansion policies unchanged: first client open on Staffing/Allocations,
   all open on Projects; search auto-expand still works on Projects and
   Allocations; `#/staffing?clientId=<id>` still opens that client.
3. Open and cancel a modal on at least two pages (e.g. Allocations edit,
   Projects new): overlay fades, dialog scales in, close animates out; Esc and
   overlay-click still close; a full save flow still works.
4. Trigger a toast (e.g. save an edit): slides in, and slides out ~3.5s later.
5. Viewer role: pages render, no mutation controls (unchanged).
6. Reduced motion: with `page.emulateMedia({ reducedMotion: 'reduce' })`,
   expand/collapse, modal, and toast all appear/disappear instantly.

If no environment is available, note the skipped check in the final report
(AGENTS.md Definition of Done handoff rule).
