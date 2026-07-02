export default function Field({ label, required, error, children, full }) {
  return (
    <div className={`field ${full ? 'full' : ''}`}>
      <label>
        {label} {required && <span className="req" aria-hidden="true">*</span>}
      </label>
      {children}
      {error && <div className="error" role="alert">{error}</div>}
    </div>
  );
}
