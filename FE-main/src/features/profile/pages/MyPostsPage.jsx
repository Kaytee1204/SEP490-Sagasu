import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getMyPosts, deletePost } from '@/features/items/api/postApi';
import Pagination from '@/components/common/Pagination';
import MyPostCard from '../components/MyPostCard';
import MyPostsEmptyState from '../components/MyPostsEmptyState';
import './MyPostsPage.css';

const FILTERS = [['ALL', 'Tất cả'], ['VISIBLE', 'Đang hiển thị'], ['PENDING', 'Chờ duyệt'],
['REJECTED', 'Bị từ chối'], ['HIDDEN', 'Bị ẩn']];
const VALID_FILTERS = new Set(FILTERS.map(([key]) => key));
const STORAGE_KEY = 'sagasu_my_posts_state';

function readSavedMyPostsState() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { page: 1, filter: 'ALL' };
    const parsed = JSON.parse(raw);
    const page = Number.isInteger(parsed?.page) && parsed.page >= 1 ? parsed.page : 1;
    const filter = VALID_FILTERS.has(parsed?.filter) ? parsed.filter : 'ALL';
    return { page, filter };
  } catch {
    return { page: 1, filter: 'ALL' };
  }
}

export default function MyPostsPage({ embedded = false }) {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const saved = readSavedMyPostsState();

  const rawUrlPage = searchParams.get('page');
  const urlPage = rawUrlPage && /^[1-9]\d*$/.test(rawUrlPage) ? Number(rawUrlPage) : null;
  const rawUrlFilter = searchParams.get('filter');
  const urlFilter = rawUrlFilter && VALID_FILTERS.has(rawUrlFilter) ? rawUrlFilter : null;

  const [page, setPageState] = useState(() => urlPage ?? saved.page);
  const [filter, setFilterState] = useState(() => urlFilter ?? saved.filter);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (urlPage !== null) setPageState(urlPage);
    if (urlFilter !== null) setFilterState(urlFilter);
  }, [urlPage, urlFilter]);

  const syncState = (nextPage, nextFilter) => {
    setPageState(nextPage);
    setFilterState(nextFilter);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ page: nextPage, filter: nextFilter }));
    } catch { /* ignore storage quota errors */ }
    const updated = new URLSearchParams(searchParams);
    if (nextPage <= 1) updated.delete('page');
    else updated.set('page', String(nextPage));
    if (nextFilter === 'ALL') updated.delete('filter');
    else updated.set('filter', nextFilter);
    setSearchParams(updated, { replace: true });
  };

  const handlePageChange = (nextPage) => syncState(nextPage, filter);
  const handleFilterChange = (nextFilter) => syncState(1, nextFilter);

  const Wrapper = embedded ? 'section' : 'main';
  return <Wrapper className={`my-posts-page ${embedded ? 'my-posts-embedded' : 'container'}`}
    aria-label={embedded ? 'Tin đăng của tôi' : undefined}>

    {!embedded && <header className="my-posts-heading"><h1>Tin đăng của tôi</h1></header>}
    <div className="my-posts-filters" aria-label="Lọc trạng thái bài đăng">
      {FILTERS.map(([key, label]) => <button key={key} className={`chip ${filter === key ? 'active' : ''}`}
        aria-pressed={filter === key} onClick={() => handleFilterChange(key)}>{label}</button>)}
    </div>
    <MyPostList key={`${user?.id}:${page}:${filter}:${attempt}`} page={page} filter={filter} onPage={handlePageChange}
      onReload={() => setAttempt(n => n + 1)} />
  </Wrapper>;
}

function MyPostList({ page, filter, onPage, onReload }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [postToDelete, setPostToDelete] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getMyPosts(page, filter).then(data => { if (!cancelled) setResult(data); })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [page, filter]);

  async function afterDelete() {
    setPostToDelete(null); setNotice('Đã ẩn bài đăng. Dữ liệu lịch sử vẫn được giữ.');
    try {
      const data = await getMyPosts(page, filter);
      if (!data.content.length && page > 1) onPage(page - 1);
      else setResult(data);
    } catch (err) { setError(err.message); }
  }

  return <>
    {notice && <p role="status">{notice}</p>}
    {error ? <div role="alert"><p>{error}</p><button className="btn btn-ghost" onClick={onReload}>Tải lại</button></div>
      : !result ? <p role="status">Đang tải bài đăng…</p> : <>
        <p>{result.totalElements} bài đăng</p>
        {result.content.length ? result.content.map(post => <MyPostCard key={`${post.id}:${post.updatedAt}`} post={post}
          onDelete={setPostToDelete} />) : <MyPostsEmptyState />}
        <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={onPage} />
      </>}
    {postToDelete && <DeletePostDialog post={postToDelete} onCancel={() => setPostToDelete(null)} onSuccess={afterDelete} />}
  </>;
}

function DeletePostDialog({ post, onCancel, onSuccess }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const dialog = useRef(null);
  const [discard, setDiscard] = useState(false);
  useEffect(() => {
    const element = dialog.current, focus = document.activeElement, overflow = document.body.style.overflow;
    element.showModal(); document.body.style.overflow = 'hidden';
    return () => { element.close(); document.body.style.overflow = overflow; if (focus?.isConnected) focus.focus(); };
  }, []);
  const close = () => { if (inFlight.current) return; if (reason.trim()) setDiscard(true); else onCancel(); };
  async function submit(event) {
    event.preventDefault();
    if (inFlight.current) return;
    if (!reason.trim()) { setError('Vui lòng nhập lý do.'); return; }
    inFlight.current = true; setBusy(true); setError('');
    try { await deletePost(post.id, reason.trim()); await onSuccess(); }
    catch (err) { setError(err.message); setBusy(false); inFlight.current = false; }
  }
  return <dialog ref={dialog} aria-labelledby="delete-post-title" className="card my-post-dialog" onCancel={event => { event.preventDefault(); close(); }}>
      <h2 id="delete-post-title">Ẩn bài đăng: {post.title}</h2>
      <p>Bài sẽ không còn xuất hiện trong danh sách và trang chi tiết. Hệ thống giữ dữ liệu lịch sử.</p>
      <form onSubmit={submit}>
        <label htmlFor="deletion-reason" className="input-label">Lý do ẩn bài *</label>
        <textarea id="deletion-reason" className="input" required maxLength={1000} rows={4}
          value={reason} disabled={busy} onChange={e => { setReason(e.target.value); setDiscard(false); }} />
        {error && <p role="alert">{error}</p>}
        {discard ? <div role="alert"><p>Bỏ lý do đang nhập và giữ bài đăng?</p><div className="my-post-actions">
          <button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onCancel}>Bỏ nội dung và đóng</button></div></div> :
        <div className="my-post-actions"><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Giữ bài</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang xử lý…' : 'Ẩn bài đăng'}</button></div>}
      </form>
  </dialog>;
}
