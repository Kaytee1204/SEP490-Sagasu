import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getPostCategories, getVisiblePosts } from '@/features/items/api/postApi';
import Pagination from '@/components/common/Pagination';
import SearchHeaderBar from '../components/SearchHeaderBar';
import SearchFilterSidebar from '../components/SearchFilterSidebar';
import SearchMatchCard from '../components/SearchMatchCard';
import './SearchPage.css';

const FILTER_KEYS = ['keyword', 'categoryId', 'postType', 'timeRange', 'fromDate', 'toDate'];

function readFilters(params) {
  const filters = Object.fromEntries(FILTER_KEYS.map(key => [key,
    params.get(key) || (key === 'keyword' ? params.get('q') : '') || '',
  ]));
  if (!filters.timeRange && (filters.fromDate || filters.toDate)) filters.timeRange = 'CUSTOM';
  return filters;
}

const SEARCH_STORAGE_KEY = 'sagasu_search_params';

function readSavedSearchParams() {
  try {
    return sessionStorage.getItem(SEARCH_STORAGE_KEY) || '';
  } catch {
    return '';
  }
}

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [reload, setReload] = useState(0);
  const [categories, setCategories] = useState([]);
  const [categoryError, setCategoryError] = useState('');
  const restoredRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    getPostCategories().then(data => { if (!cancelled) setCategories(data); })
      .catch(() => { if (!cancelled) setCategoryError('Không tải được danh mục. Hãy tải lại trang để thử lại.'); });
    return () => { cancelled = true; };
  }, []);

  const currentQuery = params.toString();
  const effectiveParams = (!currentQuery && !restoredRef.current && readSavedSearchParams())
    ? new URLSearchParams(readSavedSearchParams())
    : params;

  useEffect(() => {
    if (!currentQuery && !restoredRef.current) {
      const saved = readSavedSearchParams();
      restoredRef.current = true;
      if (saved) {
        setParams(new URLSearchParams(saved), { replace: true });
        return;
      }
    }
    restoredRef.current = true;
    try {
      sessionStorage.setItem(SEARCH_STORAGE_KEY, currentQuery);
      sessionStorage.setItem('sagasu_search_last_url', `/tim-kiem${currentQuery ? `?${currentQuery}` : ''}`);
    } catch { /* ignore */ }
  }, [currentQuery, setParams]);

  const rawPage = effectiveParams.get('page') || '1';
  const page = /^[1-9]\d*$/.test(rawPage) && Number(rawPage) <= 2147483647 ? Number(rawPage) : 1;
  const filters = readFilters(effectiveParams);
  const changePage = next => {
    const updated = new URLSearchParams(effectiveParams);
    if (next === 1) updated.delete('page');
    else updated.set('page', String(next));
    const nextStr = updated.toString();
    try {
      sessionStorage.setItem(SEARCH_STORAGE_KEY, nextStr);
      sessionStorage.setItem('sagasu_search_last_url', `/tim-kiem${nextStr ? `?${nextStr}` : ''}`);
    } catch { /* ignore */ }
    setParams(updated);
  };
  const applyFilters = values => {
    const updated = new URLSearchParams();
    for (const key of FILTER_KEYS) {
      if (values[key].trim()) updated.set(key, values[key].trim());
    }
    const nextStr = updated.toString();
    try {
      sessionStorage.setItem(SEARCH_STORAGE_KEY, nextStr);
      sessionStorage.setItem('sagasu_search_last_url', `/tim-kiem${nextStr ? `?${nextStr}` : ''}`);
    } catch { /* ignore */ }
    setParams(updated);
    setReload(current => current + 1);
  };
  const resetAll = () => {
    try {
      sessionStorage.removeItem(SEARCH_STORAGE_KEY);
      sessionStorage.setItem('sagasu_search_last_url', '/tim-kiem');
    } catch { /* ignore */ }
    setParams({});
  };

  return <main className="post-search-page">
    <SearchWorkspace key={JSON.stringify([filters.keyword, filters.timeRange, filters.fromDate, filters.toDate])} initial={filters} categories={categories}
      categoryError={categoryError} onApply={applyFilters} onReset={resetAll}>
      <PostList key={`${effectiveParams.toString()}:${reload}`} page={page} filters={filters}
        onReload={() => setReload(value => value + 1)} onPageChange={changePage}
        onReset={resetAll} />
    </SearchWorkspace>
  </main>;
}

function SearchWorkspace({ initial, categories, categoryError, onApply, onReset, children }) {
  const [draft, setDraft] = useState({ keyword: initial.keyword, timeRange: initial.timeRange,
    fromDate: initial.fromDate, toDate: initial.toDate });
  const [error, setError] = useState('');
  const workspaceRef = useRef(null);
  const toolbarRef = useRef(null);
  const values = { ...initial, ...draft };

  // The toolbar can wrap on narrow screens; keep the sidebar below its actual height.
  useLayoutEffect(() => {
    const updateToolbarHeight = () => workspaceRef.current.style.setProperty(
      '--post-search-toolbar-height', `${toolbarRef.current.getBoundingClientRect().height}px`);
    updateToolbarHeight();
    const observer = new ResizeObserver(updateToolbarHeight);
    observer.observe(toolbarRef.current);
    return () => observer.disconnect();
  }, []);

  const change = event => {
    const { name, value } = event.target;
    setError('');
    if (name === 'timeRange') {
      setDraft(current => ({ ...current, timeRange: value, fromDate: '', toDate: '' }));
      if (value !== 'CUSTOM') onApply({ ...initial, timeRange: value, fromDate: '', toDate: '' });
    } else if (name === 'categoryId' || name === 'postType') {
      // Apply immediate filters without submitting or discarding unfinished text/date edits.
      onApply({ ...initial, [name]: value });
    } else {
      setDraft(current => ({ ...current, [name]: value }));
    }
  };
  const submit = event => {
    event.preventDefault();
    if (values.fromDate && values.toDate && values.fromDate > values.toDate) {
      setError('Ngày bắt đầu không được sau ngày kết thúc.');
      return;
    }
    setError('');
    onApply(values);
  };
  const reset = () => {
    setDraft({ keyword: '', timeRange: '', fromDate: '', toDate: '' });
    setError('');
    onReset();
  };

  return <div className="post-search-workspace" ref={workspaceRef}>
    <SearchHeaderBar toolbarRef={toolbarRef} values={values} categories={categories} categoryError={categoryError}
      onChange={change} onSearch={submit}
      onClearQuery={() => setDraft(current => ({ ...current, keyword: '' }))} />
    <div className="container post-search-layout">
      <SearchFilterSidebar values={values} onChange={change} onApply={submit} onReset={reset} error={error} />
      <section className="post-search-results" aria-label="Kết quả tìm kiếm bài đăng">{children}</section>
    </div>
  </div>;
}

function PostList({ page, filters, onReload, onPageChange, onReset }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const filterKey = JSON.stringify(filters);
  const hasFilters = Object.values(filters).some(Boolean);
  useEffect(() => {
    let cancelled = false;
    getVisiblePosts(page, 12, JSON.parse(filterKey)).then(data => { if (!cancelled) setResult(data); })
      .catch(err => { if (!cancelled) setError(err.message || 'Không tải được danh sách bài đăng.'); });
    return () => { cancelled = true; };
  }, [page, filterKey]);

  if (error) return <div className="post-list-message" role="alert"><h1>Không tải được bài đăng</h1><p>{error}</p>
    <button className="btn btn-primary" onClick={onReload}>Thử lại</button></div>;
  if (!result) return <p className="post-list-message" role="status">Đang tải danh sách…</p>;
  return <>
    <header className="post-list-results-heading">
      <h1>{hasFilters ? 'Kết quả tìm kiếm' : 'Tất cả kết quả tìm kiếm'}</h1>
      <p role="status">{result.totalElements} kết quả</p>
    </header>
    {result.content.length ? <div className="post-list-grid">
      {result.content.map(post => <SearchMatchCard key={post.id} item={post} />)}
    </div> : <div className="post-list-message">
      <h2>{result.totalElements ? 'Trang này không có bài đăng' : hasFilters ? 'Không tìm thấy bài phù hợp' : 'Chưa có bài đăng hiển thị'}</h2>
      <p>{result.totalElements ? 'Vui lòng trở về trang đầu.' : hasFilters ? 'Hãy thử từ khóa khác hoặc bớt điều kiện lọc.' : 'Bạn có thể đăng tin để cộng đồng hỗ trợ tìm kiếm.'}</p>
      {page > 1 && <button type="button" className="btn btn-primary" onClick={() => onPageChange(1)}>Về trang đầu</button>}
      {hasFilters && <button type="button" className="btn btn-ghost" onClick={onReset}>Xóa bộ lọc</button>}
    </div>}
    {result.content.length > 0 && <Pagination className="post-list-pagination" currentPage={page}
      totalPages={result.totalPages} onPageChange={onPageChange} />}
  </>;
}
