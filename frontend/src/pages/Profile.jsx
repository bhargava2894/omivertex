import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Modal from '../components/Modal.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import Badge from '../components/Badge.jsx';
import SkillEditor from '../components/SkillEditor.jsx';
import AllocationForm from '../components/AllocationForm.jsx';
import { joinedWithTenure, statusLabel } from '../format.js';
import { proficiencyInfo } from '../proficiency.js';

export default function Profile({ id, showToast, canEdit }) {
  const {
    data: associate,
    loading: loadAssoc,
    reload: reloadAssoc,
  } = useLoad(() => api.get('associates', id), [id]);
  const {
    data: certs,
    loading: loadCerts,
    reload: reloadCerts,
  } = useLoad(() => api.getCertifications(id), [id]);
  const {
    data: allocations,
    loading: loadAllocs,
    reload: reloadAllocs,
  } = useLoad(() => api.list('allocations', { associateId: id }), [id]);
  // Loaded for the Assign dialog's company→project pickers.
  const { data: projects } = useLoad(() => api.list('projects'), []);
  const { data: taxonomy, reload: reloadTaxonomy } = useLoad(() => api.list('taxonomy'), []);

  const [managingSkills, setManagingSkills] = useState(false);
  const [selectedSkills, setSelectedSkills] = useState({}); // skillId -> { proficiency, primary }
  const [savingSkills, setSavingSkills] = useState(false);

  const [uploadingResume, setUploadingResume] = useState(false);
  const [suggestedSkills, setSuggestedSkills] = useState([]);
  const [resumeNotice, setResumeNotice] = useState('');

  const [ending, setEnding] = useState(null); // allocation row being ended, + endDate
  const [assigning, setAssigning] = useState(false);
  const [assignForm, setAssignForm] = useState(null);
  const [allocErrors, setAllocErrors] = useState({});
  const [savingAlloc, setSavingAlloc] = useState(false);

  const handleResumeUpload = async (file) => {
    if (!file) return;
    const ext = file.name.split('.').pop().toLowerCase();
    if (ext !== 'pdf' && ext !== 'docx') {
      showToast(
        'Unsupported file type. Only PDF (.pdf) and Word (.docx) documents are allowed.',
        true
      );
      return;
    }

    setUploadingResume(true);
    setResumeNotice('');
    setSuggestedSkills([]);

    try {
      await api.uploadResume(id, file);
      showToast('Résumé uploaded successfully');
      reloadAssoc();

      const parseData = await api.parseResume(file);
      if (parseData.textExtracted && parseData.suggestedSkills?.length > 0) {
        setSuggestedSkills(parseData.suggestedSkills);
        const detected = parseData.suggestedSkills.length;
        const viaAi = parseData.source === 'AI';
        setResumeNotice(
          (viaAi
            ? `${detected} skills detected by AI with estimated proficiency` +
              (parseData.experienceSummary ? ` — ${parseData.experienceSummary}` : '')
            : `${detected} skills detected from the résumé (added at Intermediate)`) +
            ' — click "Review & Add Skills" to review and adjust before saving.'
        );
      } else if (!parseData.textExtracted) {
        setResumeNotice(
          'Could not read text from this résumé file. Skills were not auto-detected.'
        );
      } else {
        setResumeNotice('No new matching skills were found in this résumé.');
      }
    } catch (err) {
      showToast(err.message || 'Failed to upload résumé', true);
    } finally {
      setUploadingResume(false);
    }
  };

  const handleResumeDelete = async () => {
    if (!window.confirm('Are you sure you want to delete this résumé?')) return;
    try {
      await api.deleteResume(id);
      showToast('Résumé deleted successfully');
      setResumeNotice('');
      setSuggestedSkills([]);
      reloadAssoc();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  // Builds the skill map from the associate's currently-saved skills.
  const existingSkillsMap = () => {
    const skillsMap = {};
    (associate.skillGroups || []).forEach((group) => {
      (group.skills || []).forEach((skill) => {
        skillsMap[skill.skillId] = { proficiency: skill.proficiency, primary: !!skill.primary };
      });
    });
    return skillsMap;
  };

  // Opens the skills modal seeded with the currently-saved skills.
  const openManageSkills = () => {
    setSelectedSkills(existingSkillsMap());
    setManagingSkills(true);
  };

  // Opens the skills modal seeded with saved skills PLUS résumé-detected ones
  // (added at Intermediate for review). Both entry points seed selectedSkills
  // explicitly, so nothing clobbers the suggestions after the modal opens.
  const handleReviewSkills = () => {
    const skillsMap = existingSkillsMap();
    suggestedSkills.forEach((s) => {
      if (!skillsMap[s.skillId]) {
        skillsMap[s.skillId] = { proficiency: s.proficiency || 'INTERMEDIATE', primary: false };
      }
    });
    setSelectedSkills(skillsMap);
    setManagingSkills(true);
    setResumeNotice('');
    setSuggestedSkills([]);
  };

  const todayStr = () => new Date().toISOString().slice(0, 10);

  const openEnd = (row) => {
    setAllocErrors({});
    setEnding({ row, endDate: todayStr() });
  };

  // Ends a current allocation: existing values + the chosen end date, through the
  // normal update endpoint. The row flips to "Ended" and stays in history.
  const handleEndAllocation = async () => {
    const { row, endDate } = ending;
    if (!endDate || endDate < row.startDate) {
      setAllocErrors({ endDate: 'End date must be on or after the start date' });
      return;
    }
    setSavingAlloc(true);
    setAllocErrors({});
    try {
      await api.update('allocations', row.id, {
        billable: row.billable,
        allocationPercent: row.allocationPercent,
        startDate: row.startDate,
        endDate,
      });
      showToast(`Ended allocation on ${row.projectName}`);
      setEnding(null);
      reloadAllocs();
      reloadAssoc(); // bench/billable badges may change
    } catch (err) {
      const fieldErrors = err.fieldErrors || {};
      setAllocErrors({
        ...fieldErrors,
        _general: Object.keys(fieldErrors).length ? null : err.message,
      });
      showToast(err.message, true);
    } finally {
      setSavingAlloc(false);
    }
  };

  const openAssign = () => {
    setAllocErrors({});
    // The capacity guard counts an allocation's end date as still allocated, so a
    // seamless End -> Assign needs the new start the day AFTER the latest end.
    // Find the latest end date that is today or later (just-ended or ending soon).
    const latestEnd = (allocations || [])
      .map((a) => a.endDate)
      .filter((d) => d && d >= todayStr())
      .sort()
      .pop();
    const dayAfter = (iso) => {
      const d = new Date(`${iso}T00:00:00Z`);
      d.setUTCDate(d.getUTCDate() + 1);
      return d.toISOString().slice(0, 10);
    };
    setAssignForm({
      companyId: '',
      projectId: '',
      billable: true,
      allocationPercent: 100,
      startDate: latestEnd ? dayAfter(latestEnd) : todayStr(),
      endDate: '',
    });
    setAssigning(true);
  };

  // Assigns THIS associate to a project. The server's capacity guard applies —
  // someone still at 100% gets the clear "maximum is 100%" error in the form.
  const handleAssign = async () => {
    setSavingAlloc(true);
    setAllocErrors({});
    try {
      await api.create('allocations', {
        associateId: Number(id),
        projectId: assignForm.projectId === '' ? null : Number(assignForm.projectId),
        billable: assignForm.billable,
        allocationPercent: Number(assignForm.allocationPercent),
        startDate: assignForm.startDate,
        endDate: assignForm.endDate || null,
      });
      showToast(`Assigned ${associate.name} to a new project`);
      setAssigning(false);
      reloadAllocs();
      reloadAssoc();
    } catch (err) {
      const fieldErrors = err.fieldErrors || {};
      setAllocErrors({
        ...fieldErrors,
        _general: Object.keys(fieldErrors).length ? null : err.message,
      });
      showToast(err.message, true);
    } finally {
      setSavingAlloc(false);
    }
  };

  const [addingCert, setAddingCert] = useState(false);
  const [certForm, setCertForm] = useState({
    name: '',
    authority: '',
    credentialId: '',
    issuedDate: '',
    expiryDate: '',
  });
  const [savingCert, setSavingCert] = useState(false);
  const [certErrors, setCertErrors] = useState({});

  if (loadAssoc || loadCerts || loadAllocs) {
    return (
      <div>
        {[...Array(5)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  if (!associate) {
    return (
      <div className="card">
        <div className="empty-state">
          <Icon name="inbox" size={40} />
          <p>Associate not found.</p>
        </div>
      </div>
    );
  }

  const handleSaveSkills = async () => {
    setSavingSkills(true);
    const payload = Object.entries(selectedSkills)
      .filter(([, entry]) => entry && entry.proficiency)
      .map(([skillId, entry]) => ({
        skillId: Number(skillId),
        proficiency: entry.proficiency,
        primary: !!entry.primary,
      }));

    try {
      await api.replaceSkills(id, payload);
      showToast('Skills updated successfully');
      setManagingSkills(false);
      reloadAssoc();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setSavingSkills(false);
    }
  };

  const handleAddCert = async () => {
    setSavingCert(true);
    setCertErrors({});
    const payload = {
      name: certForm.name.trim(),
      authority: certForm.authority.trim() || null,
      credentialId: certForm.credentialId.trim() || null,
      issuedDate: certForm.issuedDate || null,
      expiryDate: certForm.expiryDate || null,
    };
    try {
      await api.addCertification(id, payload);
      showToast('Certification added successfully');
      setAddingCert(false);
      setCertForm({ name: '', authority: '', credentialId: '', issuedDate: '', expiryDate: '' });
      reloadCerts();
    } catch (err) {
      const fieldErrors = err.fieldErrors || {};
      setCertErrors({
        ...fieldErrors,
        _general: Object.keys(fieldErrors).length ? null : err.message,
      });
      showToast(err.message, true);
    } finally {
      setSavingCert(false);
    }
  };

  const handleDeleteCert = async (certId, name) => {
    if (!window.confirm(`Delete certification "${name}"?`)) return;
    try {
      await api.deleteCertification(certId);
      showToast('Certification deleted');
      reloadCerts();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  const isExpiringSoon = (expiryStr) => {
    if (!expiryStr) return false;
    const expiry = new Date(expiryStr);
    const today = new Date();
    const diffTime = expiry - today;
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays >= 0 && diffDays <= 90;
  };

  return (
    <div style={{ display: 'grid', gap: '20px' }}>
      {(associate.lastWorkingDay || associate.exitReason) && (
        <div className="form-alert">
          {associate.status === 'INACTIVE' ? 'Exited' : 'Leaving'} — last working day{' '}
          {associate.lastWorkingDay || 'not set'}
          {associate.exitReason && (
            <> ({associate.exitReason.replaceAll('_', ' ').toLowerCase()})</>
          )}
        </div>
      )}
      {/* Header Profile Card */}
      <div
        className="card profile-header"
        style={{ display: 'flex', gap: '20px', alignItems: 'center', padding: '24px' }}
      >
        <span
          className="user-avatar"
          style={{ width: '64px', height: '64px', fontSize: '24px', flexShrink: 0 }}
        >
          {associate.name.charAt(0).toUpperCase()}
        </span>
        <div style={{ flexGrow: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <h2 style={{ margin: 0, fontSize: '22px', fontWeight: '700' }}>{associate.name}</h2>
            <Badge value={associate.workMode} />
            {associate.benchDays != null ? (
              <Badge value="Bench" label={`Bench · ${associate.benchDays}d`} tone="red" />
            ) : (
              <Badge
                value={associate.billable ? 'Billable' : 'Non-billable'}
                tone={associate.billable ? 'green' : 'amber'}
              />
            )}
          </div>
          <div className="cell-sub" style={{ marginTop: '4px', fontSize: '14px' }}>
            {associate.employeeId && (
              <strong style={{ color: 'var(--color-primary)' }}>{associate.employeeId}</strong>
            )}
            {associate.employeeId && <> · </>}
            <strong>{associate.designation}</strong> · {associate.email} · {associate.company} ·{' '}
            {associate.location || 'No Location'}
            {associate.phone && <> · {associate.phone}</>}
          </div>
          <div className="cell-sub" style={{ marginTop: '4px', fontSize: '13.5px' }}>
            Joined {joinedWithTenure(associate.joinedDate) || '—'} · Status{' '}
            {statusLabel(associate.status)}
            {associate.resignationDate && <> · Resignation filed {associate.resignationDate}</>}
            {associate.lastWorkingDay && <> · Last working day {associate.lastWorkingDay}</>}
          </div>
          {(associate.primarySkill || associate.secondarySkill) && (
            <div style={{ marginTop: '8px', fontSize: '13px', color: 'var(--color-muted-fg)' }}>
              Legacy Skills:{' '}
              {[
                associate.primarySkill && `Primary: ${associate.primarySkill}`,
                associate.secondarySkill && `Secondary: ${associate.secondarySkill}`,
              ]
                .filter(Boolean)
                .join(' | ')}
            </div>
          )}
        </div>
      </div>

      <div
        style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px' }}
        className="form-grid"
      >
        {/* Skills by Category */}
        <div className="card" style={{ padding: '24px' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '16px',
            }}
          >
            <h3 style={{ margin: 0 }}>Skills Taxonomy</h3>
            {canEdit && (
              <button className="btn btn-ghost btn-sm" onClick={openManageSkills}>
                <Icon name="edit" size={14} /> Manage Skills
              </button>
            )}
          </div>
          {!associate.skillGroups || associate.skillGroups.length === 0 ? (
            <div className="empty-state" style={{ padding: '20px 0' }}>
              <Icon name="inbox" size={30} />
              <p style={{ fontSize: '13.5px' }}>No structured skills recorded yet.</p>
            </div>
          ) : (
            <div style={{ display: 'grid', gap: '18px' }}>
              {associate.skillGroups.map((group) => (
                <div key={group.category}>
                  <div
                    className="cell-sub"
                    style={{
                      fontWeight: '600',
                      marginBottom: '8px',
                      textTransform: 'uppercase',
                      fontSize: '11px',
                      letterSpacing: '0.05em',
                    }}
                  >
                    {group.category}
                  </div>
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                    {group.skills.map((skill) => {
                      const info = proficiencyInfo(skill.proficiency);
                      return (
                        <Badge
                          key={skill.skillId}
                          value={skill.proficiency}
                          label={`${skill.name} · ${info.label}`}
                          tone={info.tone}
                        />
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right column: Certifications + Résumé */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Certifications Card */}
          <div className="card" style={{ padding: '24px' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '16px',
              }}
            >
              <h3 style={{ margin: 0 }}>Certifications</h3>
              {canEdit && (
                <button className="btn btn-ghost btn-sm" onClick={() => setAddingCert(true)}>
                  <Icon name="plus" size={14} /> Add Cert
                </button>
              )}
            </div>
            {!certs || certs.length === 0 ? (
              <div className="empty-state" style={{ padding: '20px 0' }}>
                <Icon name="inbox" size={30} />
                <p style={{ fontSize: '13.5px' }}>No certifications recorded.</p>
              </div>
            ) : (
              <div
                className="table-wrap"
                style={{ margin: 0, boxShadow: 'none', border: '1px solid var(--color-border)' }}
              >
                <table style={{ fontSize: '13px' }}>
                  <thead>
                    <tr>
                      <th>Certification</th>
                      <th>Authority</th>
                      <th>Expires</th>
                      {canEdit && <th style={{ width: '40px' }} />}
                    </tr>
                  </thead>
                  <tbody>
                    {certs.map((c) => (
                      <tr key={c.id}>
                        <td>
                          <div className="cell-main">{c.name}</div>
                          {c.credentialId && (
                            <div className="cell-sub" style={{ fontSize: '11px' }}>
                              ID: {c.credentialId}
                            </div>
                          )}
                        </td>
                        <td>{c.authority || '—'}</td>
                        <td>
                          {c.expiryDate ? (
                            isExpiringSoon(c.expiryDate) ? (
                              <Badge
                                value="Expiring"
                                label={`Expires ${c.expiryDate}`}
                                tone="red"
                              />
                            ) : (
                              c.expiryDate
                            )
                          ) : (
                            'No Expiry'
                          )}
                        </td>
                        {canEdit && (
                          <td className="actions">
                            <button
                              className="btn btn-danger btn-sm"
                              onClick={() => handleDeleteCert(c.id, c.name)}
                              aria-label="Delete cert"
                            >
                              <Icon name="trash" size={12} />
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Résumé Card */}
          <div className="card" style={{ padding: '24px' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '16px',
              }}
            >
              <h3 style={{ margin: 0 }}>Résumé</h3>
              {associate.resumeFilename && canEdit && (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <label className="btn btn-ghost btn-sm" style={{ cursor: 'pointer', margin: 0 }}>
                    <Icon name="upload" size={14} /> Replace
                    <input
                      type="file"
                      accept=".pdf,.docx"
                      style={{ display: 'none' }}
                      disabled={uploadingResume}
                      onChange={(e) => {
                        if (e.target.files[0]) handleResumeUpload(e.target.files[0]);
                      }}
                    />
                  </label>
                  <button
                    className="btn btn-ghost btn-sm btn-danger-hover"
                    onClick={handleResumeDelete}
                  >
                    <Icon name="trash" size={14} /> Delete
                  </button>
                </div>
              )}
            </div>

            {associate.resumeFilename ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    padding: '12px',
                    border: '1px solid var(--color-border)',
                    borderRadius: '8px',
                    backgroundColor: 'var(--color-bg-card)',
                  }}
                >
                  <Icon name="file" size={24} style={{ color: 'var(--color-primary)' }} />
                  <div style={{ flexGrow: 1, minWidth: 0 }}>
                    <div
                      style={{
                        fontSize: '14px',
                        fontWeight: '500',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {associate.resumeFilename}
                    </div>
                    <div className="cell-sub" style={{ fontSize: '11px' }}>
                      Attached résumé
                    </div>
                  </div>
                  <a
                    href={`/api/v1/associates/${id}/resume`}
                    download={associate.resumeFilename}
                    className="btn btn-primary btn-sm"
                    style={{
                      textDecoration: 'none',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <Icon name="download" size={14} /> Download
                  </a>
                </div>

                {resumeNotice && (
                  <div
                    style={{
                      fontSize: '13px',
                      color: 'var(--color-info-fg, #0284c7)',
                      backgroundColor: 'var(--color-info-bg, #f0f9ff)',
                      padding: '10px 14px',
                      borderRadius: '6px',
                      border: '1px solid var(--color-info-border, #e0f2fe)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                    }}
                  >
                    <div>{resumeNotice}</div>
                    {suggestedSkills.length > 0 && (
                      <button
                        className="btn btn-primary btn-sm"
                        style={{ alignSelf: 'flex-start' }}
                        onClick={handleReviewSkills}
                      >
                        Review &amp; Add Skills
                      </button>
                    )}
                  </div>
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <label
                  className="dropzone"
                  style={{
                    padding: '24px 16px',
                    border: '2px dashed var(--color-border)',
                    borderRadius: '8px',
                    cursor: canEdit ? 'pointer' : 'default',
                    textAlign: 'center',
                    backgroundColor: 'var(--color-bg-card)',
                    display: 'block',
                  }}
                >
                  <Icon
                    name="upload"
                    size={24}
                    style={{ marginBottom: '8px', color: 'var(--color-primary)' }}
                  />
                  <div style={{ fontSize: '14px' }}>
                    {uploadingResume ? (
                      <strong>Uploading résumé...</strong>
                    ) : (
                      <strong>Click to upload résumé</strong>
                    )}
                    {canEdit && !uploadingResume && ' or drag a PDF/Word file here'}
                  </div>
                  {canEdit && (
                    <input
                      type="file"
                      accept=".pdf,.docx"
                      style={{ display: 'none' }}
                      disabled={uploadingResume}
                      onChange={(e) => {
                        if (e.target.files[0]) handleResumeUpload(e.target.files[0]);
                      }}
                    />
                  )}
                </label>
              </div>
            )}
          </div>

          {/* Previous Employment Card */}
          {(associate.employmentHistory || []).length > 0 && (
            <div className="card" style={{ padding: '24px' }}>
              <h3 style={{ marginTop: 0 }}>Previous Employment</h3>
              <p className="stat-hint" style={{ marginTop: 0 }}>
                From the résumé — Softility engagement history lives in Allocation &amp; Engagement
                History.
              </p>
              <div style={{ display: 'grid', gap: '8px' }}>
                {associate.employmentHistory.map((e, i) => (
                  <div key={i} style={{ fontSize: '13.5px' }}>
                    <strong>{e.company}</strong>
                    {e.title && <> · {e.title}</>}
                    <div className="cell-sub">
                      {e.startDate || '?'} – {e.endDate || 'present'}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Engagement History */}
      <div className="card" style={{ padding: '24px' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}
        >
          <h3 style={{ margin: 0 }}>Allocation &amp; Engagement History</h3>
          {canEdit && (
            <button className="btn btn-primary btn-sm" onClick={openAssign}>
              <Icon name="plus" size={14} /> Assign to Project
            </button>
          )}
        </div>
        {!allocations || allocations.length === 0 ? (
          <div className="empty-state" style={{ padding: '20px 0' }}>
            <Icon name="inbox" size={30} />
            <p style={{ fontSize: '13.5px' }}>No allocations found.</p>
          </div>
        ) : (
          <div
            className="table-wrap"
            style={{ margin: 0, boxShadow: 'none', border: '1px solid var(--color-border)' }}
          >
            <table>
              <thead>
                <tr>
                  <th>Project</th>
                  <th>Client</th>
                  <th>Billable</th>
                  <th>Allocation %</th>
                  <th>Start Date</th>
                  <th>End Date</th>
                  <th>Status</th>
                  {canEdit && <th style={{ width: '70px' }} aria-label="Actions" />}
                </tr>
              </thead>
              <tbody>
                {allocations.map((a) => {
                  const isCurrent = !a.endDate || new Date(a.endDate) >= new Date();
                  return (
                    <tr key={a.id}>
                      <td>
                        <div className="cell-main">{a.projectName}</div>
                        <div className="cell-sub">{a.projectCode}</div>
                      </td>
                      <td>{a.clientName}</td>
                      <td>
                        <Badge
                          value={a.billable ? 'Billable' : 'Non-billable'}
                          tone={a.billable ? 'green' : 'amber'}
                        />
                      </td>
                      <td>{a.allocationPercent}%</td>
                      <td>{a.startDate}</td>
                      <td>{a.endDate || '—'}</td>
                      <td>
                        <Badge value={isCurrent ? 'Current' : 'Ended'} />
                      </td>
                      {canEdit && (
                        <td className="actions">
                          {isCurrent && (
                            <button
                              className="btn btn-ghost btn-sm"
                              onClick={() => openEnd(a)}
                              aria-label={`End allocation on ${a.projectName}`}
                            >
                              End
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Manage Skills Modal */}
      <AnimatePresence>
        {managingSkills && (
          <Modal
            title="Manage Skills"
            onClose={() => setManagingSkills(false)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setManagingSkills(false)}>
                  Cancel
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleSaveSkills}
                  disabled={savingSkills}
                >
                  {savingSkills ? 'Saving…' : 'Save Skills'}
                </button>
              </>
            }
          >
            <div style={{ maxHeight: '60vh', overflowY: 'auto', paddingRight: '8px' }}>
              <SkillEditor
                taxonomy={taxonomy}
                value={selectedSkills}
                onChange={setSelectedSkills}
                onTaxonomyChange={reloadTaxonomy}
                showToast={showToast}
              />
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Add Certification Modal */}
      <AnimatePresence>
        {addingCert && (
          <Modal
            title="Add Certification"
            onClose={() => setAddingCert(false)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setAddingCert(false)}>
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={handleAddCert} disabled={savingCert}>
                  {savingCert ? 'Saving…' : 'Add Certification'}
                </button>
              </>
            }
          >
            {certErrors._general && <div className="form-alert">{certErrors._general}</div>}
            <div className="form-grid">
              <Field label="Certification Name" required error={certErrors.name} full>
                <input
                  value={certForm.name}
                  onChange={(e) => setCertForm((prev) => ({ ...prev, name: e.target.value }))}
                  placeholder="e.g. AWS Certified Solutions Architect"
                  className={certErrors.name ? 'invalid' : ''}
                />
              </Field>
              <Field label="Issuing Authority" error={certErrors.authority}>
                <input
                  value={certForm.authority}
                  onChange={(e) => setCertForm((prev) => ({ ...prev, authority: e.target.value }))}
                  placeholder="e.g. Amazon Web Services"
                />
              </Field>
              <Field label="Credential ID" error={certErrors.credentialId}>
                <input
                  value={certForm.credentialId}
                  onChange={(e) =>
                    setCertForm((prev) => ({ ...prev, credentialId: e.target.value }))
                  }
                  placeholder="e.g. AWS-ASA-12345"
                />
              </Field>
              <Field label="Issued Date" error={certErrors.issuedDate}>
                <input
                  type="date"
                  value={certForm.issuedDate}
                  onChange={(e) => setCertForm((prev) => ({ ...prev, issuedDate: e.target.value }))}
                />
              </Field>
              <Field label="Expiry Date" error={certErrors.expiryDate}>
                <input
                  type="date"
                  value={certForm.expiryDate}
                  onChange={(e) => setCertForm((prev) => ({ ...prev, expiryDate: e.target.value }))}
                />
              </Field>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* End Allocation Modal */}
      <AnimatePresence>
        {ending && (
          <Modal
            title={`End allocation · ${ending.row.projectName}`}
            onClose={() => setEnding(null)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setEnding(null)}>
                  Cancel
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleEndAllocation}
                  disabled={savingAlloc}
                >
                  {savingAlloc ? 'Ending…' : 'End allocation'}
                </button>
              </>
            }
          >
            {allocErrors._general && <div className="form-alert">{allocErrors._general}</div>}
            <p className="cell-sub" style={{ marginTop: 0 }}>
              The allocation stays in the history as “Ended” — nothing is deleted. Capacity frees up
              the day after the end date, so a same-day replacement should start the next day.
            </p>
            <div className="form-grid">
              <Field label="End date" required error={allocErrors.endDate}>
                <input
                  type="date"
                  min={ending.row.startDate}
                  value={ending.endDate}
                  onChange={(e) => setEnding((s) => ({ ...s, endDate: e.target.value }))}
                  className={allocErrors.endDate ? 'invalid' : ''}
                />
              </Field>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Assign to Project Modal */}
      <AnimatePresence>
        {assigning && assignForm && (
          <Modal
            title={`Assign ${associate.name} to a project`}
            onClose={() => setAssigning(false)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setAssigning(false)}>
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={handleAssign} disabled={savingAlloc}>
                  {savingAlloc ? 'Assigning…' : 'Assign'}
                </button>
              </>
            }
          >
            {allocErrors._general && <div className="form-alert">{allocErrors._general}</div>}
            <AllocationForm
              form={assignForm}
              setField={(k, v) => setAssignForm((f) => ({ ...f, [k]: v }))}
              setFields={(partial) => setAssignForm((f) => ({ ...f, ...partial }))}
              errors={allocErrors}
              projects={projects}
            />
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
}
