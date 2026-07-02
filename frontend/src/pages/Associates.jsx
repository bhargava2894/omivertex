import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import { ExportMenu, ImportButton } from '../components/DataTransfer.jsx';

const EMPTY = {
  name: '', email: '', company: 'Softility', location: '',
  workMode: 'ONSHORE', designation: '', status: 'ACTIVE',
};

function billability(row) {
  if (!row.currentProjectId) return 'Bench';
  return row.billable ? 'Billable' : 'Non-billable';
}

export default function Associates({ showToast }) {
  const [staffing, setStaffing] = useState(''); // '' | billable | nonbillable | bench
  const [workMode, setWorkMode] = useState('');
  const [search, setSearch] = useState('');

  const params = {};
  if (workMode) params.workMode = workMode;
  if (staffing === 'bench') params.bench = 'true';
  if (staffing === 'billable') params.billable = 'true';
  if (staffing === 'nonbillable') { params.billable = 'false'; params.bench = 'false'; }

  const { data, loading, reload } = useLoad(
    () => api.list('associates', params),
    [staffing, workMode]
  );
  const [editing, setEditing] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const rows = (data || []).filter(
    (a) =>
      a.name.toLowerCase().includes(search.toLowerCase()) ||
      a.email.toLowerCase().includes(search.toLowerCase())
  );

  const openCreate = () => { setErrors({}); setEditing({ form: { ...EMPTY } }); };
  const openEdit = (row) => {
    setErrors({});
    setEditing({
      id: row.id,
      form: {
        name: row.name, email: row.email, company: row.company, location: row.location || '',
        workMode: row.workMode, designation: row.designation || '', status: row.status,
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    try {
      if (editing.id) await api.update('associates', editing.id, editing.form);
      else await api.create('associates', editing.form);
      showToast(editing.id ? 'Associate updated' : 'Associate created');
      setEditing(null);
      reload();
    } catch (err) {
      setErrors({ ...err.fieldErrors, _general: Object.keys(err.fieldErrors).length ? null : err.message });
    } finally {
      setSaving(false);
    }
  };

  const remove = async (row) => {
    if (!window.confirm(`Delete associate "${row.name}"?`)) return;
    try {
      await api.remove('associates', row.id);
      showToast('Associate deleted');
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
            placeholder="Search name or email…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search associates"
          />
          <select className="filter-select" value={staffing} onChange={(e) => setStaffing(e.target.value)} aria-label="Filter by staffing status">
            <option value="">All staffing</option>
            <option value="billable">Billable</option>
            <option value="nonbillable">Non-billable</option>
            <option value="bench">Bench</option>
          </select>
          <select className="filter-select" value={workMode} onChange={(e) => setWorkMode(e.target.value)} aria-label="Filter by work mode">
            <option value="">Onshore + Offshore</option>
            <option value="ONSHORE">Onshore</option>
            <option value="OFFSHORE">Offshore</option>
          </select>
        </div>
        <div className="toolbar-actions">
          <ImportButton onImported={reload} showToast={showToast} />
          <ExportMenu />
          <button className="btn btn-primary" onClick={openCreate}>
            <Icon name="plus" size={16} /> New Associate
          </button>
        </div>
      </div>

      <DataTable
        loading={loading}
        rows={rows}
        emptyText="No associates match these filters."
        onEdit={openEdit}
        onDelete={remove}
        columns={[
          {
            key: 'name', label: 'Associate',
            render: (r) => (
              <div>
                <div className="cell-main">{r.name}</div>
                <div className="cell-sub">{r.designation ? `${r.designation} · ` : ''}{r.email}</div>
              </div>
            ),
          },
          { key: 'company', label: 'Company' },
          { key: 'location', label: 'Location', render: (r) => r.location || '—' },
          { key: 'workMode', label: 'Shore', render: (r) => <Badge value={r.workMode} /> },
          { key: 'currentClient', label: 'Customer', render: (r) => r.currentClient || '—' },
          { key: 'currentProject', label: 'Project', render: (r) => r.currentProject || '—' },
          { key: 'billable', label: 'Billability', render: (r) => <Badge value={billability(r)} /> },
        ]}
      />

      {editing && (
        <Modal
          title={editing.id ? 'Edit Associate' : 'New Associate'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save Associate'}
              </button>
            </>
          }
        >
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <div className="form-grid">
            <Field label="Full name" required error={errors.name} full>
              <input value={editing.form.name} onChange={(e) => set('name', e.target.value)} className={errors.name ? 'invalid' : ''} />
            </Field>
            <Field label="Email" required error={errors.email} full>
              <input type="email" value={editing.form.email} onChange={(e) => set('email', e.target.value)} className={errors.email ? 'invalid' : ''} />
            </Field>
            <Field label="Company" required error={errors.company}>
              <input value={editing.form.company} onChange={(e) => set('company', e.target.value)} className={errors.company ? 'invalid' : ''} />
            </Field>
            <Field label="Designation" error={errors.designation}>
              <input value={editing.form.designation} onChange={(e) => set('designation', e.target.value)} />
            </Field>
            <Field label="Location" error={errors.location}>
              <input value={editing.form.location} onChange={(e) => set('location', e.target.value)} />
            </Field>
            <Field label="Work mode" required error={errors.workMode}>
              <select value={editing.form.workMode} onChange={(e) => set('workMode', e.target.value)}>
                <option value="ONSHORE">Onshore</option>
                <option value="OFFSHORE">Offshore</option>
              </select>
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
