import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';
import { TrendChart, StackedBar, HBarChart } from '../components/charts.jsx';

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

export default function Dashboard() {
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
  const billablePct = s.totalAssociates > 0 ? Math.round((s.billableCount / s.totalAssociates) * 100) : 0;

  return (
    <>
      <div className="stat-grid">
        <StatCard icon="users" label="Total Associates" value={s.totalAssociates} hint={`${allocated} allocated to projects`} />
        <StatCard icon="dollar" label="Billable" value={s.billableCount} hint={`${billablePct}% billability ratio`} />
        <StatCard icon="bench" label="On Bench" value={s.benchCount} hint="No current allocation" />
        <StatCard icon="briefcase" label="Active Projects" value={s.activeProjects} hint={`across ${s.totalClients} clients`} />
      </div>

      <div className="panel-grid">
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

        <div className="card panel">
          <h2>Billability Mix</h2>
          <StackedBar
            total={s.totalAssociates}
            segments={[
              { label: 'Billable', value: s.billableCount, color: 'var(--chart-2)' },
              { label: 'Non-billable', value: s.nonBillableCount, color: 'var(--chart-3)' },
              { label: 'Bench', value: s.benchCount, color: 'var(--chart-4)' },
            ]}
          />
        </div>

        <div className="card panel">
          <h2>Delivery Mix</h2>
          <StackedBar
            total={s.totalAssociates}
            segments={[
              { label: 'Onshore', value: s.onshoreCount, color: 'var(--chart-1)' },
              { label: 'Offshore', value: s.offshoreCount, color: 'var(--chart-5)' },
            ]}
          />
        </div>

        <div className="card panel" style={{ gridColumn: '1 / -1' }}>
          <h2>Headcount by Client</h2>
          {s.clientHeadcounts.length === 0 ? (
            <p className="stat-hint">No current allocations.</p>
          ) : (
            <HBarChart
              rows={s.clientHeadcounts.map((c) => ({ label: c.clientName, value: c.headcount }))}
              color="var(--chart-1)"
              unit="associates"
            />
          )}
        </div>
      </div>
    </>
  );
}
