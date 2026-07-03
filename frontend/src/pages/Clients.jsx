import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';

const EMPTY = { name: '', industry: '', location: '', status: 'ACTIVE' };

export default function Clients({ showToast, canEdit }) {
  const { data, loading, reload } = useLoad(() => api.list('clients'));
  const [editing, setEditing] = useState(null); // null | {form, id?}
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');

  const rows = (data || []).filter((c) =>
    c.name.toLowerCase().includes(search.toLowerCase())
  );

  const openCreate = () => { setErrors({}); setEditing({ form: { ...EMPTY } }); };
  const openEdit = (row) => {
    setErrors({});
    setEditing({ id: row.id, form: { name: row.name, industry: row.industry || '', location: row.location || '', status: row.status } });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    try {
      if (editing.id) await api.update('clients', editing.id, editing.form);
      else await api.create('clients', editing.form);
      showToast(editing.id ? 'Client updated' : 'Client created');
      setEditing(null);
      reload();
    } catch (err) {
      setErrors({ ...err.fieldErrors, _general: Object.keys(err.fieldErrors).length ? null : err.message });
    } finally {
      setSaving(false);
    }
  };

  const remove = async (row) => {
    if (!window.confirm(`Delete client "${row.name}"?`)) return;
    try {
      await api.remove('clients', row.id);
      showToast('Client deleted');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <input
            className="search-input"
            placeholder="Search clients…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search clients"
          />
        </div>
        {canEdit && (
          <button className="btn btn-primary" onClick={openCreate}>
            <Icon name="plus" size={16} /> New Client
          </button>
        )}
      </div>

      <DataTable
        loading={loading}
        rows={rows}
        emptyText="No clients yet. Add your first client to get started."
        onEdit={canEdit ? openEdit : undefined}
        onDelete={canEdit ? remove : undefined}
        columns={[
          { key: 'name', label: 'Client', render: (r) => <span className="cell-main">{r.name}</span> },
          { key: 'industry', label: 'Industry', render: (r) => r.industry || '—' },
          { key: 'location', label: 'Location', render: (r) => r.location || '—' },
          { key: 'status', label: 'Status', render: (r) => <Badge value={r.status} /> },
        ]}
      />

      {editing && (
        <Modal
          title={editing.id ? 'Edit Client' : 'New Client'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save Client'}
              </button>
            </>
          }
        >
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <div className="form-grid">
            <Field label="Name" required error={errors.name} full>
              <input value={editing.form.name} onChange={(e) => set('name', e.target.value)} className={errors.name ? 'invalid' : ''} />
            </Field>
            <Field label="Industry" error={errors.industry}>
              <input value={editing.form.industry} onChange={(e) => set('industry', e.target.value)} />
            </Field>
            <Field label="Location" error={errors.location}>
              <input value={editing.form.location} onChange={(e) => set('location', e.target.value)} />
            </Field>
            <Field label="Status">
              <select value={editing.form.status} onChange={(e) => set('status', e.target.value)}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </Field>
          </div>
        </Modal>
      )}
    </>
  );
}
