import Icon from './Icon.jsx';

export default function DataTable({ columns, rows, loading, emptyText, onEdit, onDelete }) {
  if (loading) {
    return (
      <div>
        {[...Array(5)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  if (!rows.length) {
    return (
      <div className="card">
        <div className="empty-state">
          <Icon name="inbox" size={40} />
          <p>{emptyText || 'Nothing here yet.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="card table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col">{c.label}</th>
            ))}
            {(onEdit || onDelete) && <th scope="col" aria-label="Actions" />}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              {columns.map((c) => (
                <td key={c.key}>{c.render ? c.render(row) : row[c.key]}</td>
              ))}
              {(onEdit || onDelete) && (
                <td className="actions">
                  {onEdit && (
                    <button className="btn btn-ghost btn-sm" onClick={() => onEdit(row)} aria-label={`Edit ${row.name || row.id}`}>
                      <Icon name="edit" size={14} /> Edit
                    </button>
                  )}{' '}
                  {onDelete && (
                    <button className="btn btn-danger btn-sm" onClick={() => onDelete(row)} aria-label={`Delete ${row.name || row.id}`}>
                      <Icon name="trash" size={14} />
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
