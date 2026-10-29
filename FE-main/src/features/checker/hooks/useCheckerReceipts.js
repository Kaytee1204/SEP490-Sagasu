import { useEffect, useState } from 'react';
import useDebounce from '@/hooks/useDebounce';
import { checkerApi } from '../api/checkerApi';
import { receiptApi } from '@/features/receipt/api/receiptApi';

export function useCheckerReceipts(api = checkerApi, initialFilter = 'ALL') {
  const [filters, setFilters] = useState({ page: 1, keyword: '', filter: initialFilter, source: 'ALL' });
  const keyword = useDebounce(filters.keyword, 300);
  const [revision, setRevision] = useState(0);
  const { page, filter, source } = filters;
  const key = JSON.stringify([page, filter, source, keyword, revision]);
  const [snapshot, setSnapshot] = useState({ key: null, data: { content: [], totalElements: 0, totalPages: 0 }, error: '' });
  useEffect(() => {
    let cancelled = false;
    api.list({ page, filter, source, keyword }).then((data) => {
      if (cancelled) return;
      if (page > Math.max(1, data.totalPages)) setFilters((current) => ({ ...current, page: Math.max(1, data.totalPages) }));
      else setSnapshot({ key, data, error: '' });
    }).catch((error) => { if (!cancelled) setSnapshot((current) => ({ ...current, key, error: error.message })); });
    return () => { cancelled = true; };
  }, [page, filter, source, keyword, key, api]);
  return {
    ...snapshot.data, filters, loading: snapshot.key !== key, error: snapshot.key === key ? snapshot.error : '',
    reload: () => setRevision((current) => current + 1),
    setPage: (page) => setFilters((current) => ({ ...current, page })),
    change: (name, value) => setFilters((current) => ({ ...current, [name]: value, page: 1 })),
    reset: () => setFilters({ page: 1, keyword: '', filter: initialFilter, source: 'ALL' }),
  };
}

export function useReceipt(id, withCategories = false, api = checkerApi) {
  const [revision, setRevision] = useState(0);
  const key = `${id || 'new'}:${withCategories}:${revision}`;
  const [snapshot, setSnapshot] = useState({ key: null, receipt: null, categories: [], error: '' });
  useEffect(() => {
    let cancelled = false;
    Promise.all([id ? api.detail(id) : null, withCategories ? checkerApi.categories() : [], withCategories ? receiptApi.locations() : []])
      .then(([receipt, categories, locations]) => { if (!cancelled) setSnapshot({ key, receipt, categories, locations, error: '' }); })
      .catch((error) => { if (!cancelled) setSnapshot({ key, receipt: null, categories: [], error: error.message }); });
    return () => { cancelled = true; };
  }, [id, withCategories, key, api]);
  return { ...snapshot, loading: key !== snapshot.key, reload: () => setRevision((current) => current + 1) };
}
