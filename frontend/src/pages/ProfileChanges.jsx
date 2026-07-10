import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import DataTable from '../components/DataTable.jsx';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';
import Modal from '../components/Modal.jsx';
import Field from '../components/Field.jsx';
import { PROF_LABELS } from '../proficiency.js';

export default function ProfileChanges({ showToast }) {
  const [filter, setFilter] = useState('PENDING'); // PENDING, APPROVED, REJECTED, ALL
  const { data, loading, reload } = useLoad(
    () => api.listProfileChanges(filter === 'ALL' ? {} : { status: filter }),
    [filter]
  );

  const [rejecting, setRejecting] = useState(null); // request object being rejected
  const [rejectNote, setRejectNote] = useState('');
  const [processing, setProcessing] = useState(null); // id of current processing action

  const handleApprove = async (id) => {
    if (processing) return;
    setProcessing(id);
    try {
      await api.approveProfileChange(id);
      showToast('Profile change request approved');
      reload();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setProcessing(null);
    }
  };

  const handleReject = async () => {
    if (!rejecting || processing) return;
    setProcessing(rejecting.id);
    try {
      await api.rejectProfileChange(rejecting.id, rejectNote);
      showToast('Profile change request rejected');
      setRejecting(null);
      setRejectNote('');
      reload();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setProcessing(null);
    }
  };

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

      <DataTable
        loading={loading}
        rows={data || []}
        emptyText="No profile changes found matching the filter."
        columns={[
          {
            key: 'associateName',
            label: 'Associate',
            render: (r) => (
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span
                  className="user-avatar"
                  style={{ width: '28px', height: '28px', fontSize: '12px' }}
                >
                  {r.associateName.charAt(0).toUpperCase()}
                </span>
                <div className="cell-main">{r.associateName}</div>
              </div>
            ),
          },
          {
            key: 'type',
            label: 'Type',
            render: (r) => (
              <Badge
                tone={r.type === 'SKILLS' ? 'blue' : 'amber'}
                label={r.type === 'SKILLS' ? 'Skills' : 'Resume'}
              />
            ),
          },
          {
            key: 'createdAt',
            label: 'Submitted',
            render: (r) => (
              <span className="cell-sub">
                {r.createdAt ? new Date(r.createdAt).toLocaleDateString() : '—'}
              </span>
            ),
          },
          {
            key: 'preview',
            label: 'Proposed Changes',
            render: (r) => {
              if (r.type === 'SKILLS') {
                return (
                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', maxWidth: '400px' }}>
                    {(r.proposedSkills || []).map((s, idx) => (
                      <Badge
                        key={idx}
                        tone={s.primary ? 'blue' : undefined}
                        label={`${s.skillName} · ${PROF_LABELS[s.proficiency] || s.proficiency}`}
                      />
                    ))}
                  </div>
                );
              } else {
                return (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Icon name="file" size={16} />
                    <span className="cell-main">{r.resumeFilename}</span>
                    {r.resumeByteSize && (
                      <span className="cell-sub">({(r.resumeByteSize / 1024).toFixed(1)} KB)</span>
                    )}
                  </div>
                );
              }
            },
          },
          {
            key: 'decision',
            label: 'Status / Details',
            render: (r) => {
              if (r.status === 'PENDING') {
                return <Badge tone="amber" label="Pending" />;
              }
              const dateStr = r.decidedAt ? new Date(r.decidedAt).toLocaleDateString() : '';
              return (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Badge tone={getStatusTone(r.status)} label={r.status.toLowerCase()} />
                    {r.decidedBy && <span className="cell-sub">by {r.decidedBy}</span>}
                  </div>
                  {dateStr && (
                    <div className="cell-sub" style={{ marginTop: '2px' }}>
                      {dateStr}
                    </div>
                  )}
                  {r.note && (
                    <div className="cell-sub" style={{ marginTop: '4px', fontStyle: 'italic' }}>
                      “{r.note}”
                    </div>
                  )}
                </div>
              );
            },
          },
          {
            key: 'actions',
            label: 'Actions',
            render: (r) => {
              if (r.status !== 'PENDING') return null;
              return (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={processing !== null}
                    onClick={() => handleApprove(r.id)}
                    style={{
                      background: 'var(--color-accent)',
                      borderColor: 'var(--color-accent)',
                    }}
                  >
                    Approve
                  </button>
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={processing !== null}
                    onClick={() => setRejecting(r)}
                  >
                    Reject
                  </button>
                </div>
              );
            },
          },
        ]}
      />

      {rejecting && (
        <Modal
          title="Reject Profile Change Request"
          onClose={() => {
            setRejecting(null);
            setRejectNote('');
          }}
          footer={
            <>
              <button
                className="btn btn-ghost"
                onClick={() => {
                  setRejecting(null);
                  setRejectNote('');
                }}
              >
                Cancel
              </button>
              <button
                className="btn btn-danger"
                disabled={processing !== null}
                onClick={handleReject}
              >
                Reject Request
              </button>
            </>
          }
        >
          <Field label="Rejection Note (shown to the associate)">
            <textarea
              className="input"
              rows={4}
              value={rejectNote}
              onChange={(e) => setRejectNote(e.target.value)}
              placeholder="e.g. Please add a certification for this level, or upload a more recent PDF resume"
              style={{ width: '100%', resize: 'vertical' }}
            />
          </Field>
        </Modal>
      )}
    </>
  );
}
