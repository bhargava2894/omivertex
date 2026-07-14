import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useMotionVariants, collapse } from '../motion.js';
import Badge from './Badge.jsx';
import Icon from './Icon.jsx';
import { TrendChart } from './charts.jsx';

/**
 * Why the number moved. A BENCH_EXIT *raises* utilization — the leaver drops out of the
 * denominator while billable FTE is unchanged — so it is spelled out rather than left to
 * the reader, who will otherwise assume every exit is bad news.
 */
function driverText(d) {
  switch (d.kind) {
    case 'ROLL_OFF':
      return `${d.associateName} rolls off ${d.projectName}`;
    case 'RAMP_UP':
      return `${d.associateName} starts on ${d.projectName}`;
    case 'BENCH_EXIT':
      return `${d.associateName} exits — on the bench, so utilization rises`;
    case 'BILLABLE_EXIT':
      return `${d.associateName} exits — billable today, so utilization falls`;
    default:
      return d.associateName;
  }
}

const DRIVER_TONE = {
  ROLL_OFF: 'amber',
  RAMP_UP: 'green',
  BENCH_EXIT: 'green',
  BILLABLE_EXIT: 'amber',
};

function deltaBadge(delta) {
  if (delta > 0) return { tone: 'green', label: `▲ +${delta}` };
  if (delta < 0) return { tone: 'amber', label: `▼ ${delta}` };
  return { tone: 'gray', label: '—' };
}

/**
 * A flat number has two very different meanings: nothing is scheduled, or scheduled
 * events are cancelling each other out. The panel used to render both as a bare
 * percentage, which is the reason this summary line exists.
 */
function summary(point) {
  const count = point.drivers.length + point.omittedDrivers;
  if (count === 0) return 'Nothing scheduled to change by then.';
  if (point.deltaPoints === 0) {
    return `${count} scheduled ${count === 1 ? 'event' : 'events'} that cancel out — the flat number is hiding movement.`;
  }
  return null;
}

/**
 * Tolerate a forecast point that predates the drivers field. A server running an older
 * build (or a stale cached bundle) must not blank the whole dashboard: React unmounts the
 * entire tree on a render throw, so one missing field would cost the user every panel.
 */
function normalize(point) {
  return {
    label: point.label,
    percent: point.percent ?? 0,
    deltaPoints: point.deltaPoints ?? 0,
    drivers: point.drivers ?? [],
    omittedDrivers: point.omittedDrivers ?? 0,
    explained: Array.isArray(point.drivers),
  };
}

export default function UtilizationForecastPanel({ points, viewMode }) {
  const [expanded, setExpanded] = useState(null);
  const anim = useMotionVariants(collapse);
  const forecast = points || [];

  return (
    <div className="card panel">
      <h2>
        <Icon name="activity" size={15} /> Utilization Forecast
      </h2>
      <p className="stat-hint" style={{ marginTop: 0 }}>
        From known end dates and recorded exits — assumes no new assignments.
      </p>

      {viewMode === 'charts' ? (
        <TrendChart
          points={forecast.map((p) => ({ month: p.label, percent: p.percent }))}
          series={[{ key: 'percent', label: 'Utilization %', color: 'var(--chart-2)' }]}
        />
      ) : (
        forecast.map((raw) => {
          const p = normalize(raw);
          const isOpen = expanded === p.label;
          const badge = deltaBadge(p.deltaPoints);
          const note = summary(p);

          // "Today" is the baseline the others are measured against — nothing to explain.
          // An unexplained point (older server) falls back to the same plain row.
          if (p.label === 'Today' || !p.explained) {
            return (
              <div className="radar-row" key={p.label}>
                <div className="cell-main">{p.label}</div>
                <div className="radar-right">
                  <span className="forecast-pct">{p.percent}%</span>
                  {p.label === 'Today' && <span className="gap-row-meta">baseline</span>}
                </div>
              </div>
            );
          }

          return (
            <div key={p.label}>
              <button
                type="button"
                className="forecast-row"
                aria-expanded={isOpen}
                onClick={() => setExpanded(isOpen ? null : p.label)}
              >
                <span
                  aria-hidden="true"
                  className="staffing-toggle-arrow"
                  style={{ transform: isOpen ? 'rotate(90deg)' : 'none' }}
                >
                  ▸
                </span>
                <span className="cell-main">{p.label}</span>
                <span className="forecast-pct">{p.percent}%</span>
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
                    <div className="forecast-detail">
                      {note && <p className="gap-row-meta">{note}</p>}
                      {p.drivers.map((d, i) => (
                        <div className="forecast-driver" key={`${d.kind}-${d.associateId}-${i}`}>
                          <Badge tone={DRIVER_TONE[d.kind] || 'gray'} label={d.date} />
                          <span>{driverText(d)}</span>
                        </div>
                      ))}
                      {p.omittedDrivers > 0 && (
                        <p className="gap-row-meta">…and {p.omittedDrivers} more.</p>
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
  );
}
