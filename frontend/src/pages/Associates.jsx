import { useState, useEffect } from 'react';
import { AnimatePresence } from 'framer-motion';
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
  employeeId: '',
  company: 'Softility',
  location: '',
  workMode: 'ONSHORE',
  designation: '',
  phone: '',
  joinedDate: '',
  resignationDate: '',
  lastWorkingDay: '',
  exitReason: '',
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

  const [staffing, setStaffing] = useState(() => getParam('staffing')); // '' | billable | nonbillable | bench
  const [workMode, setWorkMode] = useState(() => getParam('workMode'));
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
      const staff = getParam('staffing');
      const mode = getParam('workMode');

      setCategoryId(cat);
      setSkillId(sk);
      setMinProficiency(minP);
      setStaffing(staff);
      setWorkMode(mode);

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
  // résumé-extracted previous (external) employers — reviewable before create; never sent on update
  const [extractedHistory, setExtractedHistory] = useState([]);
  const [historyNote, setHistoryNote] = useState('');

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
    setExtractedHistory([]);
    setHistoryNote('');
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
    setHistoryNote('');
    // a new file voids the previous file's extraction — a failed parse must
    // never leave résumé A's employment rows attached to résumé B's upload
    setExtractedHistory([]);

    try {
      const data = await api.parseResume(file);

      if (data.name || data.phone || (data.employmentHistory || []).length > 0) {
        // filter against the LATEST Company value — the admin may change it mid-parse
        setEditing((prev) => {
          const company = (prev.form.company || 'Softility').trim().toLowerCase();
          const all = data.employmentHistory || [];
          const external = all.filter(
            (e) => !e.company || e.company.trim().toLowerCase() !== company
          );
          if (external.length < all.length) {
            setHistoryNote(
              `${prev.form.company || 'Softility'} entry omitted — internal history comes from allocations.`
            );
          }
          setExtractedHistory(external);
          return {
            ...prev,
            form: {
              ...prev.form,
              // prefill only when empty — never clobber what the admin already typed
              name: prev.form.name || data.name || '',
              phone: prev.form.phone || data.phone || '',
            },
          };
        });
      }

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
        phone: row.phone || '',
        joinedDate: row.joinedDate || '',
        resignationDate: row.resignationDate || '',
        lastWorkingDay: row.lastWorkingDay || '',
        exitReason: row.exitReason || '',
        skills: (row.skillGroups || []).reduce((acc, group) => {
          (group.skills || []).forEach((s) => {
            acc[s.skillId] = { proficiency: s.proficiency, primary: !!s.primary };
          });
          return acc;
        }, {}),
        employeeId: row.employeeId || '',
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
      employeeId: rest.employeeId ? rest.employeeId.trim() : null,
      joinedDate: rest.joinedDate || null,
      resignationDate: rest.resignationDate || null,
      lastWorkingDay: rest.lastWorkingDay || null,
      exitReason: rest.exitReason || null,
      phone: rest.phone || null,
      skills: Object.entries(skills || {})
        .filter(([, v]) => v && v.proficiency)
        .map(([skillId, v]) => ({
          skillId: Number(skillId),
          proficiency: v.proficiency,
          primary: !!v.primary,
        })),
    };
    if (!editing.id) {
      // employment history is create-only — never sent on update
      payload.employmentHistory = extractedHistory
        .filter((e) => e.company && e.company.trim())
        .map((e) => ({
          company: e.company.trim(),
          title: e.title || null,
          startDate: e.startDate || null,
          endDate: e.endDate || null,
        }));
    }
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
      const fieldErrors = err.fieldErrors || {};
      if (!fieldErrors.email && err.message && err.message.toLowerCase().includes('email')) {
        fieldErrors.email = err.message;
      }
      setErrors({
        ...fieldErrors,
        _general: Object.keys(fieldErrors).length ? null : err.message,
      });
      showToast(err.message, true);
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
                  {[r.employeeId, r.designation, r.primarySkill, r.email]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
              </div>
            ),
          },
          { key: 'company', label: 'Company' },
          { key: 'location', label: 'Location', render: (r) => r.location || '—' },
          { key: 'workMode', label: 'Shore', render: (r) => <Badge value={r.workMode} /> },
          { key: 'currentClient', label: 'Customer', render: (r) => r.currentClient || '—' },
          {
            key: 'currentProject',
            label: 'Project',
            render: (r) =>
              r.currentProject ? (
                <div>
                  <div className="cell-main">{r.currentProject}</div>
                  {(r.currentProjectStartDate || r.currentProjectEndDate) && (
                    <div className="cell-sub" title="Allocation start and end date">
                      {r.currentProjectStartDate || '—'} → {r.currentProjectEndDate || '—'}
                    </div>
                  )}
                </div>
              ) : (
                '—'
              ),
          },
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

      <AnimatePresence>
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
              <Field label="Employee ID" error={errors.employeeId}>
                <input
                  value={editing.form.employeeId}
                  onChange={(e) => set('employeeId', e.target.value)}
                  className={errors.employeeId ? 'invalid' : ''}
                />
              </Field>
              <Field label="Company" required error={errors.company}>
                <select
                  value={editing.form.company}
                  onChange={(e) => set('company', e.target.value)}
                  className={errors.company ? 'invalid' : ''}
                >
                  <option value="Softility">Softility</option>
                  <option value="Contractor">Contractor</option>
                </select>
              </Field>
              <Field label="Designation" error={errors.designation}>
                <input
                  value={editing.form.designation}
                  onChange={(e) => set('designation', e.target.value)}
                />
              </Field>
              <Field label="Phone" error={errors.phone}>
                <input value={editing.form.phone} onChange={(e) => set('phone', e.target.value)} />
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
              <Field label="Exit reason" error={errors.exitReason}>
                <select
                  value={editing.form.exitReason}
                  onChange={(e) => set('exitReason', e.target.value)}
                >
                  <option value="">— still employed —</option>
                  <option value="RESIGNED">Resigned</option>
                  <option value="TERMINATED">Terminated</option>
                  <option value="CONTRACT_ENDED">Contract ended</option>
                  <option value="RETIRED">Retired</option>
                  <option value="OTHER">Other</option>
                </select>
              </Field>
              <Field label="Resignation date" error={errors.resignationDate}>
                <input
                  type="date"
                  value={editing.form.resignationDate}
                  onChange={(e) => set('resignationDate', e.target.value)}
                />
              </Field>
              <Field label="Last working day" error={errors.lastWorkingDay}>
                <input
                  type="date"
                  value={editing.form.lastWorkingDay}
                  onChange={(e) => set('lastWorkingDay', e.target.value)}
                />
              </Field>
              {!editing.id && (
                <>
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
                            // allow re-selecting the same file to re-trigger parsing
                            e.target.value = '';
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
                      {historyNote && (
                        <p className="stat-hint" style={{ margin: 0 }}>
                          {historyNote}
                        </p>
                      )}
                    </div>
                  </Field>
                  {extractedHistory.length > 0 && (
                    <Field label="Previous employment (from résumé — review before saving)" full>
                      <div style={{ display: 'grid', gap: '6px' }}>
                        {extractedHistory.map((e, i) => (
                          <div
                            key={i}
                            style={{ display: 'flex', gap: '6px', alignItems: 'center' }}
                          >
                            <input
                              style={{ flex: 2, minWidth: 0 }}
                              value={e.company || ''}
                              placeholder="Company"
                              onChange={(ev) =>
                                setExtractedHistory((h) =>
                                  h.map((row, j) =>
                                    j === i ? { ...row, company: ev.target.value } : row
                                  )
                                )
                              }
                            />
                            <input
                              style={{ flex: 2, minWidth: 0 }}
                              value={e.title || ''}
                              placeholder="Title"
                              onChange={(ev) =>
                                setExtractedHistory((h) =>
                                  h.map((row, j) =>
                                    j === i ? { ...row, title: ev.target.value } : row
                                  )
                                )
                              }
                            />
                            <input
                              type="date"
                              style={{ flex: 1.5, minWidth: 0 }}
                              value={e.startDate || ''}
                              aria-label={`Start date for ${e.company || 'entry'}`}
                              onChange={(ev) =>
                                setExtractedHistory((h) =>
                                  h.map((row, j) =>
                                    j === i ? { ...row, startDate: ev.target.value } : row
                                  )
                                )
                              }
                            />
                            <input
                              type="date"
                              style={{ flex: 1.5, minWidth: 0 }}
                              value={e.endDate || ''}
                              aria-label={`End date for ${e.company || 'entry'}`}
                              onChange={(ev) =>
                                setExtractedHistory((h) =>
                                  h.map((row, j) =>
                                    j === i ? { ...row, endDate: ev.target.value } : row
                                  )
                                )
                              }
                            />
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              aria-label={`Remove ${e.company || 'entry'}`}
                              onClick={() =>
                                setExtractedHistory((h) => h.filter((_, j) => j !== i))
                              }
                            >
                              <Icon name="x" size={14} />
                            </button>
                          </div>
                        ))}
                      </div>
                    </Field>
                  )}
                </>
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
      </AnimatePresence>
    </>
  );
}
