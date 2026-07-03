import { useState, useEffect } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Modal from '../components/Modal.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import Badge from '../components/Badge.jsx';

const PROFICIENCIES = [
  { value: 'NOVICE', label: 'Novice', tone: 'gray' },
  { value: 'FOUNDATIONAL', label: 'Foundational', tone: 'amber' },
  { value: 'INTERMEDIATE', label: 'Intermediate', tone: 'blue' },
  { value: 'FUNCTIONAL_USER', label: 'Functional User', tone: 'blue' },
  { value: 'ADVANCE', label: 'Advance', tone: 'green' },
  { value: 'MASTERY', label: 'Mastery', tone: 'green' },
];

export default function Profile({ id, showToast, canEdit }) {
  const { data: associate, loading: loadAssoc, reload: reloadAssoc } = useLoad(() => api.get('associates', id), [id]);
  const { data: certs, loading: loadCerts, reload: reloadCerts } = useLoad(() => api.getCertifications(id), [id]);
  const { data: allocations, loading: loadAllocs } = useLoad(() => api.list('allocations', { associateId: id }), [id]);
  const { data: taxonomy } = useLoad(() => api.list('taxonomy'), []);

  const [managingSkills, setManagingSkills] = useState(false);
  const [selectedSkills, setSelectedSkills] = useState({}); // skillId -> proficiency
  const [savingSkills, setSavingSkills] = useState(false);

  const [addingCert, setAddingCert] = useState(false);
  const [certForm, setCertForm] = useState({ name: '', authority: '', credentialId: '', issuedDate: '', expiryDate: '' });
  const [savingCert, setSavingCert] = useState(false);
  const [certErrors, setCertErrors] = useState({});

  // Initialize selected skills when modal opens
  useEffect(() => {
    if (managingSkills && associate) {
      const skillsMap = {};
      (associate.skillGroups || []).forEach(group => {
        (group.skills || []).forEach(skill => {
          skillsMap[skill.skillId] = skill.proficiency;
        });
      });
      setSelectedSkills(skillsMap);
    }
  }, [managingSkills, associate]);

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

  const getProficiencyInfo = (val) => {
    return PROFICIENCIES.find(p => p.value === val) || { label: val, tone: 'gray' };
  };

  const handleSaveSkills = async () => {
    setSavingSkills(true);
    const payload = Object.entries(selectedSkills)
      .filter(([, prof]) => prof && prof !== '')
      .map(([skillId, prof]) => ({
        skillId: Number(skillId),
        proficiency: prof
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
      setCertErrors({ ...err.fieldErrors, _general: Object.keys(err.fieldErrors).length ? null : err.message });
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
      {/* Header Profile Card */}
      <div className="card profile-header" style={{ display: 'flex', gap: '20px', alignItems: 'center', padding: '24px' }}>
        <span className="user-avatar" style={{ width: '64px', height: '64px', fontSize: '24px', flexShrink: 0 }}>
          {associate.name.charAt(0).toUpperCase()}
        </span>
        <div style={{ flexGrow: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <h2 style={{ margin: 0, fontSize: '22px', fontWeight: '700' }}>{associate.name}</h2>
            <Badge value={associate.workMode} />
            {associate.benchDays != null ? (
              <Badge value="Bench" label={`Bench · ${associate.benchDays}d`} tone="red" />
            ) : (
              <Badge value={associate.billable ? 'Billable' : 'Non-billable'} tone={associate.billable ? 'green' : 'amber'} />
            )}
          </div>
          <div className="cell-sub" style={{ marginTop: '4px', fontSize: '14px' }}>
            <strong>{associate.designation}</strong> · {associate.email} · {associate.company} · {associate.location || 'No Location'}
          </div>
          {(associate.primarySkill || associate.secondarySkill) && (
            <div style={{ marginTop: '8px', fontSize: '13px', color: 'var(--color-muted-fg)' }}>
              Legacy Skills: {[
                associate.primarySkill && `Primary: ${associate.primarySkill}`,
                associate.secondarySkill && `Secondary: ${associate.secondarySkill}`
              ].filter(Boolean).join(' | ')}
            </div>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }} className="form-grid">
        {/* Skills by Category */}
        <div className="card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0 }}>Skills Taxonomy</h3>
            {canEdit && (
              <button className="btn btn-ghost btn-sm" onClick={() => setManagingSkills(true)}>
                <Icon name="edit" size={14} /> Manage Skills
              </button>
            )}
          </div>
          {(!associate.skillGroups || associate.skillGroups.length === 0) ? (
            <div className="empty-state" style={{ padding: '20px 0' }}>
              <Icon name="inbox" size={30} />
              <p style={{ fontSize: '13.5px' }}>No structured skills recorded yet.</p>
            </div>
          ) : (
            <div style={{ display: 'grid', gap: '18px' }}>
              {associate.skillGroups.map(group => (
                <div key={group.category}>
                  <div className="cell-sub" style={{ fontWeight: '600', marginBottom: '8px', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.05em' }}>
                    {group.category}
                  </div>
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                    {group.skills.map(skill => {
                      const info = getProficiencyInfo(skill.proficiency);
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

        {/* Certifications Card */}
        <div className="card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0 }}>Certifications</h3>
            {canEdit && (
              <button className="btn btn-ghost btn-sm" onClick={() => setAddingCert(true)}>
                <Icon name="plus" size={14} /> Add Cert
              </button>
            )}
          </div>
          {(!certs || certs.length === 0) ? (
            <div className="empty-state" style={{ padding: '20px 0' }}>
              <Icon name="inbox" size={30} />
              <p style={{ fontSize: '13.5px' }}>No certifications recorded.</p>
            </div>
          ) : (
            <div className="table-wrap" style={{ margin: 0, boxShadow: 'none', border: '1px solid var(--color-border)' }}>
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
                  {certs.map(c => (
                    <tr key={c.id}>
                      <td>
                        <div className="cell-main">{c.name}</div>
                        {c.credentialId && <div className="cell-sub" style={{ fontSize: '11px' }}>ID: {c.credentialId}</div>}
                      </td>
                      <td>{c.authority || '—'}</td>
                      <td>
                        {c.expiryDate ? (
                          isExpiringSoon(c.expiryDate) ? (
                            <Badge value="Expiring" label={`Expires ${c.expiryDate}`} tone="red" />
                          ) : (
                            c.expiryDate
                          )
                        ) : (
                          'No Expiry'
                        )}
                      </td>
                      {canEdit && (
                        <td className="actions">
                          <button className="btn btn-danger btn-sm" onClick={() => handleDeleteCert(c.id, c.name)} aria-label="Delete cert">
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
      </div>

      {/* Engagement History */}
      <div className="card" style={{ padding: '24px' }}>
        <h3 style={{ margin: '0 0 16px 0' }}>Allocation &amp; Engagement History</h3>
        {(!allocations || allocations.length === 0) ? (
          <div className="empty-state" style={{ padding: '20px 0' }}>
            <Icon name="inbox" size={30} />
            <p style={{ fontSize: '13.5px' }}>No allocations found.</p>
          </div>
        ) : (
          <div className="table-wrap" style={{ margin: 0, boxShadow: 'none', border: '1px solid var(--color-border)' }}>
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
                </tr>
              </thead>
              <tbody>
                {allocations.map(a => {
                  const isCurrent = !a.endDate || new Date(a.endDate) >= new Date();
                  return (
                    <tr key={a.id}>
                      <td><div className="cell-main">{a.projectName}</div><div className="cell-sub">{a.projectCode}</div></td>
                      <td>{a.clientName}</td>
                      <td><Badge value={a.billable ? 'Billable' : 'Non-billable'} tone={a.billable ? 'green' : 'amber'} /></td>
                      <td>{a.allocationPercent}%</td>
                      <td>{a.startDate}</td>
                      <td>{a.endDate || '—'}</td>
                      <td><Badge value={isCurrent ? 'Current' : 'Ended'} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Manage Skills Modal */}
      {managingSkills && (
        <Modal
          title="Manage Skills"
          onClose={() => setManagingSkills(false)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setManagingSkills(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSaveSkills} disabled={savingSkills}>
                {savingSkills ? 'Saving…' : 'Save Skills'}
              </button>
            </>
          }
        >
          <div style={{ maxHeight: '60vh', overflowY: 'auto', paddingRight: '8px' }}>
            {(!taxonomy || taxonomy.length === 0) ? (
              <p>No taxonomy categories available. Set up the taxonomy admin page first.</p>
            ) : (
              <div style={{ display: 'grid', gap: '20px' }}>
                {taxonomy.map(cat => (
                  <div key={cat.id} className="card" style={{ padding: '16px', background: 'var(--color-muted)' }}>
                    <h4 style={{ margin: '0 0 12px 0', textTransform: 'uppercase', fontSize: '12px', letterSpacing: '0.05em' }}>{cat.name}</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '12px', alignItems: 'center' }}>
                      {(cat.skills || []).map(skill => (
                        <div key={skill.id} style={{ display: 'contents' }}>
                          <span style={{ fontSize: '13.5px', fontWeight: '500' }}>{skill.name}</span>
                          <select
                            value={selectedSkills[skill.id] || ''}
                            onChange={(e) => setSelectedSkills(prev => ({ ...prev, [skill.id]: e.target.value }))}
                            style={{ width: '150px', padding: '4px 8px', fontSize: '13px' }}
                          >
                            <option value="">(Not Held)</option>
                            {PROFICIENCIES.map(p => (
                              <option key={p.value} value={p.value}>{p.label}</option>
                            ))}
                          </select>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* Add Certification Modal */}
      {addingCert && (
        <Modal
          title="Add Certification"
          onClose={() => setAddingCert(false)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setAddingCert(false)}>Cancel</button>
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
                onChange={(e) => setCertForm(prev => ({ ...prev, name: e.target.value }))}
                placeholder="e.g. AWS Certified Solutions Architect"
                className={certErrors.name ? 'invalid' : ''}
              />
            </Field>
            <Field label="Issuing Authority" error={certErrors.authority}>
              <input
                value={certForm.authority}
                onChange={(e) => setCertForm(prev => ({ ...prev, authority: e.target.value }))}
                placeholder="e.g. Amazon Web Services"
              />
            </Field>
            <Field label="Credential ID" error={certErrors.credentialId}>
              <input
                value={certForm.credentialId}
                onChange={(e) => setCertForm(prev => ({ ...prev, credentialId: e.target.value }))}
                placeholder="e.g. AWS-ASA-12345"
              />
            </Field>
            <Field label="Issued Date" error={certErrors.issuedDate}>
              <input
                type="date"
                value={certForm.issuedDate}
                onChange={(e) => setCertForm(prev => ({ ...prev, issuedDate: e.target.value }))}
              />
            </Field>
            <Field label="Expiry Date" error={certErrors.expiryDate}>
              <input
                type="date"
                value={certForm.expiryDate}
                onChange={(e) => setCertForm(prev => ({ ...prev, expiryDate: e.target.value }))}
              />
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
