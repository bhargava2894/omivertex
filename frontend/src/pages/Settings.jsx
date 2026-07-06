import { ExportMenu, ImportButton } from '../components/DataTransfer.jsx';

function ThemePreview({ mode }) {
  const dark = mode === 'dark';
  const half = mode === 'system';
  const bg = dark ? '#0b1120' : '#f6f8fb';
  const side = dark ? '#0a0f1d' : '#0f172a';
  const line = dark ? '#25314a' : '#e2e8f0';
  return (
    <div
      className="theme-preview"
      style={{ background: half ? 'linear-gradient(90deg, #f6f8fb 50%, #0b1120 50%)' : bg }}
    >
      <div className="tp-side" style={{ background: side }} />
      <div className="tp-main">
        <div className="tp-line" style={{ background: '#2563eb', width: '55%' }} />
        <div className="tp-line" style={{ background: line, width: '85%' }} />
        <div className="tp-line" style={{ background: line, width: '70%' }} />
      </div>
    </div>
  );
}

const OPTIONS = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
];

export default function Settings({ theme, setTheme, showToast, canEdit }) {
  return (
    <>
      <div className="card settings-section">
        <h2>Appearance</h2>
        <p className="desc">Choose how OmiVertex looks. System follows your OS preference.</p>
        <div className="theme-options" role="radiogroup" aria-label="Theme">
          {OPTIONS.map((o) => (
            <button
              key={o.value}
              className={`theme-option ${theme === o.value ? 'selected' : ''}`}
              role="radio"
              aria-checked={theme === o.value}
              onClick={() => setTheme(o.value)}
            >
              <ThemePreview mode={o.value} />
              {o.label}
            </button>
          ))}
        </div>
      </div>

      <div className="card settings-section">
        <h2>Data</h2>
        <p className="desc">
          Import a staffing roster from Excel or CSV, or export the current roster as Excel, CSV,
          PDF, or Word.
        </p>
        <div className="toolbar-actions">
          {canEdit && <ImportButton onImported={() => {}} showToast={showToast} />}
          <ExportMenu />
        </div>
      </div>

      <div className="card settings-section">
        <h2>About</h2>
        <p className="desc" style={{ marginBottom: 0 }}>
          OmiVertex · Softility internal resource management · Spring Boot + React + PostgreSQL
        </p>
      </div>
    </>
  );
}
