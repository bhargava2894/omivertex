import { PROFICIENCIES } from '../proficiency.js';

/**
 * Controlled editor for an associate's rated skills, shared by the Profile
 * "Manage Skills" modal and the Add/Edit Associate form.
 *
 * value:    { [skillId]: { proficiency, primary } }  — a missing key means "not held"
 * onChange: (nextValue) => void
 *
 * Exactly one skill can be starred as primary; starring one clears the others.
 * The star is only enabled once a proficiency is chosen (you can't be primary in
 * a skill you don't hold).
 */
export default function SkillEditor({ taxonomy, value, onChange }) {
  const held = value || {};

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

  if (!taxonomy || taxonomy.length === 0) {
    return <p>No taxonomy categories available. Set up the taxonomy admin page first.</p>;
  }

  return (
    <div style={{ display: 'grid', gap: '20px' }}>
      {taxonomy.map((cat) => (
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
                <div key={skill.id} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
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
  );
}
