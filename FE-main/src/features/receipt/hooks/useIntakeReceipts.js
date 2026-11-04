import { useEffect, useState } from 'react';
import useDebounce from '@/hooks/useDebounce';
import { approverApi } from '@/features/approver/api/approverApi';
import { receiptApi } from '@/features/receipt/api/receiptApi';

const RECEIPTS_STORAGE_KEY = 'sagasu_intake_receipts_state';

function readSavedReceiptFilters(initialFilter) {
  try {
    const raw = sessionStorage.getItem(RECEIPTS_STORAGE_KEY);
    if (!raw) return { page: 1, keyword: '', filter: initialFilter, source: 'ALL' };
    const parsed = JSON.parse(raw);
    return {
      page: Number.isInteger(parsed?.page) && parsed.page >= 1 ? parsed.page : 1,
      keyword: typeof parsed?.keyword === 'string' ? parsed.keyword : '',
      filter: typeof parsed?.filter === 'string' ? parsed.filter : initialFilter,
      source: typeof parsed?.source === 'string' ? parsed.source : 'ALL',
    };
  } catch {
    return { page: 1, keyword: '', filter: initialFilter, source: 'ALL' };
  }
}

export function useIntakeReceipts(api = approverApi, initialFilter = 'ALL') {
  const [filters, setFilters] = useState(() => readSavedReceiptFilters(initialFilter));
  const keyword = useDebounce(filters.keyword, 300);
  const [revision, setRevision] = useState(0);
  const { page, filter, source } = filters;

  useEffect(() => {
    try {
      sessionStorage.setItem(RECEIPTS_STORAGE_KEY, JSON.stringify(filters));
    } catch { /* ignore storage errors */ }
  }, [filters]);
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

export function useReceipt(id, withCategories = false, api = approverApi) {
  const [revision, setRevision] = useState(0);
  const key = `${id || 'new'}:${withCategories}:${revision}`;
  const [snapshot, setSnapshot] = useState({ key: null, receipt: null, categories: [], error: '' });
  useEffect(() => {
    let cancelled = false;
    Promise.all([id ? api.detail(id) : null, withCategories ? receiptApi.categories() : [], withCategories ? receiptApi.locations() : []])
      .then(([receipt, categories, locations]) => { if (!cancelled) setSnapshot({ key, receipt, categories, locations, error: '' }); })
      .catch((error) => { if (!cancelled) setSnapshot({ key, receipt: null, categories: [], error: error.message }); });
    return () => { cancelled = true; };
  }, [id, withCategories, key, api]);
  return { ...snapshot, loading: key !== snapshot.key, reload: () => setRevision((current) => current + 1) };
}
