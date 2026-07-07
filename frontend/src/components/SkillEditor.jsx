import { useState } from 'react';
import { api } from '../api.js';
import { PROFICIENCIES } from '../proficiency.js';

/**
 * Controlled editor for an associate's rated skills, shared by the Profile
 * "Manage Skills" modal and the Add/Edit Associate form.
 *
 * value:    { [skillId]: { proficiency, primary } }  — a missing key means "not held"
 * onChange: (nextValue) => void
 * onTaxonomyChange: optional () => Promise|void — reload the taxonomy after an
 *   inline skill is created. When absent, the inline add affordance is hidden.
 * showToast: optional (msg, isError) => void
 *
 * A search box filters the (long) taxonomy. When nothing matches, an admin can
 * add the missing skill to a chosen category inline; it is created via the same
 * taxonomy endpoint and then appears in the list, ready to rate. Exactly one
 * skill can be starred as primary; starring one clears the others.
 */
export default function SkillEditor({ taxonomy, value, onChange, onTaxonomyChange, showToast }) {
  const held = value || {};
  const [search, setSearch] = useState('');
  const [addCategoryId, setAddCategoryId] = useState('');
  const [adding, setAdding] = useState(false);

  const term = search.trim();
  const q = term.toLowerCase();
  const cats = taxonomy || [];

  const setProficiency = (skillId, proficiency) => {
    const next = { ...held };
    if (!proficiency) {
      delete next[skillId]; // dropping the proficiency removes the skill (and its star)
    } else {
      next[skillId] = { proficiency, primary: held[skillId]?.primary || false };
    }
    onChange(next);
  };

  const setPrimary = (skillId) => {
    const next = {};
    Object.entries(held).forEach(([id, entry]) => {
      next[id] = { ...entry, primary: String(id) === String(skillId) };
    });
    onChange(next);
  };

  const filtered = q
    ? cats
        .map((cat) => ({
          ...cat,
          skills: (cat.skills || []).filter((s) => s.name.toLowerCase().includes(q)),
        }))
        .filter((cat) => cat.skills.length > 0)
    : cats;

  const anyMatch = cats.some((cat) =>
    (cat.skills || []).some((s) => s.name.toLowerCase().includes(q))
  );

  const handleAdd = async () => {
    if (!term || !addCategoryId) return;
    setAdding(true);
    try {
      await api.create('taxonomy/skills', { name: term, categoryId: Number(addCategoryId) });
      if (showToast) showToast(`Added “${term}” to the skill taxonomy`);
      setAddCategoryId('');
      if (onTaxonomyChange) await onTaxonomyChange(); // reload so the new skill appears
    } catch (err) {
      if (showToast) showToast(err.message, true);
    } finally {
      setAdding(false);
    }
  };

  if (!taxonomy || taxonomy.length === 0) {
    return <p>No taxonomy categories available. Set up the taxonomy admin page first.</p>;
  }

  return (
    <div>
      <input
        type="search"
        placeholder="Search skills…"
        aria-label="Search skills"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        style={{
          width: '100%',
          marginBottom: '12px',
          padding: '8px 10px',
          border: '1px solid var(--color-border)',
          borderRadius: '8px',
        }}
      />

      {term && !anyMatch && (
        <div
          className="card"
          style={{ padding: '12px', marginBottom: '12px', background: 'var(--color-muted)' }}
        >
          <div style={{ fontSize: '13.5px', marginBottom: '8px' }}>No skill matches “{term}”.</div>
          {onTaxonomyChange ? (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '13px' }}>Add “{term}” to</span>
              <select
                value={addCategoryId}
                aria-label="Category for the new skill"
                onChange={(e) => setAddCategoryId(e.target.value)}
                style={{ padding: '4px 8px', fontSize: '13px' }}
              >
                <option value="">Select category…</option>
                {cats.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                disabled={adding || !addCategoryId}
                onClick={handleAdd}
              >
                {adding ? 'Adding…' : 'Add skill'}
              </button>
            </div>
          ) : (
            <div style={{ fontSize: '13px' }}>
              Add it on the Taxonomy page, then it will appear here.
            </div>
          )}
        </div>
      )}

      <div style={{ display: 'grid', gap: '20px' }}>
        {filtered.map((cat) => (
          <div
            key={cat.id}
            className="card"
            style={{ padding: '16px', background: 'var(--color-muted)' }}
          >
            <h4
              style={{
                margin: '0 0 12px 0',
                textTransform: 'uppercase',
                fontSize: '12px',
                letterSpacing: '0.05em',
              }}
            >
              {cat.name}
            </h4>
            <div style={{ display: 'grid', gap: '10px' }}>
              {(cat.skills || []).map((skill) => {
                const entry = held[skill.id];
                const isHeld = !!entry;
                return (
                  <div
                    key={skill.id}
                    style={{ display: 'flex', alignItems: 'center', gap: '10px' }}
                  >
                    <span style={{ flex: 1, fontSize: '13.5px', fontWeight: '500' }}>
                      {skill.name}
                    </span>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      aria-label={`Mark ${skill.name} as primary skill`}
                      aria-pressed={!!entry?.primary}
                      disabled={!isHeld}
                      title={isHeld ? 'Mark as primary skill' : 'Choose a proficiency first'}
                      onClick={() => setPrimary(skill.id)}
                      style={{
                        padding: '2px 8px',
                        fontSize: '16px',
                        lineHeight: 1,
                        color: entry?.primary
                          ? 'var(--color-warning, #f59e0b)'
                          : 'var(--color-border, #cbd5e1)',
                      }}
                    >
                      {entry?.primary ? '★' : '☆'}
                    </button>
                    <select
                      value={entry?.proficiency || ''}
                      aria-label={`Proficiency in ${skill.name}`}
                      onChange={(e) => setProficiency(skill.id, e.target.value)}
                      style={{ width: '150px', padding: '4px 8px', fontSize: '13px' }}
                    >
                      <option value="">(Not Held)</option>
                      {PROFICIENCIES.map((p) => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
