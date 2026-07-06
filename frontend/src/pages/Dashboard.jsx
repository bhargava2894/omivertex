import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';
import Badge from '../components/Badge.jsx';
import { TrendChart, DonutChart, VBarChart } from '../components/charts.jsx';

function StatCard({ icon, label, value, hint }) {
  return (
    <div className="card stat-card">
      <div className="stat-label">
        <span className="stat-icon">
          <Icon name={icon} size={15} />
        </span>
        {label}
      </div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}

function SplitBar({ label, value, total, color }) {
  const pct = total > 0 ? Math.round((value / total) * 100) : 0;
  return (
    <div className="split-row">
      <div className="split-head">
        <span>{label}</span>
        <strong>
          {value} · {pct}%
        </strong>
      </div>
      <div
        className="split-track"
        role="img"
        aria-label={`${label}: ${value} of ${total} (${pct}%)`}
      >
        <div className="split-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
    </div>
  );
}

export default function Dashboard() {
  const [viewMode, setViewMode] = useState(
    () => localStorage.getItem('ov-dashboard-view') || 'charts'
  );

  const toggleViewMode = (mode) => {
    setViewMode(mode);
    localStorage.setItem('ov-dashboard-view', mode);
  };

  const { data, loading, error } = useLoad(api.dashboard);

  if (loading) {
    return (
      <div>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }
  if (error) return <div className="form-alert">Could not load dashboard: {error.message}</div>;

  const s = data;
  const allocated = s.billableCount + s.nonBillableCount;
  const billablePct =
    s.totalAssociates > 0 ? Math.round((s.billableCount / s.totalAssociates) * 100) : 0;

  const maxHeadcount = Math.max(1, ...s.clientHeadcounts.map((c) => c.headcount));

  return (
    <>
      <div className="toolbar" style={{ justifyContent: 'flex-end', marginBottom: '16px' }}>
        <div
          className="toolbar-actions"
          style={{
            background: 'var(--color-muted)',
            padding: '2px',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            gap: '2px',
          }}
        >
          <button
            className={`btn btn-sm ${viewMode === 'charts' ? 'btn-primary' : 'btn-ghost'}`}
            style={{
              border: 'none',
              minHeight: '30px',
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '13px',
            }}
            onClick={() => toggleViewMode('charts')}
          >
            <Icon name="dashboard" size={13} />
            Visual Charts
          </button>
          <button
            className={`btn btn-sm ${viewMode === 'list' ? 'btn-primary' : 'btn-ghost'}`}
            style={{
              border: 'none',
              minHeight: '30px',
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '13px',
            }}
            onClick={() => toggleViewMode('list')}
          >
            <Icon name="list" size={13} />
            Progress Lists
          </button>
        </div>
      </div>

      <div className="stat-grid">
        <StatCard
          icon="users"
          label="Total Associates"
          value={s.totalAssociates}
          hint={`${allocated} allocated to projects`}
        />
        <StatCard
          icon="activity"
          label="Billable Utilization"
          value={`${s.utilizationPercent}%`}
          hint="FTE-weighted, of total workforce"
        />
        <StatCard
          icon="dollar"
          label="Billable"
          value={s.billableCount}
          hint={`${billablePct}% of headcount`}
        />
        <StatCard icon="bench" label="On Bench" value={s.benchCount} hint="No current allocation" />
        <StatCard
          icon="briefcase"
          label="Active Projects"
          value={s.activeProjects}
          hint={`across ${s.totalClients} clients`}
        />
        <StatCard
          icon="target"
          label="Open Demand"
          value={s.openPositions}
          hint="Positions awaiting a match"
        />
      </div>

      <div className="panel-grid">
        <div className="card panel">
          <h2>
            <Icon name="radar" size={15} /> Roll-off Radar — next 30 days
          </h2>
          {s.upcomingRolloffs.length === 0 ? (
            <p className="stat-hint">No allocations ending in the next 30 days.</p>
          ) : (
            s.upcomingRolloffs.map((r) => (
              <div className="radar-row" key={r.allocationId}>
                <div>
                  <div className="cell-main">{r.associateName}</div>
                  <div className="cell-sub">
                    {r.projectName} · {r.clientName}
                  </div>
                </div>
                <div className="radar-right">
                  <span className="cell-sub">{r.endDate}</span>
                  <Badge
                    tone={r.daysLeft <= 7 ? 'red' : r.daysLeft <= 14 ? 'amber' : 'blue'}
                    label={r.daysLeft <= 0 ? 'today' : `${r.daysLeft}d left`}
                  />
                </div>
              </div>
            ))
          )}
        </div>

        <div className="card panel">
          <h2>
            <Icon name="radar" size={15} /> Cert Expiry Radar — next 90 days
          </h2>
          {!s.expiringCertifications || s.expiringCertifications.length === 0 ? (
            <p className="stat-hint">No certifications expiring in the next 90 days.</p>
          ) : (
            s.expiringCertifications.map((c) => (
              <div className="radar-row" key={c.certificationId}>
                <div>
                  <div className="cell-main">{c.associateName}</div>
                  <div className="cell-sub">{c.name}</div>
                </div>
                <div className="radar-right">
                  <span className="cell-sub">{c.expiryDate}</span>
                  <Badge
                    tone={c.daysLeft <= 30 ? 'red' : c.daysLeft <= 60 ? 'amber' : 'blue'}
                    label={c.daysLeft <= 0 ? 'expired' : `${c.daysLeft}d left`}
                  />
                </div>
              </div>
            ))
          )}
        </div>

        <div className="card panel">
          <h2>
            <Icon name="bench" size={15} /> Bench Aging
          </h2>
          <div className="bench-buckets">
            <div className="bench-bucket">
              <strong>{s.benchAging.days0to30}</strong>
              <span>0–30 days</span>
            </div>
            <div className="bench-bucket warn">
              <strong>{s.benchAging.days31to60}</strong>
              <span>31–60 days</span>
            </div>
            <div className="bench-bucket danger">
              <strong>{s.benchAging.days60plus}</strong>
              <span>60+ days</span>
            </div>
          </div>
          {s.benchAssociates.length === 0 ? (
            <p className="stat-hint">Nobody on the bench — fully deployed.</p>
          ) : (
            s.benchAssociates.slice(0, 6).map((b) => (
              <div className="radar-row" key={b.id}>
                <div>
                  <div className="cell-main">{b.name}</div>
                  <div className="cell-sub">{b.designation || '—'}</div>
                </div>
                <Badge
                  tone={b.benchDays > 60 ? 'red' : b.benchDays > 30 ? 'amber' : 'green'}
                  label={`${b.benchDays}d on bench`}
                />
              </div>
            ))
          )}
        </div>
        <div className="card panel">
          <h2>Billability Mix</h2>
          {viewMode === 'charts' ? (
            <DonutChart
              segments={[
                { label: 'Billable', value: s.billableCount, color: 'var(--chart-2)' },
                { label: 'Non-billable', value: s.nonBillableCount, color: 'var(--chart-3)' },
                { label: 'Bench', value: s.benchCount, color: 'var(--chart-4)' },
              ]}
            />
          ) : (
            <>
              <SplitBar
                label="Billable"
                value={s.billableCount}
                total={s.totalAssociates}
                color="var(--color-accent)"
              />
              <SplitBar
                label="Non-billable"
                value={s.nonBillableCount}
                total={s.totalAssociates}
                color="var(--color-warn)"
              />
              <SplitBar
                label="Bench"
                value={s.benchCount}
                total={s.totalAssociates}
                color="var(--color-destructive)"
              />
            </>
          )}
        </div>

        <div className="card panel">
          <h2>Delivery Mix</h2>
          {viewMode === 'charts' ? (
            <DonutChart
              segments={[
                { label: 'Onshore', value: s.onshoreCount, color: 'var(--chart-1)' },
                { label: 'Offshore', value: s.offshoreCount, color: 'var(--chart-5)' },
              ]}
            />
          ) : (
            <>
              <SplitBar
                label="Onshore"
                value={s.onshoreCount}
                total={s.totalAssociates}
                color="var(--color-primary)"
              />
              <SplitBar
                label="Offshore"
                value={s.offshoreCount}
                total={s.totalAssociates}
                color="#7c3aed"
              />
              <div className="legend">
                <span>
                  <span className="legend-dot" style={{ background: 'var(--color-primary)' }} />
                  Onshore — client site / US
                </span>
                <span>
                  <span className="legend-dot" style={{ background: '#7c3aed' }} />
                  Offshore — delivery centers
                </span>
              </div>
            </>
          )}
        </div>

        {viewMode === 'charts' && (
          <div className="card panel" style={{ gridColumn: '1 / -1' }}>
            <h2>Staffing Trend — last 6 months</h2>
            <TrendChart
              points={s.staffingTrend}
              series={[
                { key: 'total', label: 'Allocated associates', color: 'var(--chart-1)' },
                { key: 'billable', label: 'Billable', color: 'var(--chart-2)' },
              ]}
            />
          </div>
        )}

        <div className="card panel" style={{ gridColumn: '1 / -1' }}>
          <h2>Headcount by Client</h2>
          {s.clientHeadcounts.length === 0 ? (
            <p className="stat-hint">No current allocations.</p>
          ) : viewMode === 'charts' ? (
            <VBarChart
              rows={s.clientHeadcounts.map((c) => ({ label: c.clientName, value: c.headcount }))}
              unit="associates"
            />
          ) : (
            s.clientHeadcounts.map((c, i) => (
              <div className="rank-row" key={c.clientName}>
                <span className="rank-name">{c.clientName}</span>
                <span className="rank-count">{c.headcount} associates</span>
                <div
                  className="rank-track"
                  role="img"
                  aria-label={`${c.clientName}: ${c.headcount} associates`}
                >
                  <div
                    className="rank-fill"
                    style={{
                      width: `${(c.headcount / maxHeadcount) * 100}%`,
                      background: `var(--chart-${(i % 5) + 1})`,
                    }}
                  />
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </>
  );
}
