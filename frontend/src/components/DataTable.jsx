import { useState } from 'react';
import Icon from './Icon.jsx';

const PAGE_SIZE = 25;

/**
 * Renders a table. Pagination is client-side by default (slices `rows`). Pass
 * `serverPagination={{ page, size, totalElements, totalPages, onPage }}` when the
 * caller already fetched a single page from the API — then `rows` is that page and
 * the pager drives `onPage`.
 */
export default function DataTable({
  columns,
  rows,
  loading,
  emptyText,
  onEdit,
  onDelete,
  serverPagination,
}) {
  const [clientPage, setClientPage] = useState(0);
  if (loading) {
    return (
      <div>
        {[...Array(5)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  const server = serverPagination || null;
  const isEmpty = server ? server.totalElements === 0 : rows.length === 0;
  if (isEmpty) {
    return (
      <div className="card">
        <div className="empty-state">
          <Icon name="inbox" size={40} />
          <p>{emptyText || 'Nothing here yet.'}</p>
        </div>
      </div>
    );
  }

  // client mode slices locally; server mode shows the page it was given
  const pageCount = server ? server.totalPages : Math.ceil(rows.length / PAGE_SIZE);
  const page = server ? server.page : Math.min(clientPage, pageCount - 1);
  const size = server ? server.size : PAGE_SIZE;
  const total = server ? server.totalElements : rows.length;
  const visible = server ? rows : rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const goTo = server ? server.onPage : setClientPage;
  const rangeStart = page * size + 1;
  const rangeEnd = server
    ? page * size + rows.length
    : Math.min((page + 1) * PAGE_SIZE, rows.length);

  return (
    <div className="card table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col">
                {c.label}
              </th>
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
                    <button
                      className="btn btn-ghost btn-sm"
                      onClick={() => onEdit(row)}
                      aria-label={`Edit ${row.name || row.id}`}
                    >
                      <Icon name="edit" size={14} /> Edit
                    </button>
                  )}{' '}
                  {onDelete && (
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => onDelete(row)}
                      aria-label={`Delete ${row.name || row.id}`}
                    >
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
            {rangeStart}–{rangeEnd} of {total}
          </span>
          <div className="pager-buttons">
            <button
              className="btn btn-ghost btn-sm"
              disabled={page === 0}
              onClick={() => goTo(page - 1)}
            >
              Previous
            </button>
            <button
              className="btn btn-ghost btn-sm"
              disabled={page >= pageCount - 1}
              onClick={() => goTo(page + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
