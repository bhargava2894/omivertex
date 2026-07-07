import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import SearchSelect from '../components/SearchSelect.jsx';

const EMPTY = { code: '', name: '', clientId: '', status: 'ACTIVE', startDate: '', endDate: '' };

export default function Projects({ showToast, canEdit }) {
  const [clientFilter, setClientFilter] = useState('');
  const { data, loading, reload } = useLoad(
    () => api.list('projects', { clientId: clientFilter }),
    [clientFilter]
  );
  const { data: clients, reload: reloadClients } = useLoad(() => api.list('clients'));

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

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <select
            className="filter-select"
            value={clientFilter}
            onChange={(e) => setClientFilter(e.target.value)}
            aria-label="Filter by client"
          >
            <option value="">All clients</option>
            {(clients || []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        {canEdit && (
          <button className="btn btn-primary" onClick={openCreate}>
            <Icon name="plus" size={16} /> New Project
          </button>
        )}
      </div>

      <DataTable
        loading={loading}
        rows={data || []}
        emptyText="No projects found."
        onEdit={canEdit ? openEdit : undefined}
        onDelete={canEdit ? remove : undefined}
        columns={[
          {
            key: 'name',
            label: 'Project',
            render: (r) => (
              <div>
                <div className="cell-main">{r.name}</div>
                <div className="cell-sub">{r.code}</div>
              </div>
            ),
          },
          { key: 'clientName', label: 'Client' },
          { key: 'status', label: 'Status', render: (r) => <Badge value={r.status} /> },
          { key: 'startDate', label: 'Start', render: (r) => r.startDate || '—' },
          { key: 'endDate', label: 'End', render: (r) => r.endDate || '—' },
        ]}
      />

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
    </>
  );
}
