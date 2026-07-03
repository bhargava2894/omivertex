import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';

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
  MASTERY: 'Mastery'
};

function SkillBar({ skillName, counts }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0);

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
              return (
                <div
                  key={prof}
                  style={{
                    width: `${pct}%`,
                    backgroundColor: PROF_COLORS[prof],
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    fontSize: '11px',
                    fontWeight: 'bold',
                  }}
                  title={`${PROF_LABELS[prof]}: ${val} associate(s) (${Math.round(pct)}%)`}
                >
                  {val > 0 && pct > 8 && val}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}

function CategoryPanel({ category, skills }) {
  return (
    <div className="card" style={{ padding: '24px', animation: 'fade-in 0.25s ease' }}>
      <h3 style={{ margin: '0 0 20px 0', fontSize: '16px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {category}
      </h3>
      
      <div style={{ display: 'grid', gap: '4px' }}>
        {skills.map((s) => (
          <SkillBar key={s.skill} skillName={s.skill} counts={s.counts} />
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
          <CategoryPanel key={r.category} category={r.category} skills={r.skills} />
        ))
      )}
    </div>
  );
}
