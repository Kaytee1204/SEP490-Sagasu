import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowDownWideNarrow, CircleCheck, CircleX, Clock3, Eye, EyeOff, Layers3, ListFilter, PackageOpen, Search, TriangleAlert, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { getMyPosts, deletePost } from '@/features/items/api/postApi';
import Pagination from '@/components/common/Pagination';
import MyPostCard from '../components/MyPostCard';
import { EditPostDialog } from '@/features/items/pages/EditPostPage';
import MyPostsEmptyState from '../components/MyPostsEmptyState';
import MyPostsFilterBar from '../components/MyPostsFilterBar';
import './MyPostsPage.css';

const FILTERS = [
  { key: 'ALL', label: 'Tất cả', icon: ListFilter },
  { key: 'VISIBLE', label: 'Đang hiển thị', icon: Eye },
  { key: 'PENDING', label: 'Chờ duyệt', icon: Clock3 },
  { key: 'REJECTED', label: 'Bị từ chối', icon: CircleX },
  { key: 'HIDDEN', label: 'Bị ẩn', icon: EyeOff },
];
const VALID_FILTERS = new Set(FILTERS.map(option => option.key));
const POST_TYPES = [
  { key: 'ALL', label: 'Tất cả', icon: Layers3 },
  { key: 'LOST', label: 'Mất đồ', icon: Search },
  { key: 'FOUND', label: 'Nhặt được', icon: PackageOpen },
];
const VALID_POST_TYPES = new Set(POST_TYPES.map(option => option.key));
const STORAGE_KEY = 'sagasu_my_posts_state';

function readSavedMyPostsState() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { page: 1, filter: 'ALL', postType: 'ALL' };
    const parsed = JSON.parse(raw);
    const page = Number.isInteger(parsed?.page) && parsed.page >= 1 ? parsed.page : 1;
    const filter = VALID_FILTERS.has(parsed?.filter) ? parsed.filter : 'ALL';
    const postType = VALID_POST_TYPES.has(parsed?.postType) ? parsed.postType : 'ALL';
    return { page, filter, postType };
  } catch {
    return { page: 1, filter: 'ALL', postType: 'ALL' };
  }
}

export default function MyPostsPage({ embedded = false }) {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [saved] = useState(readSavedMyPostsState);
  const [attempt, setAttempt] = useState(0);
  const hasUrlState = ['page', 'filter', 'postType'].some(key => searchParams.has(key));
  const defaults = hasUrlState ? { page: 1, filter: 'ALL', postType: 'ALL' } : saved;

  const rawUrlPage = searchParams.get('page');
  const page = rawUrlPage && /^[1-9]\d*$/.test(rawUrlPage) && Number.isSafeInteger(Number(rawUrlPage))
    ? Number(rawUrlPage) : defaults.page;
  const rawUrlFilter = searchParams.get('filter');
  const filter = VALID_FILTERS.has(rawUrlFilter) ? rawUrlFilter : defaults.filter;
  const rawUrlPostType = searchParams.get('postType');
  const postType = VALID_POST_TYPES.has(rawUrlPostType) ? rawUrlPostType : defaults.postType;

  const syncState = (nextPage, nextFilter, nextPostType) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ page: nextPage, filter: nextFilter, postType: nextPostType }));
    } catch { /* ignore storage quota errors */ }
    const updated = new URLSearchParams(searchParams);
    if (nextPage <= 1) updated.delete('page');
    else updated.set('page', String(nextPage));
    if (nextFilter === 'ALL') updated.delete('filter');
    else updated.set('filter', nextFilter);
    // Keep the type explicit so selecting "Tất cả" overrides a saved filter on reload.
    updated.set('postType', nextPostType);
    setSearchParams(updated, { replace: true });
  };

  const handlePageChange = (nextPage) => syncState(nextPage, filter, postType);
  const handleFilterChange = (nextFilter) => syncState(1, nextFilter, postType);
  const handlePostTypeChange = (nextPostType) => syncState(1, filter, nextPostType);

  const Wrapper = embedded ? 'section' : 'main';
  return <Wrapper className={`my-posts-page my-posts-dashboard ${embedded ? 'my-posts-embedded' : 'container'}`}
    aria-label={embedded ? 'Tin đăng của tôi' : undefined}>

    {!embedded && <header className="my-posts-heading"><h1>Tin đăng của tôi</h1></header>}
    <MyPostsFilterBar filter={filter} filters={FILTERS} onSelectFilter={handleFilterChange}
      postType={postType} postTypes={POST_TYPES} onSelectPostType={handlePostTypeChange} />
    <MyPostList key={`${user?.id}:${page}:${filter}:${postType}:${attempt}`} page={page} filter={filter} postType={postType} onPage={handlePageChange}
      onReload={() => setAttempt(n => n + 1)} onResetFilter={() => syncState(1, 'ALL', 'ALL')} />
  </Wrapper>;
}

function MyPostList({ page, filter, postType, onPage, onReload, onResetFilter }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [postToDelete, setPostToDelete] = useState(null);
  const [postToEdit, setPostToEdit] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getMyPosts(page, filter, postType).then(data => { if (!cancelled) setResult(data); })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [page, filter, postType]);

  async function afterDelete() {
    setPostToDelete(null); setNotice('Đã ẩn bài đăng.');
    try {
      const data = await getMyPosts(page, filter, postType);
      if (!data.content.length && page > 1) onPage(page - 1);
      else setResult(data);
    } catch (err) { setError(err.message); }
  }

  return <>
    {notice && <div className="my-posts-notice" role="status"><CircleCheck size={18} aria-hidden="true" /><span>{notice}</span>
      <button type="button" aria-label="Đóng thông báo" onClick={() => setNotice('')}><X size={17} aria-hidden="true" /></button>
    </div>}
    {error ? <div className="my-posts-error" role="alert"><TriangleAlert size={24} aria-hidden="true" /><div><h2>Chưa tải được bài đăng</h2><p>{error}</p></div>
      <button type="button" className="btn btn-ghost" onClick={onReload}>Thử lại</button></div>
      : !result ? <div className="my-posts-loading" role="status"><span>Đang tải bài đăng…</span>
        {[0, 1, 2].map(index => <div className="my-post-skeleton" key={index} aria-hidden="true"><span /><div><i /><i /><i /></div></div>)}
      </div> : <>
        <div className="my-posts-results-heading"><h2 aria-live="polite">{result.totalElements} {postType === 'LOST' ? 'bài mất đồ' : postType === 'FOUND' ? 'bài nhặt được' : 'bài đăng'}</h2>
          <span><ArrowDownWideNarrow size={15} aria-hidden="true" />Mới nhất trước</span>
        </div>
        {result.content.length ? <div className="my-post-list">{result.content.map(post => <MyPostCard key={`${post.id}:${post.updatedAt}`} post={post}
          onDelete={setPostToDelete} onEdit={setPostToEdit} />)}</div> : <MyPostsEmptyState filter={filter} postType={postType} onReset={onResetFilter} />}
        <Pagination className="my-posts-pagination" currentPage={page} totalPages={result.totalPages} onPageChange={onPage} />
      </>}
    {postToEdit && <EditPostDialog id={postToEdit.id} onClose={() => setPostToEdit(null)} onSaved={() => {
      setPostToEdit(null); setNotice('Đã cập nhật bài đăng.');
      getMyPosts(page, filter, postType).then(data => {if (!data.content.length && page > 1) onPage(page - 1); else setResult(data);}).catch(err => setError(err.message));
    }} />}
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
      <header className="my-post-hide-heading"><span><EyeOff size={21} aria-hidden="true" /></span><h2 id="delete-post-title">Ẩn bài đăng?</h2>
        <button type="button" aria-label="Đóng cửa sổ ẩn bài đăng" disabled={busy} onClick={close}><X size={20} aria-hidden="true" /></button>
      </header>
      <div className="my-post-hide-context"><strong>{post.title}</strong><span>{post.categoryName}</span></div>
      <p>Bài đăng sẽ ngừng hiển thị công khai. Dữ liệu và lịch sử xử lý vẫn được giữ.</p>
      <form className="my-post-hide-form" onSubmit={submit}>
        <label htmlFor="deletion-reason" className="input-label">Lý do ẩn bài *</label>
        <textarea id="deletion-reason" className="input" required maxLength={1000} rows={4}
          placeholder="Nhập lý do bạn muốn ẩn bài đăng…"
          value={reason} disabled={busy} onChange={e => { setReason(e.target.value); setDiscard(false); }} />
        {error && <p className="my-post-hide-error" role="alert">{error}</p>}
        {discard ? <div className="my-post-hide-discard" role="alert"><p>Bỏ lý do đang nhập và giữ bài đăng?</p><footer>
          <button type="button" className="btn btn-ghost" onClick={onCancel}>Bỏ nội dung và đóng</button>
          <button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button></footer></div> :
        <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Giữ bài đăng</button>
          <button className="btn btn-primary" disabled={busy}><EyeOff size={16} aria-hidden="true" />{busy ? 'Đang ẩn…' : 'Ẩn bài đăng'}</button></footer>}
      </form>
  </dialog>;
}
