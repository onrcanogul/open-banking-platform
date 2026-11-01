import { useCallback, useEffect, useState } from 'react';
import { useApi } from './useApi.js';

// Matches the page size of the operations endpoints: a full page means there may be another
const PAGE_SIZE = 50;

export function usePagedList(path) {
  const request = useApi();
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [loadedAt, setLoadedAt] = useState(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    // A slower answer to an older request must not replace a newer one
    let current = true;
    setLoading(true);
    request(`${path}?page=${page}`)
      .then((result) => {
        if (current) {
          setRows(result);
          setError(null);
          setLoadedAt(new Date());
        }
      })
      .catch((failure) => current && setError(failure.message))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
  }, [request, path, page, version]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);

  return { rows, page, setPage, hasNext: rows.length === PAGE_SIZE, loading, error, loadedAt, reload };
}
