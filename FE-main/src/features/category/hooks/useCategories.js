import { useEffect, useRef, useState } from 'react';
import { categoryApi } from '../api/categoryApi';
import useDebounce from '@/hooks/useDebounce';
import { useToast } from '@/hooks/useToast';

export function useCategories() {
  const [filters, setFilters] = useState({ page: 1, keyword: '', active: '' });
  const keyword = useDebounce(filters.keyword, 300);
  const [snapshot, setSnapshot] = useState({
    key: null, result: { content: [], totalElements: 0, totalPages: 0 }, error: '',
  });
  const [revision, setRevision] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const pending = useRef(false);
  const { toast, showToast } = useToast();
  const { page, active } = filters;
  const requestKey = JSON.stringify([page, active, keyword, revision]);
  const loading = snapshot.key !== requestKey;
  const error = loading ? '' : snapshot.error;

  useEffect(() => {
    let cancelled = false;
    categoryApi.list({ page, active, keyword }).then((data) => {
      if (cancelled) return;
      const lastPage = Math.max(1, data.totalPages);
      if (page > lastPage) {
        setFilters((current) => ({ ...current, page: lastPage }));
      } else {
        setSnapshot({ key: requestKey, result: data, error: '' });
      }
    }).catch((err) => {
      if (!cancelled) setSnapshot((current) => ({ ...current, key: requestKey, error: err.message }));
    });
    return () => { cancelled = true; };
  }, [page, active, keyword, requestKey]);

  const changeFilter = (key, value) => setFilters((current) => ({ ...current, [key]: value, page: 1 }));
  const reload = () => setRevision((current) => current + 1);
  const mutate = async (action, message) => {
    if (pending.current) return false;
    pending.current = true;
    setSubmitting(true);
    try {
      await action();
      showToast(message);
      reload();
      return true;
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  };

  return {
    ...snapshot.result, filters, loading, error, submitting, toast, changeFilter, reload,
    setPage: (page) => setFilters((current) => ({ ...current, page })),
    save: (category, name) => mutate(
      () => category ? categoryApi.rename(category.categoryId, name) : categoryApi.create(name),
      category ? 'Đổi tên danh mục thành công' : 'Tạo danh mục thành công',
    ),
    changeStatus: (category) => mutate(
      () => categoryApi.setActive(category.categoryId, !category.active),
      category.active ? 'Đã ngừng hoạt động danh mục' : 'Đã kích hoạt danh mục',
    ),
  };
}
