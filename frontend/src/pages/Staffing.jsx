import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';
import Modal from '../components/Modal.jsx';
import AllocationForm from '../components/AllocationForm.jsx';
import CollapsibleCard from '../components/CollapsibleCard.jsx';

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

const getParam = (name) => {
  const searchPart = window.location.hash.split('?')[1];
  if (!searchPart) return '';
  return new URLSearchParams(searchPart).get(name) || '';
};

/**
 * The single Staffing & Allocations page: a client → project → associate tree,
 * read-only for viewers and inline-editable for admins. The tree and its billable
 * rollup come server-side from /staffing (one source of truth); edits reuse the
 * /allocations CRUD endpoints, so the capacity guard, uniqueness, and audit rules
 * all still apply.
 */
export default function Staffing({ showToast, canEdit }) {
  const [search, setSearch] = useState('');
  const [includeEnded, setIncludeEnded] = useState(false);
  // Which client sections are expanded. Seeded from the ?clientId= deep link.
  const [open, setOpen] = useState(() => {
    const fromLink = getParam('clientId');
    return fromLink ? { [fromLink]: true } : null; // null = "open the first one"
  });

  const {
    data: clients,
    loading,
    reload,
  } = useLoad(
    () => api.list('staffing', { includeEnded: includeEnded ? 'true' : '' }),
    [includeEnded]
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

  const openCreate = (prefill = {}) => {
    setErrors({});
    setEditing({ form: { ...EMPTY, ...prefill } });
  };
  const openEdit = (assoc, project) => {
    setErrors({});
    setEditing({
      id: assoc.allocationId,
      label: `${assoc.name} on ${project.projectName}`,
      form: {
        billable: assoc.billable,
        allocationPercent: assoc.allocationPercent,
        startDate: assoc.startDate,
        endDate: assoc.endDate || '',
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
      const fieldErrors = err.fieldErrors || {};
      setErrors({
        ...fieldErrors,
        _general: Object.keys(fieldErrors).length ? null : err.message,
      });
      showToast(err.message, true);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (assoc, project) => {
    if (!window.confirm(`Remove allocation of ${assoc.name} on ${project.projectName}?`)) return;
    try {
      await api.remove('allocations', assoc.allocationId);
      showToast('Allocation removed');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  // Client-side search over the server tree. A client- or project-name hit shows
  // everything beneath it; otherwise match associates by name. Server counts are the
  // authoritative current rollup, so they are left as-is even when the search narrows rows.
  const q = search.trim().toLowerCase();
  const sections = (clients || [])
    .map((c) => {
      const clientHit = q && c.clientName.toLowerCase().includes(q);
      const filteredProjects = c.projects
        .map((p) => {
          const projectHit =
            clientHit ||
            p.projectName.toLowerCase().includes(q) ||
            (p.projectCode || '').toLowerCase().includes(q);
          const associates = p.associates.filter(
            (a) => !q || projectHit || a.name.toLowerCase().includes(q)
          );
          return { ...p, associates };
        })
        .filter((p) => p.associates.length > 0);
      return { ...c, projects: filteredProjects };
    })
    .filter((c) => c.projects.length > 0);

  const isOpen = (clientId) =>
    !!q ||
    (open === null
      ? sections.length > 0 && String(clientId) === String(sections[0].clientId)
      : !!open[clientId]);
  const toggle = (clientId) =>
    setOpen((prev) => {
      const base =
        prev === null ? (sections.length > 0 ? { [sections[0].clientId]: true } : {}) : prev;
      return { ...base, [clientId]: !base[clientId] };
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
            aria-label="Search staffing"
          />
          <select
            className="filter-select"
            value={includeEnded ? 'all' : 'current'}
            onChange={(e) => setIncludeEnded(e.target.value === 'all')}
            aria-label="Filter by allocation state"
          >
            <option value="current">Current only</option>
            <option value="all">Include ended</option>
          </select>
        </div>
        {canEdit && (
          <button className="btn btn-primary" onClick={() => openCreate()}>
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
            <p>No current allocations. Assign an associate to a project to staff it.</p>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: '16px' }}>
          {sections.map((c) => (
            <CollapsibleCard
              key={c.clientId}
              open={isOpen(c.clientId)}
              onToggle={() => toggle(c.clientId)}
              header={
                <>
                  <h3 className="staffing-toggle-title">{c.clientName}</h3>
                  <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
                  <Badge
                    value="Non-billable"
                    label={`${c.nonBillable} non-billable`}
                    tone="amber"
                  />
                </>
              }
            >
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
                        {p.projectName} <span style={{ fontWeight: 400 }}>· {p.projectCode}</span>
                      </span>
                      <span style={{ fontWeight: 400 }}>
                        {p.billable} billable / {p.nonBillable} non-billable
                      </span>
                      {canEdit && (
                        <button
                          className="btn btn-ghost btn-sm"
                          style={{ marginLeft: 'auto' }}
                          onClick={() =>
                            openCreate({ companyId: c.clientId, projectId: p.projectId })
                          }
                          aria-label={`Assign an associate to ${p.projectName}`}
                        >
                          <Icon name="plus" size={14} /> Assign
                        </button>
                      )}
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
                            <th>Designation</th>
                            <th>Allocation</th>
                            <th>Billing</th>
                            <th>Since</th>
                            {includeEnded && <th>State</th>}
                            {canEdit && <th aria-label="Actions" />}
                          </tr>
                        </thead>
                        <tbody>
                          {p.associates.map((a) => (
                            <tr
                              key={a.allocationId}
                              style={a.active ? undefined : { opacity: 0.55 }}
                            >
                              <td>
                                <a className="cell-main" href={`#/associates/${a.associateId}`}>
                                  {a.name}
                                </a>
                              </td>
                              <td>{a.designation || '—'}</td>
                              <td>{a.allocationPercent}%</td>
                              <td>
                                <Badge value={a.billable ? 'Billable' : 'Non-billable'} />
                              </td>
                              <td>{a.startDate}</td>
                              {includeEnded && (
                                <td>
                                  <Badge value={a.active ? 'Current' : 'Ended'} />
                                </td>
                              )}
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
                                      onClick={() => openEdit(a, p)}
                                      aria-label={`Edit allocation of ${a.name}`}
                                    >
                                      <Icon name="edit" size={14} /> Edit
                                    </button>
                                    <button
                                      className="btn btn-danger btn-sm"
                                      onClick={() => remove(a, p)}
                                      aria-label={`Remove allocation of ${a.name}`}
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
            </CollapsibleCard>
          ))}
        </div>
      )}

      <AnimatePresence>
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
              setFields={(partial) =>
                setEditing((e) => ({ ...e, form: { ...e.form, ...partial } }))
              }
              errors={errors}
              projects={projects}
              searchAssociates={editing.id ? undefined : searchAssociates}
              showProjectPicker={!editing.id}
            />
          </Modal>
        )}
      </AnimatePresence>
    </>
  );
}
