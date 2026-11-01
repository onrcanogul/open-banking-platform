import { formatDate } from '../format.js';

/** The frame shared by the lists: title, refresh, messages, table and paging. */
export default function ListPanel({ path, title, list, actions, emptyText, children }) {
  const { rows, page, setPage, hasNext, loading, error, loadedAt, reload } = list;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="breadcrumb">{path}</p>
          <h1>{title}</h1>
        </div>
        <div className="page-actions">
          {loadedAt && <span className="muted">Son güncelleme: {formatDate(loadedAt)}</span>}
          <button type="button" className="button" onClick={reload} disabled={loading}>Yenile</button>
          {actions}
        </div>
      </div>
      {error && <div className="message message-negative" role="alert">{error}</div>}
      <div className="panel">
        <div className="table-scroll">{children}</div>
        {!loading && !error && rows.length === 0 && <p className="empty">{emptyText}</p>}
        <div className="pager">
          <span className="muted">Sayfa {page + 1}</span>
          <button type="button" className="button" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
            Önceki
          </button>
          <button type="button" className="button" disabled={!hasNext || loading} onClick={() => setPage(page + 1)}>
            Sonraki
          </button>
        </div>
      </div>
    </>
  );
}
