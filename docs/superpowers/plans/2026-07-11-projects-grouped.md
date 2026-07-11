# Projects Page Grouped by Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat Projects table with collapsible per-client sections plus search and status filtering — frontend only.

**Architecture:** `Projects.jsx` keeps its data loads (`api.list('projects')`, `api.list('clients')`) and the entire create/edit modal, but renders grouped sections computed client-side. Collapse state lives in a `{clientId: true}` map; searching overrides collapse (auto-expands matches). No backend or API changes.

**Tech Stack:** React 18, existing `Badge`/`Icon`/`Field`/`Modal`/`SearchSelect` components, CSS tokens.

**Branch:** `feature/projects-grouped`. Spec: `docs/superpowers/specs/2026-07-11-projects-grouped-by-client-design.md`.

**Verification:** no frontend unit-test runner exists — the gate is `npm run format && npm run build` (Prettier + ESLint) plus a visual check; `./mvnw test` must stay green (no backend changes expected).

---

### Task 1: Rewrite the Projects page rendering

**Files:**
- Modify: `frontend/src/pages/Projects.jsx`

- [ ] **Step 1: Replace state, toolbar, and table with grouped sections.** Keep `EMPTY`, all modal state/handlers (`openCreate`, `openEdit`, `set`, `save`, `remove`, `createClient`, `clientOptions`) and the entire `<Modal>` block **verbatim**. Changes:

1. Imports: remove `DataTable`; everything else stays.
2. Replace the `clientFilter` state and the filtered load with an unfiltered load plus new UI state:

```jsx
const { data, loading, reload } = useLoad(() => api.list('projects'), []);
const { data: clients, reload: reloadClients } = useLoad(() => api.list('clients'));
const [search, setSearch] = useState('');
const [statusFilter, setStatusFilter] = useState('');
const [collapsed, setCollapsed] = useState({}); // clientId -> true
```

3. Grouping/filtering logic before the return (statuses: `ACTIVE`, `ON_HOLD`, `COMPLETED`):

```jsx
const q = search.trim().toLowerCase();
const projectMatches = (p) =>
  !q ||
  p.name.toLowerCase().includes(q) ||
  (p.code || '').toLowerCase().includes(q);

const sections = (clients || [])
  .map((client) => {
    const all = (data || []).filter((p) => p.clientId === client.id);
    const clientHit = q && client.name.toLowerCase().includes(q);
    const rows = all
      .filter((p) => !statusFilter || p.status === statusFilter)
      .filter((p) => clientHit || projectMatches(p))
      .sort(
        (a, b) =>
          (a.status === 'ACTIVE' ? 0 : 1) - (b.status === 'ACTIVE' ? 0 : 1) ||
          a.name.localeCompare(b.name)
      );
    return {
      client,
      rows,
      total: all.length,
      activeCount: all.filter((p) => p.status === 'ACTIVE').length,
    };
  })
  .filter((s) => (q ? s.rows.length > 0 || s.client.name.toLowerCase().includes(q) : true))
  .filter((s) => (statusFilter ? s.rows.length > 0 : true))
  .sort((a, b) => a.client.name.localeCompare(b.client.name));

// searching auto-expands every visible section
const isCollapsed = (clientId) => !q && !!collapsed[clientId];
const toggle = (clientId) => setCollapsed((c) => ({ ...c, [clientId]: !c[clientId] }));
```

4. Toolbar (replaces the client `<select>`):

```jsx
<div className="toolbar">
  <div className="toolbar-filters">
    <input
      className="filter-select"
      type="search"
      placeholder="Search clients, projects, or codes…"
      value={search}
      onChange={(e) => setSearch(e.target.value)}
      aria-label="Search projects"
    />
    <select
      className="filter-select"
      value={statusFilter}
      onChange={(e) => setStatusFilter(e.target.value)}
      aria-label="Filter by status"
    >
      <option value="">All statuses</option>
      <option value="ACTIVE">Active</option>
      <option value="ON_HOLD">On hold</option>
      <option value="COMPLETED">Completed</option>
    </select>
  </div>
  {canEdit && (
    <button className="btn btn-primary" onClick={openCreate}>
      <Icon name="plus" size={16} /> New Project
    </button>
  )}
</div>
```

5. Sections (replaces `<DataTable …/>`; loading keeps the skeleton idiom):

```jsx
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
      <p>No clients or projects found.</p>
    </div>
  </div>
) : (
  <div style={{ display: 'grid', gap: '16px' }}>
    {sections.map(({ client, rows, total, activeCount }) => (
      <div className="card" key={client.id} style={{ padding: '16px 20px' }}>
        <button
          onClick={() => toggle(client.id)}
          aria-expanded={!isCollapsed(client.id)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            width: '100%',
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            color: 'var(--color-foreground)',
            textAlign: 'left',
          }}
        >
          <span
            aria-hidden
            style={{
              display: 'inline-block',
              transition: 'transform 0.15s ease',
              transform: isCollapsed(client.id) ? 'none' : 'rotate(90deg)',
            }}
          >
            ▸
          </span>
          <span style={{ fontWeight: 700, fontSize: '15px' }}>{client.name}</span>
          <span className="cell-sub">
            {total === 0
              ? 'No projects yet'
              : `${total} project${total === 1 ? '' : 's'} · ${activeCount} active`}
          </span>
        </button>

        {!isCollapsed(client.id) && rows.length > 0 && (
          <div style={{ marginTop: '10px' }}>
            {rows.map((r) => (
              <div className="radar-row" key={r.id}>
                <div>
                  <div className="cell-main">{r.name}</div>
                  <div className="cell-sub">
                    {r.code} · {r.startDate || '—'} → {r.endDate || '—'}
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Badge value={r.status} />
                  {canEdit && (
                    <>
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => openEdit(r)}
                        aria-label={`Edit ${r.name}`}
                      >
                        <Icon name="edit" size={14} /> Edit
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => remove(r)}
                        aria-label={`Delete ${r.name}`}
                      >
                        <Icon name="trash" size={14} />
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    ))}
  </div>
)}
```

(Buttons/badges copy `DataTable`'s exact action idiom — `btn btn-ghost btn-sm` + `edit` icon, `btn btn-danger btn-sm` + `trash` icon. There is no chevron icon in `Icon.jsx`; the rotated `▸` span is deliberate.)

- [ ] **Step 2: Build gate**

Run: `cd frontend && npm run format && npm run build`
Expected: Prettier + ESLint clean, build succeeds (in particular: no unused `DataTable` import).

- [ ] **Step 3: Visual check** (if dev server available)

Run `./mvnw spring-boot:run` (seeded data), open `#/projects`: sections per client alphabetical, counts correct, collapse toggles, search hides/expands, status filter works, edit/delete/create modal flows unchanged.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Projects.jsx
git commit -m "feat: Projects page grouped by client — collapsible sections, search, status filter

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Backend regression + docs + graph

- [ ] **Step 1:** Run `./mvnw test` → all green (no backend change; regression only).
- [ ] **Step 2:** `docs/TECHNICAL.md`: if the frontend/pages section describes the Projects page, update its description to "grouped by client with search + status filter"; otherwise no change needed (check first — do not invent a section).
- [ ] **Step 3:** Graph refresh: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- [ ] **Step 4:** Commit any doc changes:

```bash
git add docs/TECHNICAL.md
git commit -m "docs: Projects page layout note

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
