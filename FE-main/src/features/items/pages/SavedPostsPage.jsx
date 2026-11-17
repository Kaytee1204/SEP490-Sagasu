import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Bookmark, Search, ArrowDownWideNarrow } from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import SearchMatchCard from '@/features/search/components/SearchMatchCard';
import '@/features/search/pages/SearchPage.css';
import { getSavedPosts } from '../api/savedPostApi';
import { useSavedPosts } from '../hooks/useSavedPosts';
import '../styles/SavedPosts.css';

const FILTERS = [['', 'Tất cả'], ['LOST', 'Cần tìm'], ['FOUND', 'Nhặt được']];

export default function SavedPostsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number.parseInt(params.get('page'), 10) || 1);
  const postType = ['LOST', 'FOUND'].includes(params.get('postType')) ? params.get('postType') : '';
  const { version } = useSavedPosts();
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const refresh = () => setAttempt(current => current + 1);
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, []);
  const change = useCallback((nextPage, nextType = postType) => {
    const query = new URLSearchParams();
    if (nextPage > 1) query.set('page', String(nextPage));
    if (nextType) query.set('postType', nextType);
    setParams(query);
  }, [postType, setParams]);
  return <main className="container saved-posts-page">
    <header className="saved-posts-heading">
      <div><span className="saved-posts-eyebrow"><Bookmark size={16} aria-hidden="true" />Danh sách cá nhân</span>
        <h1>Bài đăng đã lưu</h1><p>Giữ lại những món đồ bạn muốn xem lại.</p></div>
      <Link to="/tim-kiem" className="btn btn-ghost"><Search size={17} aria-hidden="true" />Tìm thêm đồ</Link>
    </header>
    <div className="saved-posts-toolbar">
      <div className="saved-posts-filters" role="group" aria-label="Lọc loại bài đăng">
        {FILTERS.map(([value, label]) => <button key={value} type="button" aria-pressed={postType === value}
          className={postType === value ? 'is-active' : ''} onClick={() => change(1, value)}>{label}</button>)}
      </div>
      <span><ArrowDownWideNarrow size={16} aria-hidden="true" />Mới lưu trước</span>
    </div>
    <SavedList key={`${page}:${postType}:${version}:${attempt}`} page={page} postType={postType}
      onPageChange={change} onRetry={() => setAttempt(current => current + 1)} />
  </main>;
}

function SavedList({ page, postType, onPageChange, onRetry }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getSavedPosts(page, postType).then(data => {
      if (!active) return;
      if (page > 1 && !data.content.length) onPageChange(Math.max(1, data.totalPages));
      else setResult(data);
    }).catch(err => { if (active) setError(err.message || 'Không tải được bài đăng đã lưu.'); });
    return () => { active = false; };
  }, [page, postType, onPageChange]);
  if (error) return <section className="saved-posts-empty" role="alert"><h2>Chưa tải được danh sách</h2><p>{error}</p>
    <button type="button" className="btn btn-accent" onClick={onRetry}>Thử lại</button></section>;
  if (!result) return <p className="saved-posts-loading" role="status">Đang tải bài đăng đã lưu…</p>;
  if (!result.content.length) return <section className="saved-posts-empty">
    <span className="saved-posts-empty-icon"><Bookmark size={30} aria-hidden="true" /></span>
    <h2>{postType ? 'Chưa có bài đã lưu thuộc loại này' : 'Bạn chưa lưu bài đăng nào'}</h2>
    <p>Bấm biểu tượng lưu trên bài đăng để dễ dàng xem lại tại đây.</p>
    <Link to="/tim-kiem" className="btn btn-accent"><Search size={17} aria-hidden="true" />Tìm đồ thất lạc</Link>
  </section>;
  return <><p className="saved-posts-count" role="status">{result.totalElements} bài đăng đã lưu</p>
    <div className="post-list-grid">{result.content.map(item => <SearchMatchCard key={item.id} item={item} />)}</div>
    {result.totalPages > 1 && <Pagination className="post-list-pagination" currentPage={page} totalPages={result.totalPages} onPageChange={onPageChange} />}
  </>;
}
