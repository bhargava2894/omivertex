import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Badge from '../components/Badge.jsx';

const ACTION_TONES = {
  CREATED: 'green',
  UPDATED: 'blue',
  DELETED: 'red',
  FILLED: 'blue',
  IMPORTED: 'amber',
};
const ENTITY_TYPES = ['', 'Client', 'Project', 'Associate', 'Allocation', 'Position', 'Import'];

const formatTime = (iso) =>
  new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

export default function AuditLog() {
  const [entityType, setEntityType] = useState('');
  const { data, loading } = useLoad(
    () => api.list('admin/audit', { entityType, limit: 200 }),
    [entityType]
  );

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <select
            className="filter-select"
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            aria-label="Filter by entity type"
          >
            {ENTITY_TYPES.map((t) => (
              <option key={t} value={t}>
                {t || 'All entities'}
              </option>
            ))}
          </select>
        </div>
      </div>

      <DataTable
        loading={loading}
        rows={data || []}
        emptyText="No changes recorded yet."
        columns={[
          {
            key: 'timestamp',
            label: 'When',
            render: (r) => <span className="cell-sub">{formatTime(r.timestamp)}</span>,
          },
          {
            key: 'username',
            label: 'Who',
            render: (r) => <span className="cell-main">{r.username}</span>,
          },
          {
            key: 'action',
            label: 'Action',
            render: (r) => <Badge tone={ACTION_TONES[r.action] || 'gray'} label={r.action} />,
          },
          { key: 'entityType', label: 'Entity' },
          { key: 'summary', label: 'What changed' },
        ]}
      />
    </>
  );
}
