import { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';
import Modal from '../components/Modal.jsx';
import Badge from '../components/Badge.jsx';
import CollapsibleCard from '../components/CollapsibleCard.jsx';
import { HBarChart } from '../components/charts.jsx';
import { PROF_COLORS, PROF_LABELS, PROF_TONES } from '../proficiency.js';

const MAX_TOOLTIP_NAMES = 8;

/** Mirrors the Dashboard panel's gap tones — keep the two views consistent. */
function gapBadge(gap) {
  if (gap > 0) return { tone: 'red', label: `short ${gap}` };
  if (gap === 0) return { tone: 'amber', label: 'tight' };
  return { tone: 'green', label: `+${-gap} spare` };
}

function SkillBar({ skillName, counts, people, onDrillDown }) {
  const [hover, setHover] = useState(null); // { prof, leftPct }
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const byProf = (prof) => people.filter((p) => p.proficiency === prof);

  let cumulative = 0;

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '160px 1fr',
        alignItems: 'center',
        gap: '16px',
        marginBottom: '14px',
      }}
    >
      <div
        style={{
          fontWeight: '500',
          fontSize: '13.5px',
          textOverflow: 'ellipsis',
          overflow: 'hidden',
          whiteSpace: 'nowrap',
        }}
        title={skillName}
      >
        {skillName}
      </div>
      <div style={{ position: 'relative' }}>
        <div
          style={{
            display: 'flex',
            height: '22px',
            borderRadius: '4px',
            overflow: 'hidden',
            background: 'var(--color-muted)',
          }}
        >
          {total === 0 ? (
            <div
              style={{
                flexGrow: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '11px',
                color: 'var(--color-muted-fg)',
              }}
            >
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
            {byProf(hover.prof)
              .slice(0, MAX_TOOLTIP_NAMES)
              .map((p) => (
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
      <h3
        style={{
          margin: '0 0 20px 0',
          fontSize: '16px',
          fontWeight: '700',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {category}
      </h3>

      <div style={{ display: 'grid', gap: '4px' }}>
        {skills.map((s) => (
          <SkillBar
            key={s.skill}
            skillName={s.skill}
            counts={s.counts}
            people={s.people || []}
            onDrillDown={onDrillDown}
          />
        ))}
      </div>

      <div
        style={{
          display: 'flex',
          gap: '16px',
          flexWrap: 'wrap',
          marginTop: '20px',
          paddingTop: '16px',
          borderTop: '1px solid var(--color-border)',
        }}
      >
        {Object.entries(PROF_LABELS).map(([key, label]) => (
          <span
            key={key}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '12px',
              color: 'var(--color-foreground)',
            }}
          >
            <span
              style={{
                backgroundColor: PROF_COLORS[key],
                width: '12px',
                height: '12px',
                borderRadius: '2px',
                display: 'inline-block',
              }}
            />
            <span>{label}</span>
          </span>
        ))}
      </div>
    </div>
  );
}

export default function SkillReports() {
  const { data: reports, loading } = useLoad(() => api.list('reports/skills'), []);
  const { data: taxonomy } = useLoad(() => api.list('taxonomy'), []);
  const { data: gaps } = useLoad(() => api.list('reports/skill-gaps'), []);
  const [drill, setDrill] = useState(null); // { skill, prof, people }
  const [gapsOpen, setGapsOpen] = useState(true);

  const onDrillDown = (skill, prof, people) => setDrill({ skill, prof, people });

  const findTaxonomyIds = (skillName) => {
    if (!taxonomy) return {};
    for (const cat of taxonomy) {
      const sk = (cat.skills || []).find((s) => s.name === skillName);
      if (sk) {
        return { categoryId: cat.id, skillId: sk.id };
      }
    }
    return {};
  };

  const handleShowInRoster = () => {
    if (!drill) return;
    const { categoryId, skillId } = findTaxonomyIds(drill.skill);
    let url = `#/associates`;
    const params = [];
    if (categoryId) params.push(`categoryId=${categoryId}`);
    if (skillId) params.push(`skillId=${skillId}`);
    if (drill.prof) params.push(`minProficiency=${drill.prof}`);
    if (params.length) {
      url += `?${params.join('&')}`;
    }
    window.location.hash = url;
    setDrill(null);
  };

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
      {gaps && gaps.length > 0 && (
        <CollapsibleCard
          open={gapsOpen}
          onToggle={() => setGapsOpen(!gapsOpen)}
          header={
            <h3
              style={{
                margin: 0,
                fontSize: '16px',
                fontWeight: '700',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              Skill Gaps — open demand vs supply
            </h3>
          }
        >
          <div style={{ padding: '0 24px 24px' }}>
            <p className="stat-hint" style={{ marginTop: 0 }}>
              Every skill with open demand or rated associates. Gap = open seats minus bench supply
              at the required proficiency.
            </p>
            {gaps.filter((g) => g.gap > 0).length > 0 && (
              <HBarChart
                rows={gaps
                  .filter((g) => g.gap > 0)
                  .map((g) => ({ label: g.skillName, value: g.gap }))}
                color="var(--chart-1)"
                unit=" short"
              />
            )}
            <div style={{ marginTop: '12px' }}>
              {gaps.map((g) => {
                const badge = gapBadge(g.gap);
                return (
                  <div className="radar-row" key={g.skillId}>
                    <div>
                      <div className="cell-main">{g.skillName}</div>
                      <div className="cell-sub">
                        {g.category} · {g.demand} open · {g.benchSupply} on bench · {g.totalSupply}{' '}
                        total
                      </div>
                    </div>
                    <Badge tone={badge.tone} label={badge.label} />
                  </div>
                );
              })}
            </div>
          </div>
        </CollapsibleCard>
      )}

      {!reports || reports.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <Icon name="inbox" size={40} />
            <p>No skill ratings or taxonomy found in the system.</p>
          </div>
        </div>
      ) : (
        reports.map((r) => (
          <CategoryPanel
            key={r.category}
            category={r.category}
            skills={r.skills}
            onDrillDown={onDrillDown}
          />
        ))
      )}

      <AnimatePresence>
        {drill && (
          <Modal
            title={`${drill.skill} — ${PROF_LABELS[drill.prof]} (${drill.people.length})`}
            onClose={() => setDrill(null)}
            footer={
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn btn-primary" onClick={handleShowInRoster}>
                  Show in Roster
                </button>
                <button className="btn btn-ghost" onClick={() => setDrill(null)}>
                  Close
                </button>
              </div>
            }
          >
            {drill.people.map((p) => (
              <div className="radar-row" key={p.associateId}>
                <a
                  href={`#/associates/${p.associateId}`}
                  className="cell-main"
                  style={{ color: 'var(--color-primary)', textDecoration: 'none' }}
                  onClick={() => setDrill(null)}
                >
                  {p.name}
                </a>
                <Badge tone={PROF_TONES[drill.prof]} label={PROF_LABELS[drill.prof]} />
              </div>
            ))}
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
}
