import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';

export default function AccessRequests({ showToast }) {
  const [filter, setFilter] = useState('PENDING'); // PENDING, APPROVED, REJECTED, ALL
  const { data, loading, reload } = useLoad(() => api.listRequests(), []);
  const [processing, setProcessing] = useState(null); // id of current processing action
  const [roleChoice, setRoleChoice] = useState({}); // per-request role to grant on approve

  const handleApprove = async (id) => {
    if (processing) return;
    setProcessing(id);
    try {
      const role = roleChoice[id] || 'VIEWER';
      await api.approveRequest(id, role);
      showToast(
        `Access approved as ${role === 'ADMIN' ? 'Admin' : role === 'ASSOCIATE' ? 'Associate' : 'Viewer'}`
      );
      reload();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setProcessing(null);
    }
  };

  const handleReject = async (id) => {
    if (processing) return;
    setProcessing(id);
    try {
      await api.rejectRequest(id);
      showToast('Access request rejected');
      reload();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setProcessing(null);
    }
  };

  const filteredRequests = (data || []).filter((r) => {
    if (filter === 'ALL') return true;
    return r.status === filter;
  });

  const getStatusTone = (status) => {
    if (status === 'APPROVED') return 'green';
    if (status === 'REJECTED') return 'red';
    return 'amber';
  };

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-filters">
          <div style={{ display: 'flex', gap: '8px' }}>
            {['PENDING', 'APPROVED', 'REJECTED', 'ALL'].map((tab) => (
              <button
                key={tab}
                className={`btn ${filter === tab ? 'btn-primary' : 'btn-ghost'}`}
                style={{ fontSize: '13.5px', padding: '6px 14px' }}
                onClick={() => setFilter(tab)}
              >
                {tab.charAt(0) + tab.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>
      </div>

      {loading ? (
        <div>
          {[...Array(5)].map((_, i) => (
            <div key={i} className="skeleton-row" />
          ))}
        </div>
      ) : filteredRequests.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <Icon name="inbox" size={40} />
            <p>No access requests found matching the filter.</p>
          </div>
        </div>
      ) : (
        <div className="card table-wrap" style={{ animation: 'fade-in 0.3s ease' }}>
          <table>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Requested Date</th>
                <th scope="col">Status</th>
                <th scope="col" aria-label="Actions" style={{ width: '260px' }} />
              </tr>
            </thead>
            <tbody>
              {filteredRequests.map((row) => (
                <tr key={row.id}>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span
                        className="user-avatar"
                        style={{ width: '28px', height: '28px', fontSize: '12px' }}
                      >
                        {row.name.charAt(0).toUpperCase()}
                      </span>
                      <div className="cell-main">{row.name}</div>
                    </div>
                  </td>
                  <td>{row.email}</td>
                  <td>{row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '—'}</td>
                  <td>
                    <Badge value={row.status} tone={getStatusTone(row.status)} />
                  </td>
                  <td className="actions" style={{ textAlign: 'right' }}>
                    {row.status === 'PENDING' && (
                      <div style={{ display: 'inline-flex', gap: '8px', alignItems: 'center' }}>
                        <select
                          className="input input-sm"
                          aria-label={`Role to grant ${row.name}`}
                          disabled={processing !== null}
                          value={roleChoice[row.id] || 'VIEWER'}
                          onChange={(e) =>
                            setRoleChoice((prev) => ({ ...prev, [row.id]: e.target.value }))
                          }
                          style={{ padding: '4px 8px', fontSize: '13px' }}
                        >
                          <option value="VIEWER">Viewer</option>
                          <option value="ASSOCIATE">Associate</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                        <button
                          className="btn btn-primary btn-sm"
                          disabled={processing !== null}
                          onClick={() => handleApprove(row.id)}
                          style={{
                            padding: '4px 10px',
                            background: 'var(--color-success, #10b981)',
                            borderColor: 'var(--color-success, #10b981)',
                          }}
                        >
                          Approve
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={processing !== null}
                          onClick={() => handleReject(row.id)}
                          style={{ padding: '4px 10px' }}
                        >
                          Reject
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
