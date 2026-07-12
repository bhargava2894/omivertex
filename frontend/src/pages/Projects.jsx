import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import SearchSelect from '../components/SearchSelect.jsx';
import CollapsibleCard from '../components/CollapsibleCard.jsx';

const EMPTY = { code: '', name: '', clientId: '', status: 'ACTIVE', startDate: '', endDate: '' };

export default function Projects({ showToast, canEdit }) {
  const { data, loading, reload } = useLoad(() => api.list('projects'), []);
  const { data: clients, reload: reloadClients } = useLoad(() => api.list('clients'));
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [collapsed, setCollapsed] = useState({}); // clientId -> true

  const clientOptions = (clients || []).map((c) => ({ value: c.id, label: c.name }));
  // Inline "add client": create it, refresh the list, and return the new option.
  const createClient = async (name) => {
    const created = await api.create('clients', { name });
    await reloadClients();
    return { value: created.id, label: created.name };
  };
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
      form: {
        code: row.code,
        name: row.name,
        clientId: row.clientId,
        status: row.status,
        startDate: row.startDate || '',
        endDate: row.endDate || '',
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    const payload = {
      ...editing.form,
      clientId: editing.form.clientId === '' ? null : Number(editing.form.clientId),
      startDate: editing.form.startDate || null,
      endDate: editing.form.endDate || null,
    };
    try {
      if (editing.id) await api.update('projects', editing.id, payload);
      else await api.create('projects', payload);
      showToast(editing.id ? 'Project updated' : 'Project created');
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
    if (!window.confirm(`Delete project "${row.name}"?`)) return;
    try {
      await api.remove('projects', row.id);
      showToast('Project deleted');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  const q = search.trim().toLowerCase();
  const projectMatches = (p) =>
    !q || p.name.toLowerCase().includes(q) || (p.code || '').toLowerCase().includes(q);

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

  return (
    <>
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
            </CollapsibleCard>
          ))}
        </div>
      )}

      <AnimatePresence>
        {editing && (
          <Modal
            title={editing.id ? 'Edit Project' : 'New Project'}
            onClose={() => setEditing(null)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setEditing(null)}>
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={save} disabled={saving}>
                  {saving ? 'Saving…' : 'Save Project'}
                </button>
              </>
            }
          >
            {errors._general && <div className="form-alert">{errors._general}</div>}
            <div className="form-grid">
              <Field label="Code" required error={errors.code}>
                <input
                  value={editing.form.code}
                  onChange={(e) => set('code', e.target.value)}
                  placeholder="e.g. MER-101"
                  className={errors.code ? 'invalid' : ''}
                />
              </Field>
              <Field label="Client" required error={errors.clientId}>
                <SearchSelect
                  options={clientOptions}
                  value={editing.form.clientId}
                  onChange={(v) => set('clientId', v)}
                  onCreate={createClient}
                  placeholder="Search or add a client…"
                  invalid={!!errors.clientId}
                />
              </Field>
              <Field label="Name" required error={errors.name} full>
                <input
                  value={editing.form.name}
                  onChange={(e) => set('name', e.target.value)}
                  className={errors.name ? 'invalid' : ''}
                />
              </Field>
              <Field label="Status">
                <select value={editing.form.status} onChange={(e) => set('status', e.target.value)}>
                  <option value="ACTIVE">Active</option>
                  <option value="ON_HOLD">On hold</option>
                  <option value="COMPLETED">Completed</option>
                </select>
              </Field>
              <div />
              <Field label="Start date" error={errors.startDate}>
                <input
                  type="date"
                  value={editing.form.startDate}
                  onChange={(e) => set('startDate', e.target.value)}
                />
              </Field>
              <Field label="End date" error={errors.endDate}>
                <input
                  type="date"
                  value={editing.form.endDate}
                  onChange={(e) => set('endDate', e.target.value)}
                />
              </Field>
            </div>
          </Modal>
        )}
      </AnimatePresence>
    </>
  );
}
