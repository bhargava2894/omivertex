import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { api } from '../api.js';
import { collapse, useMotionVariants } from '../motion.js';
import Badge from './Badge.jsx';
import CollapsibleCard from './CollapsibleCard.jsx';
import { PROF_LABELS, PROF_TONES } from '../proficiency.js';
import { gapBadge } from '../skillGap.js';

/** Names shown per group before we stop listing and say how many are left. */
const MAX_NAMES = 4;

const FILTERS = [
  { key: 'short', label: 'Short', hint: 'need people', match: (g) => g.gap > 0 },
  {
    key: 'tight',
    label: 'Tight',
    hint: 'demand exactly met',
    match: (g) => g.gap === 0 && g.demand > 0,
  },
  { key: 'spare', label: 'Spare', hint: 'idle capacity', match: (g) => g.gap < 0 },
  { key: 'all', label: 'All skills', hint: 'everything rated', match: () => true },
];

/** Share of the open demand the bench can actually cover — the row's one visual. */
function CoverageBar({ gap }) {
  if (gap.demand === 0) {
    // The bucket already says "no open demand"; repeating it on every row is noise.
    return (
      <span className="gap-row-meta">
        {gap.benchSupply} idle of {gap.totalSupply} rated
      </span>
    );
  }
  const covered = Math.min(gap.benchSupply, gap.demand);
  const pct = Math.round((covered / gap.demand) * 100);
  const tone = pct === 100 ? 'var(--color-accent)' : 'var(--color-warn)';
  return (
    <div>
      <div className="gap-bar" role="presentation">
        <div className="gap-bar-fill" style={{ width: `${pct}%`, background: tone }} />
      </div>
      <div className="gap-row-meta" style={{ marginTop: '4px' }}>
        {covered} of {gap.demand} seat{gap.demand === 1 ? '' : 's'} coverable from bench
      </div>
    </div>
  );
}

function Person({ id, name, meta, proficiency }) {
  return (
    <div className="gap-person">
      <div style={{ minWidth: 0 }}>
        <a className="gap-person-name" href={`#/associates/${id}`}>
          {name}
        </a>
        <div className="gap-person-meta">{meta}</div>
      </div>
      {proficiency && <Badge tone={PROF_TONES[proficiency]} label={PROF_LABELS[proficiency]} />}
    </div>
  );
}

/**
 * One quadrant of the drill-down. The qualifier that applies to every row ("one level
 * below Advance") belongs in the header, not repeated on each person. Empty states say
 * why rather than vanishing.
 */
function Quadrant({ title, hint, count, empty, children }) {
  return (
    <div className="gap-quadrant">
      <div className="gap-quadrant-head">
        <span className="gap-quadrant-title">{title}</span>
        <span className="gap-quadrant-count">{count}</span>
      </div>
      <span className="gap-quadrant-hint">{hint}</span>
      {count === 0 ? <p className="gap-quadrant-empty">{empty}</p> : children}
    </div>
  );
}

function More({ total }) {
  if (total <= MAX_NAMES) return null;
  return <div className="gap-more">+{total - MAX_NAMES} more</div>;
}

function GapDetail({ detail }) {
  const bar = PROF_LABELS[detail.threshold];
  // With no open demand there are no positions and no proficiency bar, so those two
  // quadrants can only ever be empty. Rendering them would be dead space, not information.
  const hasDemand = detail.openDemand.length > 0;

  return (
    <div className="gap-quadrants">
      {hasDemand && (
        <Quadrant
          title="Who's asking"
          hint="open positions requiring this skill"
          count={detail.openDemand.length}
          empty="No open position requires this skill."
        >
          {detail.openDemand.slice(0, MAX_NAMES).map((p) => (
            <div className="gap-person" key={p.positionId}>
              <div style={{ minWidth: 0 }}>
                <a className="gap-person-name" href="#/positions">
                  {p.title}
                </a>
                <div className="gap-person-meta">
                  {p.clientName} · {p.headcount} seat{p.headcount === 1 ? '' : 's'}
                  {p.startDate ? ` · starts ${p.startDate}` : ''}
                </div>
              </div>
              <Badge
                tone={PROF_TONES[p.minProficiency]}
                label={PROF_LABELS[p.minProficiency] || 'any'}
              />
            </div>
          ))}
          <More total={detail.openDemand.length} />
        </Quadrant>
      )}

      <Quadrant
        title="Free now"
        hint={hasDemand ? `on the bench at ${bar} or above` : 'rated and idle right now'}
        count={detail.benchSupply.length}
        empty="Every qualified holder is already allocated."
      >
        {detail.benchSupply.slice(0, MAX_NAMES).map((p) => (
          <Person
            key={p.associateId}
            id={p.associateId}
            name={p.name}
            proficiency={p.proficiency}
            meta={
              p.benchDays != null ? `${p.designation} · ${p.benchDays}d on bench` : p.designation
            }
          />
        ))}
        <More total={detail.benchSupply.length} />
      </Quadrant>

      <Quadrant
        title="Coming free"
        hint="qualified, rolling off within 30 days"
        count={detail.rollingOff.length}
        empty="Nobody qualified rolls off within 30 days."
      >
        {detail.rollingOff.slice(0, MAX_NAMES).map((p) => (
          <Person
            key={p.associateId}
            id={p.associateId}
            name={p.name}
            proficiency={p.proficiency}
            meta={`${p.projectName} · frees ${p.endDate}`}
          />
        ))}
        <More total={detail.rollingOff.length} />
      </Quadrant>

      {hasDemand && (
        <Quadrant
          title="Could train"
          hint={`one level below ${bar}`}
          count={detail.nearMiss.length}
          empty={`Nobody is within one level of ${bar}.`}
        >
          {detail.nearMiss.slice(0, MAX_NAMES).map((p) => (
            <Person
              key={p.associateId}
              id={p.associateId}
              name={p.name}
              proficiency={p.proficiency}
              meta={p.onBench ? 'on bench' : 'allocated'}
            />
          ))}
          <More total={detail.nearMiss.length} />
        </Quadrant>
      )}
    </div>
  );
}

export default function SkillGapPanel({ gaps }) {
  const [open, setOpen] = useState(true);
  const [filter, setFilter] = useState(null); // null = pick the most urgent non-empty bucket
  const [expanded, setExpanded] = useState(null);
  const [details, setDetails] = useState({}); // skillId -> detail, cached for the session
  const [loading, setLoading] = useState(null);
  const anim = useMotionVariants(collapse);

  const counts = useMemo(
    () => Object.fromEntries(FILTERS.map((f) => [f.key, gaps.filter(f.match).length])),
    [gaps]
  );
  // Land on the rows that need a decision; fall back to everything when nothing is short.
  const active = filter ?? (counts.short > 0 ? 'short' : 'all');
  // The API sorts worst-shortfall-first, which puts the *smallest* surplus on top of the
  // spare bucket — backwards. Biggest idle pool should lead when you're looking for slack.
  const rows = gaps
    .filter(FILTERS.find((f) => f.key === active).match)
    .sort((a, b) => (active === 'spare' ? a.gap - b.gap : 0));

  const toggleRow = async (skillId) => {
    if (expanded === skillId) {
      setExpanded(null);
      return;
    }
    setExpanded(skillId);
    if (details[skillId]) return;
    setLoading(skillId);
    try {
      const detail = await api.get('reports/skill-gaps', skillId);
      setDetails((prev) => ({ ...prev, [skillId]: detail }));
    } finally {
      setLoading(null);
    }
  };

  return (
    <CollapsibleCard
      open={open}
      onToggle={() => setOpen(!open)}
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
      <div style={{ padding: '18px 24px 24px' }}>
        <div className="gap-triage">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              className="gap-tile"
              aria-pressed={active === f.key}
              onClick={() => setFilter(f.key)}
            >
              <span className="gap-tile-value">{counts[f.key]}</span>
              <span className="gap-tile-label">{f.label}</span>
              <span className="gap-tile-hint">{f.hint}</span>
            </button>
          ))}
        </div>

        {rows.length === 0 ? (
          <p className="stat-hint" style={{ margin: 0 }}>
            Nothing in this bucket.
          </p>
        ) : (
          rows.map((g) => {
            const badge = gapBadge(g.gap, g.demand);
            const isOpen = expanded === g.skillId;
            return (
              <div key={g.skillId}>
                <button
                  type="button"
                  className="gap-row"
                  aria-expanded={isOpen}
                  onClick={() => toggleRow(g.skillId)}
                >
                  <span
                    aria-hidden="true"
                    className="staffing-toggle-arrow"
                    style={{ transform: isOpen ? 'rotate(90deg)' : 'none' }}
                  >
                    ▸
                  </span>
                  <span style={{ minWidth: 0 }}>
                    <span className="gap-row-name">{g.skillName}</span>
                    <span className="gap-row-meta" style={{ display: 'block' }}>
                      {g.category} · {g.totalSupply} rated
                    </span>
                  </span>
                  <CoverageBar gap={g} />
                  <Badge tone={badge.tone} label={badge.label} />
                </button>

                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div
                      initial={anim.initial}
                      animate={anim.animate}
                      exit={anim.exit}
                      style={{ overflow: 'hidden' }}
                    >
                      <div className="gap-detail">
                        {loading === g.skillId || !details[g.skillId] ? (
                          <div className="skeleton-row" />
                        ) : (
                          <GapDetail detail={details[g.skillId]} />
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })
        )}
      </div>
    </CollapsibleCard>
  );
}
