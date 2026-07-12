import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Badge from '../components/Badge.jsx';
import Field from '../components/Field.jsx';
import Icon from '../components/Icon.jsx';
import Modal from '../components/Modal.jsx';
import SkillEditor from '../components/SkillEditor.jsx';
import { PROF_LABELS } from '../proficiency.js';

/**
 * The ASSOCIATE-role self-service view: your own profile, plus "propose a
 * change" flows for skills and resume. Nothing edits live data — proposals
 * go to the admin approval queue.
 */
export default function MyProfile({ showToast }) {
  const { data: profile, loading } = useLoad(() => api.myProfile(), []);
  const { data: changes, reload: reloadChanges } = useLoad(() => api.myChanges(), []);
  const { data: taxonomy, reload: reloadTaxonomy } = useLoad(() => api.list('taxonomy'), []);

  const [editingSkills, setEditingSkills] = useState(null); // map skillId -> {proficiency, primary}
  const [submitting, setSubmitting] = useState(false);
  const [aiSuggestions, setAiSuggestions] = useState(null); // ParsedResumeResponse after a proposal

  if (loading || !profile) return <div className="skeleton-row" />;

  const pendingSkills = (changes || []).find((c) => c.type === 'SKILLS' && c.status === 'PENDING');
  const pendingResume = (changes || []).find((c) => c.type === 'RESUME' && c.status === 'PENDING');
  const lastRejected = (changes || []).find((c) => c.status === 'REJECTED');

  const openSkillEditor = () => {
    const held = {};
    (profile.skillGroups || []).forEach((group) =>
      (group.skills || []).forEach((s) => {
        held[s.skillId] = { proficiency: s.proficiency, primary: !!s.primary };
      })
    );
    setEditingSkills(held);
  };

  const submitSkills = async () => {
    setSubmitting(true);
    try {
      const skills = Object.entries(editingSkills || {})
        .filter(([, v]) => v && v.proficiency)
        .map(([skillId, v]) => ({
          skillId: Number(skillId),
          proficiency: v.proficiency,
          primary: !!v.primary,
        }));
      await api.proposeSkills(skills);
      showToast('Skill change submitted for approval');
      setEditingSkills(null);
      reloadChanges();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setSubmitting(false);
    }
  };

  const submitResume = async (file) => {
    if (!file) return;
    try {
      await api.proposeResume(file);
      showToast('Resume submitted for approval');
      reloadChanges();
      try {
        const parsed = await api.parseMyResume(file);
        if (parsed.suggestedSkills?.length > 0) setAiSuggestions(parsed);
      } catch {
        // parsing is best-effort; the proposal itself already succeeded
      }
    } catch (err) {
      showToast(err.message, true);
    }
  };

  const reviewSuggestedSkills = () => {
    const held = {};
    (profile.skillGroups || []).forEach((group) =>
      (group.skills || []).forEach((s) => {
        held[s.skillId] = { proficiency: s.proficiency, primary: !!s.primary };
      })
    );
    aiSuggestions.suggestedSkills.forEach((s) => {
      if (!held[s.skillId]) {
        held[s.skillId] = { proficiency: s.proficiency || 'INTERMEDIATE', primary: false };
      }
    });
    setEditingSkills(held);
    setAiSuggestions(null);
  };

  return (
    <div style={{ display: 'grid', gap: '20px' }}>
      {pendingSkills && (
        <div className="form-alert">
          Your skills change is awaiting admin approval (submitted{' '}
          {new Date(pendingSkills.createdAt).toLocaleDateString()}).
        </div>
      )}
      {pendingResume && (
        <div className="form-alert">
          Your resume “{pendingResume.resumeFilename}” is awaiting admin approval.
        </div>
      )}
      {aiSuggestions && (
        <div className="form-alert">
          <div>
            {aiSuggestions.suggestedSkills.length} skills detected in your resume
            {aiSuggestions.experienceSummary ? ` — ${aiSuggestions.experienceSummary}` : ''}
          </div>
          {!pendingSkills && (
            <button
              className="btn btn-primary btn-sm"
              style={{ marginTop: '8px' }}
              onClick={reviewSuggestedSkills}
            >
              Review &amp; propose skills
            </button>
          )}
        </div>
      )}
      {!pendingSkills && !pendingResume && lastRejected && (
        <div className="form-alert">
          Your last {lastRejected.type.toLowerCase()} change was rejected
          {lastRejected.note ? `: “${lastRejected.note}”` : '.'}
        </div>
      )}

      <div
        className="card profile-header"
        style={{ display: 'flex', gap: '20px', alignItems: 'center', padding: '24px' }}
      >
        <span
          className="user-avatar"
          style={{ width: '64px', height: '64px', fontSize: '24px', flexShrink: 0 }}
        >
          {profile.name.charAt(0).toUpperCase()}
        </span>
        <div style={{ flexGrow: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <h2 style={{ margin: 0, fontSize: '22px', fontWeight: '700' }}>{profile.name}</h2>
            <Badge value={profile.workMode} />
            {profile.currentProject && <Badge tone="green" label={profile.currentProject} />}
          </div>
          <div className="cell-sub" style={{ marginTop: '4px', fontSize: '14px' }}>
            <strong>{profile.designation || '—'}</strong> · {profile.email} · {profile.company}
          </div>
        </div>
      </div>

      <div className="card panel">
        <h2>
          <Icon name="activity" size={15} /> My Skills
        </h2>
        {(profile.skillGroups || []).length === 0 ? (
          <p className="stat-hint">No rated skills yet — propose your skill set below.</p>
        ) : (
          (profile.skillGroups || []).map((group) => (
            <div key={group.category} style={{ marginBottom: '10px' }}>
              <div className="cell-sub" style={{ marginBottom: '4px' }}>
                {group.category}
              </div>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                {group.skills.map((s) => (
                  <Badge
                    key={s.skillId}
                    tone={s.primary ? 'blue' : undefined}
                    label={`${s.name} · ${PROF_LABELS[s.proficiency] || s.proficiency}`}
                  />
                ))}
              </div>
            </div>
          ))
        )}
        <button
          className="btn btn-primary btn-sm"
          style={{ marginTop: '8px' }}
          disabled={!!pendingSkills}
          onClick={openSkillEditor}
        >
          <Icon name="plus" size={14} /> Propose skill changes
        </button>
      </div>

      <div className="card panel">
        <h2>
          <Icon name="sheet" size={15} /> My Resume
        </h2>
        <p className="cell-sub">{profile.resumeFilename || 'No resume on file.'}</p>
        <Field label="Upload a new resume (PDF/Word) — goes to admin approval">
          <input
            type="file"
            accept=".pdf,.doc,.docx"
            disabled={!!pendingResume}
            onChange={(e) => submitResume(e.target.files[0])}
          />
        </Field>
      </div>

      <div className="card panel">
        <h2>
          <Icon name="list" size={15} /> My Change Requests
        </h2>
        {(changes || []).length === 0 ? (
          <p className="stat-hint">No change requests yet.</p>
        ) : (
          (changes || []).map((c) => (
            <div className="radar-row" key={c.id}>
              <div>
                <div className="cell-main">
                  {c.type === 'SKILLS'
                    ? `Skills (${(c.proposedSkills || []).length} rated)`
                    : `Resume — ${c.resumeFilename}`}
                </div>
                <div className="cell-sub">
                  {new Date(c.createdAt).toLocaleString()}
                  {c.note ? ` · ${c.note}` : ''}
                </div>
              </div>
              <Badge
                tone={c.status === 'PENDING' ? 'amber' : c.status === 'APPROVED' ? 'green' : 'red'}
                label={c.status.toLowerCase()}
              />
            </div>
          ))
        )}
      </div>

      <AnimatePresence>
        {editingSkills != null && (
          <Modal
            title="Propose Skill Changes"
            onClose={() => setEditingSkills(null)}
            footer={
              <>
                <button className="btn btn-ghost" onClick={() => setEditingSkills(null)}>
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={submitSkills} disabled={submitting}>
                  {submitting ? 'Submitting…' : 'Submit for approval'}
                </button>
              </>
            }
          >
            <p className="stat-hint" style={{ marginTop: 0 }}>
              An admin reviews and approves your changes before they go live.
            </p>
            <SkillEditor
              taxonomy={taxonomy}
              value={editingSkills}
              onChange={setEditingSkills}
              onTaxonomyChange={reloadTaxonomy}
              showToast={showToast}
            />
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
}
