import { useState, useEffect } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import SkillEditor from '../components/SkillEditor.jsx';
import { ExportMenu, ImportButton } from '../components/DataTransfer.jsx';
import { PROFICIENCIES } from '../proficiency.js';

const EMPTY = {
  name: '',
  email: '',
  company: 'Softility',
  location: '',
  workMode: 'ONSHORE',
  designation: '',
  joinedDate: '',
  skills: {}, // skillId -> { proficiency, primary }
  status: 'ACTIVE',
};

function billability(row) {
  if (!row.currentProjectId) return 'Bench';
  return row.billable ? 'Billable' : 'Non-billable';
}

export default function Associates({ showToast, canEdit }) {
  const getParam = (name) => {
    const hash = window.location.hash;
    const searchPart = hash.split('?')[1];
    if (!searchPart) return '';
    const searchParams = new URLSearchParams(searchPart);
    return searchParams.get(name) || '';
  };

  const [staffing, setStaffing] = useState(''); // '' | billable | nonbillable | bench
  const [workMode, setWorkMode] = useState('');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [categoryId, setCategoryId] = useState(() => getParam('categoryId'));
  const [skillId, setSkillId] = useState(() => getParam('skillId'));
  const [minProficiency, setMinProficiency] = useState(() => getParam('minProficiency'));

  // debounce the search box so we don't refetch on every keystroke
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 250);
    return () => clearTimeout(t);
  }, [search]);

  // any filter/search change returns to the first page
  useEffect(() => {
    setPage(0);
  }, [staffing, workMode, categoryId, skillId, minProficiency, debouncedSearch]);

  const { data: taxonomy, reload: reloadTaxonomy } = useLoad(() => api.list('taxonomy'), []);

  useEffect(() => {
    if (!taxonomy) return;
    const initialSkillId = getParam('skillId');
    if (initialSkillId && !categoryId) {
      const cat = (taxonomy || []).find((c) =>
        (c.skills || []).some((s) => String(s.id) === String(initialSkillId))
      );
      if (cat) {
        setCategoryId(String(cat.id));
      }
    }
  }, [taxonomy]);

  useEffect(() => {
    const syncFiltersFromUrl = () => {
      const cat = getParam('categoryId');
      const sk = getParam('skillId');
      const minP = getParam('minProficiency');

      setCategoryId(cat);
      setSkillId(sk);
      setMinProficiency(minP);

      if (taxonomy && sk && !cat) {
        const foundCat = (taxonomy || []).find((c) =>
          (c.skills || []).some((s) => String(s.id) === String(sk))
        );
        if (foundCat) {
          setCategoryId(String(foundCat.id));
        }
      }
    };
    window.addEventListener('hashchange', syncFiltersFromUrl);
    return () => window.removeEventListener('hashchange', syncFiltersFromUrl);
  }, [taxonomy]);

  const params = {};
  if (workMode) params.workMode = workMode;
  if (staffing === 'bench') params.bench = 'true';
  if (staffing === 'billable') params.billable = 'true';
  if (staffing === 'nonbillable') {
    params.billable = 'false';
    params.bench = 'false';
  }
  if (categoryId) params.categoryId = categoryId;
  if (skillId) params.skillId = skillId;
  if (minProficiency) params.minProficiency = minProficiency;
  if (debouncedSearch) params.q = debouncedSearch;
  params.page = page;
  params.size = 25;

  const { data, loading, reload } = useLoad(
    () => api.list('associates', params),
    [staffing, workMode, categoryId, skillId, minProficiency, debouncedSearch, page]
  );
  const [editing, setEditing] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [resumeFile, setResumeFile] = useState(null);
  const [parsingResume, setParsingResume] = useState(false);
  const [resumeNotice, setResumeNotice] = useState('');
  const [drag, setDrag] = useState(false);

  // server returns a paged envelope; search + filtering happen server-side now
  const rows = data?.content || [];
  const serverPagination = {
    page: data?.page ?? 0,
    size: data?.size ?? 25,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
    onPage: setPage,
  };

  const openCreate = () => {
    setErrors({});
    setEditing({ form: { ...EMPTY } });
    setResumeFile(null);
    setParsingResume(false);
    setResumeNotice('');
  };

  const handleResumeSelect = async (file) => {
    if (!file) return;
    const ext = file.name.split('.').pop().toLowerCase();
    if (ext !== 'pdf' && ext !== 'docx') {
      showToast(
        'Unsupported file type. Only PDF (.pdf) and Word (.docx) documents are allowed.',
        true
      );
      return;
    }

    setResumeFile(file);
    setParsingResume(true);
    setResumeNotice('');

    try {
      const data = await api.parseResume(file);
      if (data.textExtracted && data.suggestedSkills?.length > 0) {
        const mergedSkills = { ...editing.form.skills };
        let addedCount = 0;
        data.suggestedSkills.forEach((s) => {
          if (!mergedSkills[s.skillId]) {
            mergedSkills[s.skillId] = { proficiency: 'INTERMEDIATE', primary: false };
            addedCount++;
          }
        });

        if (addedCount > 0) {
          setEditing((prev) => ({
            ...prev,
            form: {
              ...prev.form,
              skills: mergedSkills,
            },
          }));
          setResumeNotice(
            `${addedCount} skills detected from the résumé and added at Intermediate — please review and adjust each before saving.`
          );
          showToast(`Extracted ${addedCount} skills from résumé`);
        } else {
          setResumeNotice(
            'Résumé parsed successfully, but all detected skills were already selected.'
          );
        }
      } else if (!data.textExtracted) {
        setResumeNotice('Could not read text from this résumé file. Please add skills manually.');
        showToast('Text extraction returned empty', true);
      } else {
        setResumeNotice('No matching taxonomy skills were found in this résumé.');
        showToast('No matching skills found');
      }
    } catch (err) {
      showToast(err.message || 'Failed to parse résumé', true);
      setResumeNotice(`Failed to parse résumé: ${err.message}`);
    } finally {
      setParsingResume(false);
    }
  };
  const openEdit = (row) => {
    setErrors({});
    setEditing({
      id: row.id,
      form: {
        name: row.name,
        email: row.email,
        company: row.company,
        location: row.location || '',
        workMode: row.workMode,
        designation: row.designation || '',
        joinedDate: row.joinedDate || '',
        skills: (row.skillGroups || []).reduce((acc, group) => {
          (group.skills || []).forEach((s) => {
            acc[s.skillId] = { proficiency: s.proficiency, primary: !!s.primary };
          });
          return acc;
        }, {}),
        status: row.status,
      },
    });
  };
  const set = (k, v) => setEditing((e) => ({ ...e, form: { ...e.form, [k]: v } }));

  const save = async () => {
    setSaving(true);
    setErrors({});
    const { skills, ...rest } = editing.form;
    const payload = {
      ...rest,
      joinedDate: rest.joinedDate || null,
      skills: Object.entries(skills || {})
        .filter(([, v]) => v && v.proficiency)
        .map(([skillId, v]) => ({
          skillId: Number(skillId),
          proficiency: v.proficiency,
          primary: !!v.primary,
        })),
    };
    try {
      if (editing.id) {
        await api.update('associates', editing.id, payload);
      } else {
        const created = await api.create('associates', payload);
        if (resumeFile) {
          try {
            await api.uploadResume(created.id, resumeFile);
          } catch (uploadErr) {
            showToast(`Associate created, but résumé upload failed: ${uploadErr.message}`, true);
          }
        }
      }
      showToast(editing.id ? 'Associate updated' : 'Associate created');
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
          <select
            className="filter-select"
            value={staffing}
            onChange={(e) => setStaffing(e.target.value)}
            aria-label="Filter by staffing status"
          >
            <option value="">All staffing</option>
            <option value="billable">Billable</option>
            <option value="nonbillable">Non-billable</option>
            <option value="bench">Bench</option>
          </select>
          <select
            className="filter-select"
            value={workMode}
            onChange={(e) => setWorkMode(e.target.value)}
            aria-label="Filter by work mode"
          >
            <option value="">Onshore + Offshore</option>
            <option value="ONSHORE">Onshore</option>
            <option value="OFFSHORE">Offshore</option>
          </select>
          <select
            className="filter-select"
            value={categoryId}
            onChange={(e) => {
              setCategoryId(e.target.value);
              setSkillId('');
            }}
            aria-label="Filter by skill category"
          >
            <option value="">All Categories</option>
            {(taxonomy || []).map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
          <select
            className="filter-select"
            value={skillId}
            onChange={(e) => setSkillId(e.target.value)}
            disabled={!categoryId}
            aria-label="Filter by specific skill"
          >
            <option value="">All Skills</option>
            {((taxonomy || []).find((c) => String(c.id) === categoryId)?.skills || []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <select
            className="filter-select"
            value={minProficiency}
            onChange={(e) => setMinProficiency(e.target.value)}
            aria-label="Filter by minimum proficiency"
          >
            <option value="">Any Level</option>
            {PROFICIENCIES.map((p, i) => (
              <option key={p.value} value={p.value}>
                {p.label}
                {i < PROFICIENCIES.length - 1 ? '+' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-actions">
          {canEdit && <ImportButton onImported={reload} showToast={showToast} />}
          <ExportMenu showToast={showToast} />
          {canEdit && (
            <button className="btn btn-primary" onClick={openCreate}>
              <Icon name="plus" size={16} /> New Associate
            </button>
          )}
        </div>
      </div>

      <DataTable
        loading={loading}
        rows={rows}
        serverPagination={serverPagination}
        emptyText="No associates match these filters."
        onEdit={canEdit ? openEdit : undefined}
        onDelete={canEdit ? remove : undefined}
        columns={[
          {
            key: 'name',
            label: 'Associate',
            render: (r) => (
              <div>
                <div className="cell-main">
                  <a href={'#/associates/' + r.id}>{r.name}</a>
                </div>
                <div className="cell-sub">
                  {[r.designation, r.primarySkill, r.email].filter(Boolean).join(' · ')}
                </div>
              </div>
            ),
          },
          { key: 'company', label: 'Company' },
          { key: 'location', label: 'Location', render: (r) => r.location || '—' },
          { key: 'workMode', label: 'Shore', render: (r) => <Badge value={r.workMode} /> },
          { key: 'currentClient', label: 'Customer', render: (r) => r.currentClient || '—' },
          { key: 'currentProject', label: 'Project', render: (r) => r.currentProject || '—' },
          {
            key: 'billable',
            label: 'Billability',
            render: (r) =>
              r.benchDays != null ? (
                <Badge value="Bench" label={`Bench · ${r.benchDays}d`} />
              ) : (
                <Badge value={billability(r)} />
              ),
          },
        ]}
      />

      {editing && (
        <Modal
          title={editing.id ? 'Edit Associate' : 'New Associate'}
          size="lg"
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save Associate'}
              </button>
            </>
          }
        >
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <div className="form-grid">
            <Field label="Full name" required error={errors.name} full>
              <input
                value={editing.form.name}
                onChange={(e) => set('name', e.target.value)}
                className={errors.name ? 'invalid' : ''}
              />
            </Field>
            <Field label="Email" required error={errors.email} full>
              <input
                type="email"
                value={editing.form.email}
                onChange={(e) => set('email', e.target.value)}
                className={errors.email ? 'invalid' : ''}
              />
            </Field>
            <Field label="Company" required error={errors.company}>
              <input
                value={editing.form.company}
                onChange={(e) => set('company', e.target.value)}
                className={errors.company ? 'invalid' : ''}
              />
            </Field>
            <Field label="Designation" error={errors.designation}>
              <input
                value={editing.form.designation}
                onChange={(e) => set('designation', e.target.value)}
              />
            </Field>
            <Field label="Joined date" error={errors.joinedDate}>
              <input
                type="date"
                value={editing.form.joinedDate}
                onChange={(e) => set('joinedDate', e.target.value)}
              />
            </Field>
            <Field label="Location" error={errors.location}>
              <input
                value={editing.form.location}
                onChange={(e) => set('location', e.target.value)}
              />
            </Field>
            <Field label="Work mode" required error={errors.workMode}>
              <select
                value={editing.form.workMode}
                onChange={(e) => set('workMode', e.target.value)}
              >
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
            {!editing.id && (
              <Field label="Résumé (PDF/Word)" error={errors.resume} full>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <label
                    className={`dropzone ${drag ? 'drag' : ''}`}
                    style={{
                      padding: '24px 16px',
                      border: '2px dashed var(--color-border)',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      textAlign: 'center',
                      backgroundColor: 'var(--color-bg-card)',
                      transition: 'all 0.2s ease',
                      display: 'block',
                    }}
                    onDragOver={(e) => {
                      e.preventDefault();
                      setDrag(true);
                    }}
                    onDragLeave={() => setDrag(false)}
                    onDrop={async (e) => {
                      e.preventDefault();
                      setDrag(false);
                      handleResumeSelect(e.dataTransfer.files[0]);
                    }}
                  >
                    <Icon
                      name="upload"
                      size={24}
                      style={{ marginBottom: '8px', color: 'var(--color-primary)' }}
                    />
                    <div style={{ fontSize: '14px', color: 'var(--color-foreground)' }}>
                      {parsingResume ? (
                        <strong>Parsing résumé...</strong>
                      ) : resumeFile ? (
                        <strong>Selected: {resumeFile.name}</strong>
                      ) : (
                        <strong>Click to select a résumé</strong>
                      )}
                      {!parsingResume && !resumeFile && ' or drag a PDF/Word file here'}
                    </div>
                    <input
                      type="file"
                      accept=".pdf,.docx"
                      style={{ display: 'none' }}
                      onChange={async (e) => {
                        if (e.target.files[0]) {
                          handleResumeSelect(e.target.files[0]);
                        }
                      }}
                    />
                  </label>
                  {resumeNotice && (
                    <div
                      style={{
                        fontSize: '13px',
                        color: 'var(--color-info-fg, #0284c7)',
                        backgroundColor: 'var(--color-info-bg, #f0f9ff)',
                        padding: '10px 14px',
                        borderRadius: '6px',
                        border: '1px solid var(--color-info-border, #e0f2fe)',
                        lineHeight: '1.4',
                      }}
                    >
                      {resumeNotice}
                    </div>
                  )}
                </div>
              </Field>
            )}
            <Field label="Skills (★ marks the primary skill)" full>
              <div
                style={{
                  maxHeight: '40vh',
                  overflowY: 'auto',
                  paddingRight: '8px',
                  border: '1px solid var(--color-border)',
                  borderRadius: '8px',
                  padding: '12px',
                }}
              >
                <SkillEditor
                  taxonomy={taxonomy}
                  value={editing.form.skills}
                  onChange={(v) => set('skills', v)}
                  onTaxonomyChange={reloadTaxonomy}
                  showToast={showToast}
                />
              </div>
            </Field>
          </div>
        </Modal>
      )}
    </>
  );
}
