import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import SearchSelect from '../components/SearchSelect.jsx';
import { PROFICIENCIES } from '../proficiency.js';

const EMPTY = {
  title: '',
  projectId: '',
  requiredSkill: '',
  requiredSkillId: '',
  minProficiency: '',
  billable: true,
  allocationPercent: 100,
  startDate: '',
  endDate: '',
  status: 'OPEN',
};

export default function Positions({ showToast, canEdit }) {
  const [statusFilter, setStatusFilter] = useState('OPEN');
  const { data, loading, reload } = useLoad(
    () => api.list('positions', { status: statusFilter }),
    [statusFilter]
  );
  const { data: projects } = useLoad(() => api.list('projects'));
  const { data: taxonomy } = useLoad(() => api.list('taxonomy'), []);

  const [editing, setEditing] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [matching, setMatching] = useState(null); // { position, candidates|null, filling }

  const openCreate = () => {
    setErrors({});
    setEditing({ form: { ...EMPTY } });
  };
  const openEdit = (row) => {
    setErrors({});
    setEditing({
      id: row.id,
      form: {
        title: row.title,
        projectId: row.projectId,
        requiredSkill: row.requiredSkill || '',
        requiredSkillId: row.requiredSkillId || '',
        minProficiency: row.minProficiency || '',
        billable: row.billable,
        allocationPercent: row.allocationPercent,
        startDate: row.startDate || '',
        endDate: row.endDate || '',
        status: row.status,
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    const f = editing.form;
    const payload = {
      ...f,
      projectId: f.projectId === '' ? null : Number(f.projectId),
      requiredSkillId: f.requiredSkillId === '' ? null : Number(f.requiredSkillId),
      minProficiency: f.minProficiency === '' ? null : f.minProficiency,
      allocationPercent: Number(f.allocationPercent),
      startDate: f.startDate || null,
      endDate: f.endDate || null,
    };
    try {
      if (editing.id) await api.update('positions', editing.id, payload);
      else await api.create('positions', payload);
      showToast(editing.id ? 'Position updated' : 'Position opened');
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
    if (!window.confirm(`Delete position "${row.title}"?`)) return;
    try {
      await api.remove('positions', row.id);
      showToast('Position deleted');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  const openMatches = async (row) => {
    setMatching({ position: row, candidates: null });
    try {
      const candidates = await api.list(`positions/${row.id}/matches`);
      setMatching((m) => (m && m.position.id === row.id ? { ...m, candidates } : m));
    } catch (err) {
      showToast(err.message, true);
      setMatching(null);
    }
  };

  const fill = async (candidate) => {
    setMatching((m) => ({ ...m, filling: candidate.associateId }));
    try {
      await api.create(`positions/${matching.position.id}/fill`, {
        associateId: candidate.associateId,
      });
      showToast(`${candidate.name} allocated to ${matching.position.projectName}`);
      setMatching(null);
      reload();
    } catch (err) {
      showToast(err.message, true);
      setMatching((m) => ({ ...m, filling: null }));
    }
  };

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Filter by status"
          >
            <option value="OPEN">Open</option>
            <option value="FILLED">Filled</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="">All statuses</option>
          </select>
        </div>
        {canEdit && (
          <button className="btn btn-primary" onClick={openCreate}>
            <Icon name="plus" size={16} /> Open Position
          </button>
        )}
      </div>

      <DataTable
        loading={loading}
        rows={data || []}
        emptyText="No positions in this view. Open one to start matching bench talent."
        onEdit={canEdit ? openEdit : undefined}
        onDelete={canEdit ? remove : undefined}
        columns={[
          {
            key: 'title',
            label: 'Position',
            render: (r) => (
              <div>
                <div className="cell-main">{r.title}</div>
                <div className="cell-sub">
                  {r.projectName} · {r.clientName}
                </div>
              </div>
            ),
          },
          {
            key: 'requiredSkill',
            label: 'Skill',
            render: (r) => (
              <div>
                <div className="cell-main">{r.requiredSkillName || r.requiredSkill || '—'}</div>
                {r.minProficiency && (
                  <div className="cell-sub" style={{ fontSize: '11px' }}>
                    Min: {r.minProficiency.replace('_', ' ')}
                  </div>
                )}
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
          { key: 'startDate', label: 'Start', render: (r) => r.startDate || '—' },
          { key: 'endDate', label: 'End', render: (r) => r.endDate || '—' },
          {
            key: 'status',
            label: 'Status',
            render: (r) => (
              <Badge value={r.status === 'OPEN' ? 'ACTIVE' : r.status} label={r.status} />
            ),
          },
          {
            key: 'match',
            label: '',
            render: (r) =>
              canEdit && r.status === 'OPEN' ? (
                <button className="btn btn-ghost btn-sm" onClick={() => openMatches(r)}>
                  <Icon name="sparkles" size={14} /> Find match
                </button>
              ) : null,
          },
        ]}
      />

      {editing && (
        <Modal
          title={editing.id ? 'Edit Position' : 'Open Position'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save Position'}
              </button>
            </>
          }
        >
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <div className="form-grid">
            <Field label="Title" required error={errors.title} full>
              <input
                value={editing.form.title}
                onChange={(e) => set('title', e.target.value)}
                placeholder="e.g. Senior Java Developer"
                className={errors.title ? 'invalid' : ''}
              />
            </Field>
            <Field label="Project" required error={errors.projectId} full>
              <SearchSelect
                options={(projects || []).map((p) => ({
                  value: p.id,
                  label: `${p.clientName} · ${p.name}`,
                }))}
                value={editing.form.projectId}
                onChange={(v) => set('projectId', v)}
                placeholder="Search company or project…"
                invalid={!!errors.projectId}
              />
            </Field>
            <Field label="Required Skill (Structured)" error={errors.requiredSkillId}>
              <SearchSelect
                options={(taxonomy || []).flatMap((cat) =>
                  (cat.skills || []).map((s) => ({ value: s.id, label: `${cat.name} · ${s.name}` }))
                )}
                value={editing.form.requiredSkillId}
                onChange={(v) => set('requiredSkillId', v)}
                placeholder="Search skill from taxonomy…"
                invalid={!!errors.requiredSkillId}
              />
            </Field>
            <Field label="Minimum Proficiency" error={errors.minProficiency}>
              <select
                value={editing.form.minProficiency}
                onChange={(e) => set('minProficiency', e.target.value)}
                disabled={!editing.form.requiredSkillId}
              >
                <option value="">Any Level</option>
                {PROFICIENCIES.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Legacy Required Skill (Text fallback)" error={errors.requiredSkill} full>
              <input
                value={editing.form.requiredSkill}
                onChange={(e) => set('requiredSkill', e.target.value)}
                placeholder="e.g. Java, Python (legacy search fallback)"
              />
            </Field>
            <Field label="Allocation %" error={errors.allocationPercent}>
              <input
                type="number"
                min="1"
                max="100"
                value={editing.form.allocationPercent}
                onChange={(e) => set('allocationPercent', e.target.value)}
              />
            </Field>
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
            {editing.id ? (
              <Field label="Status">
                <select value={editing.form.status} onChange={(e) => set('status', e.target.value)}>
                  <option value="OPEN">Open</option>
                  <option value="FILLED">Filled</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </Field>
            ) : (
              <div />
            )}
            <div className="checkbox-field">
              <input
                id="pos-billable"
                type="checkbox"
                checked={editing.form.billable}
                onChange={(e) => set('billable', e.target.checked)}
              />
              <label htmlFor="pos-billable">Billable seat</label>
            </div>
          </div>
        </Modal>
      )}

      {matching && (
        <Modal
          title={`Candidates — ${matching.position.title}`}
          onClose={() => setMatching(null)}
          footer={
            <button className="btn btn-ghost" onClick={() => setMatching(null)}>
              Close
            </button>
          }
        >
          <p className="cell-sub" style={{ marginTop: 0 }}>
            {matching.position.requiredSkillName
              ? ` · needs ${matching.position.requiredSkillName}${matching.position.minProficiency ? ` (${matching.position.minProficiency.replace('_', ' ')}+)` : ''}`
              : matching.position.requiredSkill
                ? ` · needs ${matching.position.requiredSkill}`
                : ''}{' '}
            · {matching.position.allocationPercent}%
          </p>
          {matching.candidates == null ? (
            <div className="skeleton-row" />
          ) : matching.candidates.length === 0 ? (
            <p className="stat-hint">No one has enough free capacity for this position.</p>
          ) : (
            matching.candidates.map((c) => (
              <div className="radar-row" key={c.associateId}>
                <div>
                  <div className="cell-main">
                    {c.name}{' '}
                    {c.skillMatch && (
                      <Badge
                        tone="green"
                        label={`skill match${c.matchedProficiency ? ` (${c.matchedProficiency.replace('_', ' ')})` : ''}`}
                      />
                    )}
                  </div>
                  <div className="cell-sub">
                    {[c.designation, c.primarySkill, c.secondarySkill]
                      .filter(Boolean)
                      .join(' · ') || '—'}
                  </div>
                </div>
                <div className="radar-right">
                  <Badge
                    tone={c.benchDays != null ? 'red' : 'blue'}
                    label={
                      c.benchDays != null ? `bench ${c.benchDays}d` : `${c.availablePercent}% free`
                    }
                  />
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={matching.filling != null}
                    onClick={() => fill(c)}
                  >
                    {matching.filling === c.associateId ? 'Allocating…' : 'Allocate'}
                  </button>
                </div>
              </div>
            ))
          )}
        </Modal>
      )}
    </>
  );
}
