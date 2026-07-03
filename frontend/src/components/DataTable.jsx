import { useState } from 'react';
import Icon from './Icon.jsx';

const PAGE_SIZE = 25;

export default function DataTable({ columns, rows, loading, emptyText, onEdit, onDelete }) {
  const [page, setPage] = useState(0);
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

  const pageCount = Math.ceil(rows.length / PAGE_SIZE);
  const safePage = Math.min(page, pageCount - 1);
  const visible = rows.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

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
          {visible.map((row) => (
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
      {pageCount > 1 && (
        <div className="table-pager">
          <span className="cell-sub">
            {safePage * PAGE_SIZE + 1}–{Math.min((safePage + 1) * PAGE_SIZE, rows.length)} of {rows.length}
          </span>
          <div className="pager-buttons">
            <button className="btn btn-ghost btn-sm" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
              Previous
            </button>
            <button className="btn btn-ghost btn-sm" disabled={safePage >= pageCount - 1} onClick={() => setPage(safePage + 1)}>
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
