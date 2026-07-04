import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';

const PROF_COLORS = {
  NOVICE: '#9ca3af',
  FOUNDATIONAL: 'var(--chart-3)',
  INTERMEDIATE: 'var(--chart-1)',
  FUNCTIONAL_USER: 'var(--chart-2)',
  ADVANCE: 'var(--chart-5)',
  MASTERY: 'var(--chart-4)',
};

const PROF_LABELS = {
  NOVICE: 'Novice',
  FOUNDATIONAL: 'Foundational',
  INTERMEDIATE: 'Intermediate',
  FUNCTIONAL_USER: 'Functional User',
  ADVANCE: 'Advance',
  MASTERY: 'Mastery',
};

const PROF_TONES = {
  NOVICE: 'gray', FOUNDATIONAL: 'amber', INTERMEDIATE: 'blue',
  FUNCTIONAL_USER: 'green', ADVANCE: 'blue', MASTERY: 'green',
};

const MAX_TOOLTIP_NAMES = 8;

function SkillBar({ skillName, counts, people, onDrillDown }) {
  const [hover, setHover] = useState(null); // { prof, leftPct }
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const byProf = (prof) => people.filter((p) => p.proficiency === prof);

  let cumulative = 0;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '160px 1fr', alignItems: 'center', gap: '16px', marginBottom: '14px' }}>
      <div style={{ fontWeight: '500', fontSize: '13.5px', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }} title={skillName}>
        {skillName}
      </div>
      <div style={{ position: 'relative' }}>
        <div style={{ display: 'flex', height: '22px', borderRadius: '4px', overflow: 'hidden', background: 'var(--color-muted)' }}>
          {total === 0 ? (
            <div style={{ flexGrow: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', color: 'var(--color-muted-fg)' }}>
              No rated associates
            </div>
          ) : (
            Object.entries(counts).map(([prof, val]) => {
              if (val === 0) return null;
              const pct = (val / total) * 100;
              const leftPct = cumulative + pct / 2;
              cumulative += pct;
              const hovered = hover?.prof === prof;
              return (
                <button
                  key={prof}
                  type="button"
                  aria-label={`${skillName} ${PROF_LABELS[prof]}: ${val} associates — click for the full list`}
                  onPointerEnter={() => setHover({ prof, leftPct })}
                  onPointerLeave={() => setHover(null)}
                  onClick={() => onDrillDown(skillName, prof, byProf(prof))}
                  style={{
                    width: `${pct}%`,
                    backgroundColor: PROF_COLORS[prof],
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    fontSize: '11px',
                    fontWeight: 'bold',
                    border: 'none',
                    padding: 0,
                    cursor: 'pointer',
                    filter: hovered ? 'brightness(1.15)' : 'none',
                    transition: 'filter 0.15s ease',
                  }}
                >
                  {val > 0 && pct > 8 && val}
                </button>
              );
            })
          )}
        </div>

        {hover && (
          <div
            className="chart-tooltip"
            style={{
              left: `${hover.leftPct}%`,
              bottom: 'calc(100% + 8px)',
              top: 'auto',
              transform: 'translateX(-50%)',
            }}
          >
            <div className="tt-title">
              {skillName} · {PROF_LABELS[hover.prof]} ({byProf(hover.prof).length})
            </div>
            {byProf(hover.prof).slice(0, MAX_TOOLTIP_NAMES).map((p) => (
              <div className="tt-row" key={p.associateId}>
                <span className="tt-key" style={{ background: PROF_COLORS[hover.prof] }} />
                <span>{p.name}</span>
              </div>
            ))}
            {byProf(hover.prof).length > MAX_TOOLTIP_NAMES && (
              <div className="tt-row">
                <span className="tt-label">
                  +{byProf(hover.prof).length - MAX_TOOLTIP_NAMES} more — click to see all
                </span>
              </div>
            )}
            <div className="tt-row">
              <span className="tt-label">Click for profiles</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function CategoryPanel({ category, skills, onDrillDown }) {
  return (
    <div className="card" style={{ padding: '24px', animation: 'fade-in 0.25s ease' }}>
      <h3 style={{ margin: '0 0 20px 0', fontSize: '16px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {category}
      </h3>

      <div style={{ display: 'grid', gap: '4px' }}>
        {skills.map((s) => (
          <SkillBar key={s.skill} skillName={s.skill} counts={s.counts} people={s.people || []} onDrillDown={onDrillDown} />
        ))}
      </div>

      <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--color-border)' }}>
        {Object.entries(PROF_LABELS).map(([key, label]) => (
          <span key={key} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--color-foreground)' }}>
            <span style={{ backgroundColor: PROF_COLORS[key], width: '12px', height: '12px', borderRadius: '2px', display: 'inline-block' }} />
            <span>{label}</span>
          </span>
        ))}
      </div>
    </div>
  );
}

export default function SkillReports() {
  const { data: reports, loading } = useLoad(() => api.list('reports/skills'), []);
  const [drill, setDrill] = useState(null); // { skill, prof, people }

  const onDrillDown = (skill, prof, people) => setDrill({ skill, prof, people });

  if (loading) {
    return (
      <div>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gap: '24px' }}>
      {(!reports || reports.length === 0) ? (
        <div className="card">
          <div className="empty-state">
            <Icon name="inbox" size={40} />
            <p>No skill ratings or taxonomy found in the system.</p>
          </div>
        </div>
      ) : (
        reports.map((r) => (
          <CategoryPanel key={r.category} category={r.category} skills={r.skills} onDrillDown={onDrillDown} />
        ))
      )}

      {drill && (
        <Modal
          title={`${drill.skill} — ${PROF_LABELS[drill.prof]} (${drill.people.length})`}
          onClose={() => setDrill(null)}
          footer={<button className="btn btn-ghost" onClick={() => setDrill(null)}>Close</button>}
        >
          {drill.people.map((p) => (
            <div className="radar-row" key={p.associateId}>
              <a href={`#/associates/${p.associateId}`} className="cell-main" style={{ color: 'var(--color-primary)', textDecoration: 'none' }} onClick={() => setDrill(null)}>
                {p.name}
              </a>
              <Badge tone={PROF_TONES[drill.prof]} label={PROF_LABELS[drill.prof]} />
            </div>
          ))}
        </Modal>
      )}
    </div>
  );
}
