import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
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
  workMode: '',
  skills: [], // rows of { skillId, minProficiency, required }
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
        workMode: row.workMode || '',
        skills: (row.skills || []).map((s) => ({
          skillId: s.skillId,
          minProficiency: s.minProficiency || '',
          required: s.required,
        })),
        billable: row.billable,
        allocationPercent: row.allocationPercent,
        startDate: row.startDate || '',
        endDate: row.endDate || '',
        status: row.status,
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));
  const setSkillRow = (i, row) =>
    setEditing((e) => ({
      ...e,
      form: { ...e.form, skills: e.form.skills.map((s, idx) => (idx === i ? row : s)) },
    }));
  const removeSkillRow = (i) =>
    setEditing((e) => ({
      ...e,
      form: { ...e.form, skills: e.form.skills.filter((_, idx) => idx !== i) },
    }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    const f = editing.form;
    const payload = {
      ...f,
      projectId: f.projectId === '' ? null : Number(f.projectId),
      workMode: f.workMode || null,
      skills: (f.skills || [])
        .filter((s) => s.skillId !== '' && s.skillId != null)
        .map((s) => ({
          skillId: Number(s.skillId),
          minProficiency: s.minProficiency || null,
          required: !!s.required,
        })),
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
            key: 'skills',
            label: 'Skills',
            render: (r) => (
              <div>
                <div className="cell-main">
                  {r.skills && r.skills.length
                    ? r.skills
                        .filter((s) => s.required)
                        .map((s) => s.skillName)
                        .join(', ') || '—'
                    : r.requiredSkill || '—'}
                </div>
                {r.skills && r.skills.some((s) => !s.required) && (
                  <div className="cell-sub" style={{ fontSize: '11px' }}>
                    Nice:{' '}
                    {r.skills
                      .filter((s) => !s.required)
                      .map((s) => s.skillName)
                      .join(', ')}
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

      <AnimatePresence>
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
              <Field label="Skill requirements" error={errors.skills} full>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {editing.form.skills.map((row, i) => (
                    <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <div style={{ flex: 2 }}>
                        <SearchSelect
                          options={(taxonomy || []).flatMap((cat) =>
                            (cat.skills || []).map((s) => ({
                              value: s.id,
                              label: `${cat.name} · ${s.name}`,
                            }))
                          )}
                          value={row.skillId}
                          onChange={(v) => setSkillRow(i, { ...row, skillId: v })}
                          placeholder="Search skill…"
                        />
                      </div>
                      <select
                        style={{ flex: 1 }}
                        value={row.minProficiency}
                        onChange={(e) => setSkillRow(i, { ...row, minProficiency: e.target.value })}
                      >
                        <option value="">Any level</option>
                        {PROFICIENCIES.map((p) => (
                          <option key={p.value} value={p.value}>
                            {p.label}
                          </option>
                        ))}
                      </select>
                      <select
                        style={{ flex: 1 }}
                        value={row.required ? 'must' : 'nice'}
                        onChange={(e) =>
                          setSkillRow(i, { ...row, required: e.target.value === 'must' })
                        }
                      >
                        <option value="must">Must-have</option>
                        <option value="nice">Nice-to-have</option>
                      </select>
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        onClick={() => removeSkillRow(i)}
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    style={{ alignSelf: 'flex-start' }}
                    onClick={() =>
                      set('skills', [
                        ...editing.form.skills,
                        { skillId: '', minProficiency: '', required: true },
                      ])
                    }
                  >
                    + Add requirement
                  </button>
                </div>
              </Field>
              <Field label="Work mode">
                <select
                  value={editing.form.workMode}
                  onChange={(e) => set('workMode', e.target.value)}
                >
                  <option value="">Any</option>
                  <option value="ONSHORE">Onshore</option>
                  <option value="OFFSHORE">Offshore</option>
                </select>
              </Field>
              <Field
                label="Legacy Required Skill (Text fallback)"
                error={errors.requiredSkill}
                full
              >
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
                  <select
                    value={editing.form.status}
                    onChange={(e) => set('status', e.target.value)}
                  >
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
      </AnimatePresence>

      <AnimatePresence>
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
              {matching.position.skills && matching.position.skills.length
                ? `needs ${matching.position.skills
                    .map((s) => `${s.skillName}${s.required ? '' : ' (nice)'}`)
                    .join(', ')}`
                : matching.position.requiredSkill
                  ? `needs ${matching.position.requiredSkill}`
                  : ''}
              {matching.position.workMode ? ` · ${matching.position.workMode.toLowerCase()}` : ''} ·{' '}
              {matching.position.allocationPercent}%
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
                      {c.fullMatch ? (
                        <Badge tone="green" label="full match" />
                      ) : (
                        <Badge tone="amber" label="partial" />
                      )}
                    </div>
                    <div className="cell-sub">
                      {c.fullMatch
                        ? [c.designation, (c.matchedSkills || []).join(', ')]
                            .filter(Boolean)
                            .join(' · ') || '—'
                        : `missing: ${(c.missingRequirements || []).join(', ') || '—'}`}
                    </div>
                  </div>
                  <div className="radar-right">
                    <Badge
                      tone={c.benchDays != null ? 'red' : 'blue'}
                      label={
                        c.benchDays != null
                          ? `bench ${c.benchDays}d`
                          : `${c.availablePercent}% free`
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
      </AnimatePresence>
    </>
  );
}
