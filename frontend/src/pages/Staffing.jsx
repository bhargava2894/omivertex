import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';

const getParam = (name) => {
  const searchPart = window.location.hash.split('?')[1];
  if (!searchPart) return '';
  return new URLSearchParams(searchPart).get(name) || '';
};

export default function Staffing() {
  const { data: clients, loading } = useLoad(() => api.list('staffing'), []);
  // Which client sections are expanded. Seeded from the ?clientId= deep link.
  const [open, setOpen] = useState(() => {
    const fromLink = getParam('clientId');
    return fromLink ? { [fromLink]: true } : null; // null = "open the first one"
  });

  if (loading) {
    return (
      <div>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  if (!clients || clients.length === 0) {
    return (
      <div className="card">
        <div className="empty-state">
          <Icon name="inbox" size={40} />
          <p>No current allocations. Assign associates to projects first.</p>
        </div>
      </div>
    );
  }

  const isOpen = (clientId) =>
    open === null ? String(clientId) === String(clients[0].clientId) : !!open[clientId];
  const toggle = (clientId) =>
    setOpen((prev) => {
      const base = prev === null ? { [clients[0].clientId]: true } : prev;
      return { ...base, [clientId]: !base[clientId] };
    });

  return (
    <div style={{ display: 'grid', gap: '16px' }}>
      {clients.map((c) => (
        <div className="card" key={c.clientId} style={{ padding: 0, overflow: 'hidden' }}>
          <button
            type="button"
            onClick={() => toggle(c.clientId)}
            aria-expanded={isOpen(c.clientId)}
            style={{
              display: 'flex',
              width: '100%',
              alignItems: 'center',
              gap: '12px',
              padding: '16px 20px',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <span aria-hidden="true" style={{ fontSize: '12px' }}>
              {isOpen(c.clientId) ? '▾' : '▸'}
            </span>
            <h3 style={{ margin: 0, flexGrow: 1 }}>{c.clientName}</h3>
            <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
            <Badge value="Non-billable" label={`${c.nonBillable} non-billable`} tone="amber" />
          </button>

          {isOpen(c.clientId) && (
            <div style={{ padding: '0 20px 20px', display: 'grid', gap: '16px' }}>
              {c.projects.map((p) => (
                <div key={p.projectId}>
                  <div
                    className="cell-sub"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      fontWeight: 600,
                      margin: '8px 0',
                    }}
                  >
                    <span>
                      {p.projectName} <span style={{ fontWeight: 400 }}>· {p.projectCode}</span>
                    </span>
                    <span style={{ fontWeight: 400 }}>
                      {p.billable} billable / {p.nonBillable} non-billable
                    </span>
                  </div>
                  <div
                    className="table-wrap"
                    style={{
                      margin: 0,
                      boxShadow: 'none',
                      border: '1px solid var(--color-border)',
                    }}
                  >
                    <table style={{ fontSize: '13px' }}>
                      <thead>
                        <tr>
                          <th>Associate</th>
                          <th>Designation</th>
                          <th>Allocation</th>
                          <th>Billing</th>
                          <th>Since</th>
                        </tr>
                      </thead>
                      <tbody>
                        {p.associates.map((a) => (
                          <tr key={`${p.projectId}-${a.associateId}`}>
                            <td>
                              <a className="cell-main" href={`#/associates/${a.associateId}`}>
                                {a.name}
                              </a>
                            </td>
                            <td>{a.designation || '—'}</td>
                            <td>{a.allocationPercent}%</td>
                            <td>
                              <Badge value={a.billable ? 'Billable' : 'Non-billable'} />
                            </td>
                            <td>{a.startDate}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
