import { useEffect, useRef, useState } from 'react';
import Icon from './Icon.jsx';
import Modal from './Modal.jsx';

const EXPORTS = [
  { format: 'xlsx', label: 'Excel (.xlsx)', icon: 'sheet' },
  { format: 'csv', label: 'CSV (.csv)', icon: 'sheet' },
  { format: 'pdf', label: 'PDF (.pdf)', icon: 'file' },
  { format: 'docx', label: 'Word (.docx)', icon: 'file' },
];

export function ExportMenu() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="menu-wrap" ref={ref}>
      <button className="btn btn-ghost" onClick={() => setOpen(!open)} aria-expanded={open} aria-haspopup="menu">
        <Icon name="download" size={16} /> Export
      </button>
      {open && (
        <div className="menu" role="menu">
          {EXPORTS.map((e) => (
            <a key={e.format} className="menu-item" role="menuitem" href={`/api/v1/data/export?format=${e.format}`} download onClick={() => setOpen(false)}>
              <Icon name={e.icon} size={16} /> {e.label}
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

export function ImportButton({ onImported, showToast }) {
  const [open, setOpen] = useState(false);
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [file, setFile] = useState(null);
  const [summary, setSummary] = useState(null); // dry-run preview or final result
  const [error, setError] = useState(null);

  const post = async (theFile, dryRun) => {
    const form = new FormData();
    form.append('file', theFile);
    const res = await fetch(`/api/v1/data/import?dryRun=${dryRun}`, { method: 'POST', body: form });
    const body = await res.json();
    if (!res.ok) throw new Error(body.message || 'Import failed');
    return body;
  };

  // step 1: always preview first — nothing is written
  const preview = async (theFile) => {
    if (!theFile) return;
    setBusy(true);
    setError(null);
    setSummary(null);
    try {
      setSummary(await post(theFile, true));
      setFile(theFile);
    } catch (err) {
      setError(err.message);
      setFile(null);
    } finally {
      setBusy(false);
    }
  };

  // step 2: user confirmed — import for real
  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await post(file, false);
      setSummary(result);
      setFile(null);
      showToast(`Imported ${result.allocationsCreated} allocation${result.allocationsCreated === 1 ? '' : 's'}`);
      onImported();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const close = () => {
    setOpen(false);
    setFile(null);
    setSummary(null);
    setError(null);
    setDrag(false);
  };

  const isPreview = summary?.dryRun;

  return (
    <>
      <button className="btn btn-ghost" onClick={() => setOpen(true)}>
        <Icon name="upload" size={16} /> Import
      </button>
      {open && (
        <Modal
          title="Import Roster"
          onClose={close}
          footer={
            isPreview ? (
              <>
                <button className="btn btn-ghost" onClick={close}>Cancel</button>
                <button className="btn btn-primary" onClick={confirm} disabled={busy}>
                  {busy ? 'Importing…' : 'Looks right — import now'}
                </button>
              </>
            ) : (
              <button className="btn btn-primary" onClick={close}>Done</button>
            )
          }
        >
          {error && <div className="form-alert">{error}</div>}

          {!summary && (
            <label
              className={`dropzone ${drag ? 'drag' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
              onDragLeave={() => setDrag(false)}
              onDrop={(e) => { e.preventDefault(); setDrag(false); preview(e.dataTransfer.files[0]); }}
            >
              <Icon name="upload" size={30} />
              <div>
                <strong>{busy ? 'Analyzing…' : 'Click to choose a file'}</strong> or drag it here
              </div>
              <div className="hint">
                .xlsx or .csv with columns: ASSOCIATE NAME, COMPANY, LOCATION, CUSTOMER, BILLABLE (B/NB), PROJECT, SKILL
                — you'll see a preview before anything is saved
              </div>
              <input type="file" accept=".xlsx,.csv" disabled={busy} onChange={(e) => preview(e.target.files[0])} />
            </label>
          )}

          {summary && (
            <>
              <p className="cell-sub" style={{ marginTop: 0 }}>
                {isPreview
                  ? 'Preview — nothing has been saved yet. This is what importing will do:'
                  : 'Import complete.'}
              </p>
              <div className="import-summary">
                <div className="import-stat"><strong>{summary.rowsProcessed}</strong> rows processed</div>
                <div className="import-stat"><strong>{summary.associatesCreated}</strong> associates {isPreview ? 'to add' : 'added'}</div>
                <div className="import-stat"><strong>{summary.clientsCreated}</strong> clients {isPreview ? 'to add' : 'added'}</div>
                <div className="import-stat"><strong>{summary.projectsCreated}</strong> projects {isPreview ? 'to add' : 'added'}</div>
                <div className="import-stat"><strong>{summary.allocationsCreated}</strong> allocations {isPreview ? 'to create' : 'created'}</div>
                <div className="import-stat"><strong>{summary.skipped}</strong> skipped (already present)</div>
              </div>
              {summary.errors.length > 0 && (
                <ul className="import-errors">
                  {summary.errors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              )}
            </>
          )}
        </Modal>
      )}
    </>
  );
}
