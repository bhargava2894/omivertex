import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';

const today = () => new Date().toISOString().slice(0, 10);

const EMPTY = {
  associateId: '',
  projectId: '',
  billable: true,
  allocationPercent: 100,
  startDate: today(),
  endDate: '',
};

export default function Allocations({ showToast, canEdit }) {
  const [projectFilter, setProjectFilter] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);

  const { data, loading, reload } = useLoad(
    () => api.list('allocations', { projectId: projectFilter, active: activeOnly ? 'true' : '' }),
    [projectFilter, activeOnly]
  );
  const { data: projects } = useLoad(() => api.list('projects'));
  const { data: associates } = useLoad(() => api.list('associates'));

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

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <select
            className="filter-select"
            value={projectFilter}
            onChange={(e) => setProjectFilter(e.target.value)}
            aria-label="Filter by project"
          >
            <option value="">All projects</option>
            {(projects || []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
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

      <DataTable
        loading={loading}
        rows={data || []}
        emptyText="No allocations found. Assign an associate to a project."
        onEdit={canEdit ? openEdit : undefined}
        onDelete={canEdit ? remove : undefined}
        columns={[
          {
            key: 'associateName',
            label: 'Associate',
            render: (r) => <span className="cell-main">{r.associateName}</span>,
          },
          {
            key: 'projectName',
            label: 'Project',
            render: (r) => (
              <div>
                <div>{r.projectName}</div>
                <div className="cell-sub">{r.clientName}</div>
              </div>
            ),
          },
          {
            key: 'billable',
            label: 'Billing',
            render: (r) => <Badge value={r.billable ? 'Billable' : 'Non-billable'} />,
          },
          {
            key: 'allocationPercent',
            label: 'Allocation',
            render: (r) => `${r.allocationPercent}%`,
          },
          { key: 'startDate', label: 'Start' },
          { key: 'endDate', label: 'End', render: (r) => r.endDate || '—' },
          {
            key: 'active',
            label: 'State',
            render: (r) => <Badge value={r.active ? 'Current' : 'Ended'} />,
          },
        ]}
      />

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
          <div className="form-grid">
            {!editing.id && (
              <>
                <Field label="Associate" required error={errors.associateId} full>
                  <select
                    value={editing.form.associateId}
                    onChange={(e) => set('associateId', e.target.value)}
                    className={errors.associateId ? 'invalid' : ''}
                  >
                    <option value="">Select associate…</option>
                    {(associates || []).map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} {a.currentProject ? `(on ${a.currentProject})` : '(bench)'}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Project" required error={errors.projectId} full>
                  <select
                    value={editing.form.projectId}
                    onChange={(e) => set('projectId', e.target.value)}
                    className={errors.projectId ? 'invalid' : ''}
                  >
                    <option value="">Select project…</option>
                    {(projects || []).map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} · {p.clientName}
                      </option>
                    ))}
                  </select>
                </Field>
              </>
            )}
            <Field label="Allocation %" required error={errors.allocationPercent}>
              <input
                type="number"
                min="1"
                max="100"
                value={editing.form.allocationPercent}
                onChange={(e) => set('allocationPercent', e.target.value)}
                className={errors.allocationPercent ? 'invalid' : ''}
              />
            </Field>
            <div className="checkbox-field">
              <input
                id="billable"
                type="checkbox"
                checked={editing.form.billable}
                onChange={(e) => set('billable', e.target.checked)}
              />
              <label htmlFor="billable">Billable engagement</label>
            </div>
            <Field label="Start date" required error={errors.startDate}>
              <input
                type="date"
                value={editing.form.startDate}
                onChange={(e) => set('startDate', e.target.value)}
                className={errors.startDate ? 'invalid' : ''}
              />
            </Field>
            <Field label="End date (roll-off)" error={errors.endDate}>
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
