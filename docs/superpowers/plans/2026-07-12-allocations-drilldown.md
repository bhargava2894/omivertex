# Allocations Drill-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the Allocations page from a flat table into the same client → project → rows drill-down as the Staffing page, keeping full CRUD and ended-allocation history.

**Architecture:** Frontend-only change to one page. `GET /allocations` stays as-is; the page joins each allocation's `projectId` against the already-loaded projects list to get `clientId` and project `code`, then groups client → project in plain page code (same style as `Projects.jsx`). The assign/edit modal, `AllocationForm`, and delete flow are untouched.

**Tech Stack:** React 18 + Vite. No new dependencies. Gate is Prettier + ESLint via `npm run build` (this project has no frontend unit-test harness — AGENTS.md TDD applies to the backend, which is untouched here; verification is the build gate plus a manual smoke check).

**Spec:** `docs/superpowers/specs/2026-07-12-allocations-drilldown-design.md`

---

### Task 1: Rewrite the Allocations page read view

**Files:**
- Modify: `frontend/src/pages/Allocations.jsx` (full rewrite of the read view; CRUD handlers and modal kept verbatim)

Key changes vs. the current file:
- Imports: drop `DataTable` and `SearchSelect` (both now unused — ESLint fails the build on unused imports); keep `Modal`, `Badge`, `Icon`, `AllocationForm`.
- Toolbar: text search replaces the project `SearchSelect` filter; the state select and Assign button stay.
- Read view: collapsible client cards (Staffing's `staffing-toggle` pattern) → project sub-headers ("Name · CODE") → nested table with Edit/Delete per row when `canEdit`.
- Expansion: first client open by default (`open === null` sentinel, as in `Staffing.jsx`); a non-empty search auto-expands everything.
- `openCreate`, `openEdit`, `set`, `save`, `remove`, `searchAssociates`, `EMPTY`, and the `<Modal>` block are **unchanged** from the current file.

- [ ] **Step 1: Replace the page content**

Replace the entire contents of `frontend/src/pages/Allocations.jsx` with:

```jsx
import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';
import AllocationForm from '../components/AllocationForm.jsx';

const today = () => new Date().toISOString().slice(0, 10);

const EMPTY = {
  associateId: '',
  companyId: '', // client filter for the project picker; not submitted
  projectId: '',
  billable: true,
  allocationPercent: 100,
  startDate: today(),
  endDate: '',
};

export default function Allocations({ showToast, canEdit }) {
  const [search, setSearch] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);
  // Which client sections are expanded; null = "open the first one" (as on Staffing).
  const [open, setOpen] = useState(null);

  const { data, loading, reload } = useLoad(
    () => api.list('allocations', { active: activeOnly ? 'true' : '' }),
    [activeOnly]
  );
  const { data: projects } = useLoad(() => api.list('projects'));

  // Associates are searched server-side (the roster can be 500+), so we don't
  // eagerly load them all — the picker queries as you type.
  const searchAssociates = (q) =>
    api.list('associates', { q, page: 0, size: 20 }).then((r) =>
      (r.content || []).map((a) => ({
        value: a.id,
        label: a.currentProject ? `${a.name} — on ${a.currentProject}` : `${a.name} — bench`,
      }))
    );

  const [editing, setEditing] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setErrors({});
    setEditing({ form: { ...EMPTY } });
  };
  const openEdit = (row) => {
    setErrors({});
    setEditing({
      id: row.id,
      label: `${row.associateName} on ${row.projectName}`,
      form: {
        billable: row.billable,
        allocationPercent: row.allocationPercent,
        startDate: row.startDate,
        endDate: row.endDate || '',
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    const f = editing.form;
    try {
      if (editing.id) {
        await api.update('allocations', editing.id, {
          billable: f.billable,
          allocationPercent: Number(f.allocationPercent),
          startDate: f.startDate,
          endDate: f.endDate || null,
        });
      } else {
        await api.create('allocations', {
          associateId: f.associateId === '' ? null : Number(f.associateId),
          projectId: f.projectId === '' ? null : Number(f.projectId),
          billable: f.billable,
          allocationPercent: Number(f.allocationPercent),
          startDate: f.startDate,
          endDate: f.endDate || null,
        });
      }
      showToast(editing.id ? 'Allocation updated' : 'Associate assigned');
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
  };

  const remove = async (row) => {
    if (!window.confirm(`Remove allocation of ${row.associateName} on ${row.projectName}?`)) return;
    try {
      await api.remove('allocations', row.id);
      showToast('Allocation removed');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  // --- client → project grouping (mirrors the Staffing drill-down) ---
  const q = search.trim().toLowerCase();
  const projectById = new Map((projects || []).map((p) => [p.id, p]));

  const clientMap = new Map();
  for (const r of data || []) {
    const project = projectById.get(r.projectId);
    // clientId comes from the projects list; fall back to the name while it loads
    const key = project ? `c${project.clientId}` : `n${r.clientName}`;
    if (!clientMap.has(key)) {
      clientMap.set(key, { key, name: r.clientName, byProject: new Map() });
    }
    const client = clientMap.get(key);
    if (!client.byProject.has(r.projectId)) {
      client.byProject.set(r.projectId, {
        projectId: r.projectId,
        name: r.projectName,
        code: project?.code || '',
        rows: [],
      });
    }
    client.byProject.get(r.projectId).rows.push(r);
  }

  // A client-name hit shows everything under that client (like the Projects page).
  const sections = [...clientMap.values()]
    .map((client) => {
      const clientHit = q && client.name.toLowerCase().includes(q);
      const projectSections = [...client.byProject.values()]
        .map((p) => {
          const projectHit =
            clientHit || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q);
          const rows = p.rows
            .filter((r) => !q || projectHit || r.associateName.toLowerCase().includes(q))
            .sort(
              (a, b) =>
                (a.active ? 0 : 1) - (b.active ? 0 : 1) ||
                a.associateName.localeCompare(b.associateName)
            );
          return { ...p, rows };
        })
        .filter((p) => p.rows.length > 0)
        .sort((a, b) => a.name.localeCompare(b.name));
      const rows = projectSections.flatMap((p) => p.rows);
      const billable = rows.filter((r) => r.billable).length;
      return {
        ...client,
        projects: projectSections,
        billable,
        nonBillable: rows.length - billable,
      };
    })
    .filter((c) => c.projects.length > 0)
    .sort((a, b) => a.name.localeCompare(b.name));

  // Searching auto-expands every visible section.
  const isOpen = (key) =>
    !!q || (open === null ? sections.length > 0 && key === sections[0].key : !!open[key]);
  const toggle = (key) =>
    setOpen((prev) => {
      const base = prev === null ? (sections.length > 0 ? { [sections[0].key]: true } : {}) : prev;
      return { ...base, [key]: !base[key] };
    });

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <input
            className="filter-select"
            type="search"
            placeholder="Search clients, projects, or associates…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search allocations"
          />
          <select
            className="filter-select"
            value={activeOnly ? 'active' : ''}
            onChange={(e) => setActiveOnly(e.target.value === 'active')}
            aria-label="Filter by allocation state"
          >
            <option value="active">Current only</option>
            <option value="">Including ended</option>
          </select>
        </div>
        {canEdit && (
          <button className="btn btn-primary" onClick={openCreate}>
            <Icon name="plus" size={16} /> Assign Associate
          </button>
        )}
      </div>

      {loading ? (
        <div>
          {[...Array(4)].map((_, i) => (
            <div key={i} className="skeleton-row" />
          ))}
        </div>
      ) : sections.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <Icon name="inbox" size={40} />
            <p>No allocations found. Assign an associate to a project.</p>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: '16px' }}>
          {sections.map((c) => (
            <div className="card" key={c.key} style={{ padding: 0, overflow: 'hidden' }}>
              <button
                type="button"
                className="staffing-toggle"
                onClick={() => toggle(c.key)}
                aria-expanded={isOpen(c.key)}
              >
                <span aria-hidden="true" className="staffing-toggle-arrow">
                  ▸
                </span>
                <h3 className="staffing-toggle-title">{c.name}</h3>
                <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
                <Badge value="Non-billable" label={`${c.nonBillable} non-billable`} tone="amber" />
              </button>

              {isOpen(c.key) && (
                <div style={{ padding: '0 20px 20px', display: 'grid', gap: '16px' }}>
                  {c.projects.map((p) => (
                    <div key={p.projectId}>
                      <div
                        className="cell-sub"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '10px',
                          fontWeight: 600,
                          margin: '8px 0',
                        }}
                      >
                        <span>
                          {p.name}
                          {p.code && <span style={{ fontWeight: 400 }}> · {p.code}</span>}
                        </span>
                      </div>
                      <div
                        className="table-wrap"
                        style={{
                          margin: 0,
                          boxShadow: 'none',
                          border: '1px solid var(--color-border)',
                        }}
                      >
                        <table style={{ fontSize: '13px' }}>
                          <thead>
                            <tr>
                              <th>Associate</th>
                              <th>Billing</th>
                              <th>Allocation</th>
                              <th>Start</th>
                              <th>End</th>
                              <th>State</th>
                              {canEdit && <th aria-label="Actions" />}
                            </tr>
                          </thead>
                          <tbody>
                            {p.rows.map((r) => (
                              <tr key={r.id}>
                                <td>
                                  <a className="cell-main" href={`#/associates/${r.associateId}`}>
                                    {r.associateName}
                                  </a>
                                </td>
                                <td>
                                  <Badge value={r.billable ? 'Billable' : 'Non-billable'} />
                                </td>
                                <td>{r.allocationPercent}%</td>
                                <td>{r.startDate}</td>
                                <td>{r.endDate || '—'}</td>
                                <td>
                                  <Badge value={r.active ? 'Current' : 'Ended'} />
                                </td>
                                {canEdit && (
                                  <td>
                                    <div
                                      style={{
                                        display: 'flex',
                                        gap: '8px',
                                        justifyContent: 'flex-end',
                                      }}
                                    >
                                      <button
                                        className="btn btn-ghost btn-sm"
                                        onClick={() => openEdit(r)}
                                        aria-label={`Edit allocation of ${r.associateName}`}
                                      >
                                        <Icon name="edit" size={14} /> Edit
                                      </button>
                                      <button
                                        className="btn btn-danger btn-sm"
                                        onClick={() => remove(r)}
                                        aria-label={`Remove allocation of ${r.associateName}`}
                                      >
                                        <Icon name="trash" size={14} />
                                      </button>
                                    </div>
                                  </td>
                                )}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {editing && (
        <Modal
          title={editing.id ? `Edit · ${editing.label}` : 'Assign Associate to Project'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : editing.id ? 'Save Changes' : 'Assign'}
              </button>
            </>
          }
        >
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <AllocationForm
            form={editing.form}
            setField={set}
            setFields={(partial) => setEditing((e) => ({ ...e, form: { ...e.form, ...partial } }))}
            errors={errors}
            projects={projects}
            searchAssociates={editing.id ? undefined : searchAssociates}
            showProjectPicker={!editing.id}
          />
        </Modal>
      )}
    </>
  );
}
```

- [ ] **Step 2: Format**

Run: `cd frontend && npm run format`
Expected: Prettier rewrites/normalizes the file with no errors.

- [ ] **Step 3: Build (Prettier check + ESLint + Vite)**

Run: `cd frontend && npm run build`
Expected: build succeeds. ESLint must report **no errors** (advisory React-Compiler-era warnings are acceptable per AGENTS.md). If it flags an unused import, an import was not removed — fix and rerun.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Allocations.jsx
git commit -m "feat: Allocations page as client -> project drill-down (matches Staffing)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Record the decision in docs

**Files:**
- Modify: `docs/TODO.md` (the `## Resolved decisions` section)

`docs/TECHNICAL.md` is intentionally untouched: the API contract did not change (`GET /allocations` still accepts `projectId` and `active`; the frontend simply stopped sending `projectId`).

- [ ] **Step 1: Append a resolved decision**

Add this bullet at the **end** of the `## Resolved decisions` section in `docs/TODO.md`:

```markdown
- **Allocations page is a grouped drill-down** (2026-07-12): the Allocations page
  mirrors the Staffing client → project → rows drill-down (collapsible client
  cards, search auto-expands, "Current only / Including ended" kept, CRUD per
  row). The project dropdown filter was removed — grouping + search replace it.
  Grouping is client-side from `GET /allocations` joined to the projects list
  for `clientId`/`code` (no backend change); the endpoint's `projectId` param
  remains for API consumers.
```

- [ ] **Step 2: Commit**

```bash
git add docs/TODO.md
git commit -m "docs: record Allocations drill-down decision

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Definition of Done — verify, refresh graph

**Files:** none created/modified (verification only; graph output is not git-tracked).

- [ ] **Step 1: Full backend suite (Spotless + ArchUnit included)**

Run: `./mvnw test` (from the repo root)
Expected: BUILD SUCCESS, all tests green. Backend was untouched, so any failure is pre-existing — stop and report rather than "fixing" unrelated code.

- [ ] **Step 2: Refresh the knowledge graph**

Run (from the repo root):

```bash
$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```

Expected: rebuild completes without error. `graphify-out/` is not tracked by git — nothing to commit.

- [ ] **Step 3: Manual smoke check (if a running environment is available)**

Start the app (backend + `cd frontend && npm run dev`) and verify on `#/allocations`:
1. Clients render as collapsible cards; first one open, others collapsed.
2. Typing in search matches client / project / code / associate names and auto-expands; clearing restores toggles.
3. "Including ended" shows Ended rows with an End date and `Ended` badge.
4. Edit and Delete on a nested row open the existing modal / confirm flow and work.
5. As a read-only User (viewer role): no Assign button, no Edit/Delete column.
6. Associate name links navigate to the associate detail page.

If no environment is available, note the skipped check in the final report (per AGENTS.md's Definition of Done handoff rule).
